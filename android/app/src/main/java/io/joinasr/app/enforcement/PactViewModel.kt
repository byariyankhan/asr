package io.joinasr.app.enforcement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.analytics.Analytics
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.sync.Sync
import io.joinasr.app.sync.Uuid7
import io.joinasr.app.witness.WitnessStore
import io.joinasr.app.sync.Sync.AddAppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    private val carried = CarriedUsage(application)
    private val outcomes = OutcomeStore(application)
    private val sync = Sync(application)
    private val witnesses = WitnessStore(application)

    /**
     * True while the phone is asking the server whether this account has a
     * challenge it does not know about.
     *
     * Read together with [state]: "no pact on this phone" and "no pact" are
     * different answers, and showing somebody the start-a-challenge screen
     * for the second it takes to find out would be showing them the wrong
     * one.
     */
    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

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

    /**
     * Brings back the challenge this account is running.
     *
     * Called when somebody signs in. It does nothing at all in the ordinary
     * case -- there is a pact on the phone and the first line returns -- and
     * everything in the cases that used to lose one: a reinstall, a new
     * handset, a phone replaced after a theft. Those all looked identical to
     * "no challenge" and were treated as such, which is how deleting the app
     * became a way out of a pact nobody was told about.
     *
     * Nothing is asked and nothing is claimed. One account runs on one
     * phone, so signing in here has already moved the challenge here -- the
     * server did it while registering this install, signed the last phone
     * out and told the witnesses. This only picks up what is now this
     * phone's, and writes down the id to report against.
     *
     * The permissions are a separate matter and a real one: they are granted
     * per install, so a challenge restored onto a fresh install is a
     * challenge that blocks nothing until somebody grants them. That is what
     * the gate on the way to the dashboard is for.
     *
     * Never overwrites. A challenge on this phone is the one being enforced
     * here, and the server's copy is bookkeeping about it.
     */
    fun restoreFromServer() {
        viewModelScope.launch {
            if (store.current() != null) return@launch
            _restoring.value = true
            val remote = runCatching { sync.remoteChallenge() }.getOrNull()
            // Checked again: committing a challenge while this was in flight
            // is rare and the local one wins.
            if (remote != null && store.current() == null) {
                sync.adopt(remote)
                // The day arrives with the challenge. Whatever these apps
                // have already had today was spent on a phone this one
                // cannot see, and until that is known this phone must not
                // hand out a second allowance for it.
                carried.expect(CarriedUsage.today())
                store.save(remote.pact)
            }
            _restoring.value = false
        }
    }

    init {
        // A challenge that ended with no signal left an event in the outbox.
        // This is where it gets another go: on app start, which is the one
        // moment there is definitely a foreground process and usually a
        // network.
        viewModelScope.launch {
            val ended = outcomes.current() ?: return@launch
            if (ended.reported) return@launch
            runCatching {
                val pact = ended.asPact()
                sync.drain(pact)
                if (sync.isDrained(pact)) outcomes.markReported()
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
            Analytics.log(Analytics.pactCreated(durationDays))
            // The server's copy is best-effort and keyed by the start
            // time, so a stale id from the last challenge can never be
            // mistaken for this one's.
            runCatching { sync.remotePactId(pact) }
        }
    }

    fun acknowledgeEnded() {
        viewModelScope.launch { outcomes.markSeen() }
    }

    /** True from the tap on "Add to challenge" until the server has answered. */
    private val _addingApp = MutableStateFlow(false)
    val addingApp: StateFlow<Boolean> = _addingApp.asStateFlow()

    /** Why the last add did not happen, until the screen clears it. */
    private val _addAppError = MutableStateFlow<String?>(null)
    val addAppError: StateFlow<String?> = _addAppError.asStateFlow()

    /** The label of the app just added, until the screen acknowledges it. */
    private val _appAdded = MutableStateFlow<String?>(null)
    val appAdded: StateFlow<String?> = _appAdded.asStateFlow()

    /**
     * Brings one more app under a limit on the running challenge.
     *
     * The one change a running challenge takes, and only in this direction:
     * an app joins, no app leaves, no limit moves. It counts from now
     * against the whole of today -- an app already past the limit it was
     * just given is blocked within the second, which is the day's usage and
     * not a breach, the same as starting a challenge in the afternoon. The
     * witnesses are not told; their summary shows one more app from today.
     *
     * Online only, unlike everything else about a challenge: the server's
     * copy is what the witnesses read, and the new limit should exist for
     * them the moment it exists for the person. The pact that comes back
     * replaces the stored one whole, which is what the enforcement loop is
     * already watching.
     */
    fun addApp(entry: AppEntry, limitMinutes: Int) {
        if (_addingApp.value) return
        viewModelScope.launch {
            val pact = store.current() ?: return@launch
            if (pact.appFor(entry.packageName) != null) {
                _appAdded.value = entry.label
                return@launch
            }
            _addingApp.value = true
            _addAppError.value = null
            val result = runCatching {
                sync.addApp(pact, entry.packageName, entry.label, limitMinutes)
            }.getOrElse { AddAppResult.Refused("Something went wrong. Try again in a moment.") }
            when (result) {
                is AddAppResult.Added -> {
                    // Only if this is still the same challenge: a give-up
                    // that raced this request must not be undone by it.
                    if (store.current()?.startedAtMillis == pact.startedAtMillis) {
                        store.save(result.pact)
                        _appAdded.value = entry.label
                    }
                }
                is AddAppResult.Refused -> _addAppError.value = result.message
            }
            _addingApp.value = false
        }
    }

    fun clearAddAppError() {
        _addAppError.value = null
    }

    fun acknowledgeAppAdded() {
        _appAdded.value = null
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
                witnesses = runCatching { witnesses.current().filter { it.accepted } }
                    .getOrDefault(emptyList()),
                eventId = Uuid7.next(now),
                nowMillis = now,
            )
            outcomes.save(ending.outcome)
            Analytics.log(Analytics.challengeBroken("user_gave_up", pact.durationDays))
            runCatching {
                sync.report(pact, ending.event)
                if (sync.isDrained(pact)) outcomes.markReported()
            }
            store.clear()
        }
    }
}
