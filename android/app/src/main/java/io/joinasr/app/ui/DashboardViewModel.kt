package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.enforcement.CarriedUsage
import io.joinasr.app.enforcement.ProtectionStatusStore
import io.joinasr.app.usage.usageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What the dashboard draws, refreshed while somebody is looking at it. */
data class DashboardState(
    val minutesByPackage: Map<String, Int> = emptyMap(),
    /**
     * Whether the enforcement loop has run recently. False means nothing is
     * being enforced right now, whatever the permissions say.
     */
    val loopLive: Boolean = true,
    /**
     * Whether the system recently refused to show a block screen. It does
     * that silently when the overlay permission is missing, and this is the
     * only trace.
     */
    val blockDropped: Boolean = false,
)

/**
 * Today's minutes and whether protection is actually working.
 *
 * Its own usage reader rather than a channel to the enforcement service.
 * Reading usage is a read-only query against the system, so two readers cost
 * nothing but a little work and save binding a service, keeping a connection
 * alive and inventing a protocol — for a number the screen can simply go and
 * ask for.
 *
 * The liveness half is not decoration. Twice now this app has shown a
 * confident dashboard while enforcing nothing, and a person only finds that
 * out by scrolling past their limit undisturbed. The screen should be the
 * first to say it, not the last.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = usageReader(application)
    private val protection = ProtectionStatusStore(application)
    private val carried = CarriedUsage(application)
    private val openedAt = System.currentTimeMillis()

    // The same whole-day figure the loop decides on: what this phone can
    // see, plus what the day already held when the challenge arrived here
    // from another handset. Without the second part the row said "5 of 30
    // min" while the block screen, counting the twenty-five minutes spent
    // on the old phone, said thirty.
    private val readings = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val elsewhere = carried.forDay(CarriedUsage.today(now))
            emit(reader.poll().plus(elsewhere).minutesByPackage to now)
            delay(POLL_MILLIS)
        }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<DashboardState> =
        combine(readings, protection.status) { (minutes, now), status ->
            DashboardState(
                minutesByPackage = minutes,
                // Given the benefit of the doubt for a few seconds after the
                // screen opens: this is where the service is started from,
                // and it has not had a chance to leave its first mark yet.
                loopLive = status.isLive(now) || now - openedAt < GRACE_MILLIS,
                blockDropped = status.lastBlockFailedMillis > 0 &&
                    now - status.lastBlockFailedMillis < BLOCK_FAILURE_WINDOW_MILLIS,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), DashboardState())

    private companion object {
        /**
         * Slower than the enforcement loop on purpose. This only keeps a
         * number on a screen honest while somebody looks at it; being a few
         * seconds behind is invisible, and the service is what has to be
         * exact.
         */
        const val POLL_MILLIS = 5_000L

        const val GRACE_MILLIS = 10_000L

        /** How long a dropped block screen stays worth mentioning. */
        const val BLOCK_FAILURE_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
