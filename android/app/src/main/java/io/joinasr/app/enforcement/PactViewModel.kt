package io.joinasr.app.enforcement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.apps.AppEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /**
     * Null until the first read comes back, and null again if nothing is
     * stored. Callers must not treat "not loaded yet" as "no pact" and start
     * a second one; the setup flow only reaches the commit after somebody
     * has walked through it, so there is no path where that matters, and it
     * is written down here so it stays that way.
     */
    val pact: StateFlow<Pact?> =
        store.pact.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun commit(apps: List<AppEntry>, limits: Map<String, Int>) {
        viewModelScope.launch {
            store.save(Pact.from(apps, limits, System.currentTimeMillis()))
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
