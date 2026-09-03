package io.joinasr.app.enforcement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.apps.AppEntry
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

    val state: StateFlow<PactState> = store.pact
        .map { if (it == null) PactState.None else PactState.Active(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PactState.Loading)

    fun commit(apps: List<AppEntry>, limits: Map<String, Int>, durationDays: Int) {
        viewModelScope.launch {
            store.save(Pact.from(apps, limits, durationDays, System.currentTimeMillis()))
        }
    }

    /**
     * Ends the challenge. Not reachable from any screen yet: the designs put
     * it behind the review and dashboard screens, and an app that can drop a
     * commitment from an unlabelled code path is not one.
     */
    fun abandon() {
        viewModelScope.launch { store.clear() }
    }
}
