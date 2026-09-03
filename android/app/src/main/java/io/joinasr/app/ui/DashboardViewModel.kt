package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.usage.usageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Today's minutes, for the dashboard's live rows.
 *
 * Its own reader rather than a channel to the enforcement service. Reading
 * usage is a read-only query against the system, so two readers cost nothing
 * but a little work and save binding a service, keeping a connection alive
 * and inventing a protocol -- for a number the screen can simply go and ask
 * for.
 *
 * Polling stops when nothing is collecting, which is what WhileSubscribed is
 * doing here: the dashboard in the background must not keep querying, that
 * is the enforcement service's job and it does it more cleverly.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = usageReader(application)

    val minutesByPackage: StateFlow<Map<String, Int>> = flow {
        while (true) {
            emit(reader.poll().minutesByPackage)
            delay(POLL_MILLIS)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyMap())

    private companion object {
        /**
         * Slower than the enforcement loop on purpose. This only keeps a
         * number on a screen honest while somebody looks at it; being a few
         * seconds behind is invisible, and the service is what has to be
         * exact.
         */
        const val POLL_MILLIS = 5_000L
    }
}
