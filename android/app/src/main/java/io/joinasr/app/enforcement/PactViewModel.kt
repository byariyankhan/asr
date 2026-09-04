package io.joinasr.app.enforcement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.sync.Sync
import io.joinasr.app.sync.Uuid7
import io.joinasr.app.witness.WitnessStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether a challenge is running.
 *
 * Loading is a state of its own rather than a null pact, because the two
 * mean opposite things to the screen above: "no challenge yet, show setup"
 * and "we have not looked yet". Collapsing them shows the first setup screen
 * for a frame to everybody who already has a challenge, every launch.
 */
sealed interface PactState {
    data object Loading : PactState
    data object None : PactState
    data class Active(val pact: Pact) : PactState
}

/**
 * The stored pact, and the one place it is committed.
 *
 * Committing is a single call on purpose. It is the moment the app stops
 * being a set of forms and becomes something that will get in the person's
 * way, and there should be exactly one line in the codebase where that
 * happens.
 */
class PactViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PactStore(application)
    private val outcomes = OutcomeStore(application)
    private val sync = Sync(application)
    private val witnesses = WitnessStore(application)

    val state: StateFlow<PactState> = store.pact
        .map { if (it == null) PactState.None else PactState.Active(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PactState.Loading)

    /**
     * The last challenge to end, if the person has not been shown how it
     * ended yet. Figma 26 reads this; acknowledging it is what stops the
     * screen reappearing on every launch.
     */
    val endedUnseen: StateFlow<PactOutcome?> =
        outcomes.unseen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A challenge that ended with no signal left an event in the outbox.
        // This is where it gets another go: on app start, which is the one
        // moment there is definitely a foreground process and usually a
        // network.
        viewModelScope.launch {
            val ended = outcomes.current() ?: return@launch
            if (ended.reported) return@launch
            runCatching {
                sync.drain(ended.asPact())
                if (sync.isDrained()) outcomes.markReported()
            }
        }
    }

    fun commit(apps: List<AppEntry>, limits: Map<String, Int>, durationDays: Int) {
        viewModelScope.launch {
            val pact = Pact.from(apps, limits, durationDays, System.currentTimeMillis())
            // Stored first. The challenge has started whether or not the
            // server ever hears about it, and a person on a train pressing
            // Start must end up with a running challenge, not an error.
            store.save(pact)
            // The server's copy is best-effort and keyed by the start
            // time, so a stale id from the last challenge can never be
            // mistaken for this one's.
            runCatching { sync.remotePactId(pact) }
        }
    }

    fun acknowledgeEnded() {
        viewModelScope.launch { outcomes.markSeen() }
    }

    /**
     * Ends the challenge because the person said so.
     *
     * There has to be a way out. Without one the only way out is to
     * uninstall, and that is the worst ending available to everybody in it:
     * the person loses their history and does not come back, and their
     * witnesses are told the harshest thing there is to be told -- that the
     * app was removed -- about somebody who was merely tired. Walking out
     * the front door costs less than going through the wall.
     *
     * It is not free, though, and nothing here pretends it is. This is a
     * failed challenge, it is reported as one, and the witnesses hear about
     * it in the same breath they would have heard about a broken limit. A
     * quiet exit would leave the word "witness" meaning nothing.
     *
     * The order is [EnforcementService]'s order, for [EnforcementService]'s
     * reason: outcome first, event queued second, pact cleared last. A phone
     * that dies in the middle comes back with a finished challenge and
     * something still to send, rather than with a challenge that stopped
     * being enforced and was never recorded.
     */
    fun giveUp() {
        viewModelScope.launch {
            val pact = store.current() ?: return@launch
            val now = System.currentTimeMillis()
            val ending = Endings.gaveUp(
                pact = pact,
                witnesses = runCatching { witnesses.current().count { it.accepted } }
                    .getOrDefault(0),
                eventId = Uuid7.next(now),
                nowMillis = now,
            )
            outcomes.save(ending.outcome)
            runCatching {
                sync.report(pact, ending.event)
                if (sync.isDrained()) outcomes.markReported()
            }
            store.clear()
        }
    }
}

/**
 * A finished challenge, back in the shape the sync layer needs to address
 * it. Only ever used to find or create the server's copy of something that
 * has already ended, which is why nothing enforces it.
 */
private fun PactOutcome.asPact() = Pact(
    apps = apps,
    startedAtMillis = startedAtMillis,
    durationDays = durationDays,
)
