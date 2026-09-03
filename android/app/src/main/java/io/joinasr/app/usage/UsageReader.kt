package io.joinasr.app.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import java.time.ZoneId

/** What one poll of the system found. */
data class UsageSnapshot(
    /** Whole minutes in the foreground today, per package. Absent means none. */
    val minutesByPackage: Map<String, Int>,
    /** What is in front of the person right now, if anything. */
    val foregroundPackage: String?,
    /** The midnight these figures are counted from. */
    val dayStartMillis: Long,
)

/**
 * Reads foreground time out of Android and keeps a running total for today.
 *
 * Stateful on purpose. Asking the system for a whole day of events several
 * times a minute is work the phone pays for in battery, so each poll asks
 * only for what has happened since the last one and hands it to the
 * [ForegroundAccumulator], which carries the total forward. The arithmetic
 * lives there and is tested there; this class is the translation layer and
 * nothing else.
 *
 * Nothing here checks whether usage access was granted. Without it the
 * system returns an empty event stream rather than an error, so a caller
 * that has not checked would quietly measure zero forever — see
 * `Permissions.hasUsageAccess`, and check it before trusting a snapshot.
 */
class UsageReader(private val manager: UsageStatsManager?) {

    private var accumulator: ForegroundAccumulator? = null
    private var dayStartMillis: Long = 0

    @Synchronized
    fun poll(nowMillis: Long = System.currentTimeMillis()): UsageSnapshot {
        val today = Day.startOfDay(nowMillis, ZoneId.systemDefault())
        val carried = accumulator

        val current: ForegroundAccumulator
        if (carried == null || today != dayStartMillis) {
            // A new day, or the first poll since the process started. Read
            // from before midnight so an app that was already open when the
            // day turned over is counted from midnight rather than from
            // whenever it is next touched.
            current = ForegroundAccumulator(today)
            accumulator = current
            dayStartMillis = today
            feed(current, from = today - INITIAL_LOOKBACK_MILLIS, to = nowMillis)
        } else {
            current = carried
            feed(current, from = current.cursorMillis, to = nowMillis)
        }

        return UsageSnapshot(
            minutesByPackage = current.minutesByPackage(nowMillis),
            foregroundPackage = current.foregroundPackage(),
            dayStartMillis = today,
        )
    }

    /**
     * Throws away today's total and starts again.
     *
     * For the case where usage access has just been granted: everything
     * before that point reads as zero, and carrying it forward would
     * under-report the day for as long as the day lasts.
     */
    @Synchronized
    fun reset() {
        accumulator = null
        dayStartMillis = 0
    }

    private fun feed(accumulator: ForegroundAccumulator, from: Long, to: Long) {
        val events = manager?.queryEvents(from, to) ?: return
        val translated = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            val packageName = event.packageName ?: continue
            val kind = kindOf(event.eventType) ?: continue
            translated += UsageEvent(packageName, kind, event.timeStamp)
        }
        accumulator.add(translated, to)
    }

    /**
     * ACTIVITY_RESUMED and ACTIVITY_PAUSED are the same two values under
     * names introduced in API 29; the older ones are used because this app
     * runs from 26 and they are compile-time constants, so the deprecation
     * costs nothing at runtime.
     *
     * The screen going dark or the phone shutting down ends foreground time
     * whether or not a pause arrives with it. Most devices send the pause as
     * well and a second close is a no-op; the ones that do not would
     * otherwise credit somebody with a whole night inside whatever app they
     * fell asleep holding.
     */
    @Suppress("DEPRECATION")
    private fun kindOf(eventType: Int): UsageEvent.Kind? = when (eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEvent.Kind.Resumed
        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEvent.Kind.Paused
        SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> UsageEvent.Kind.Interrupted
        else -> null
    }

    private companion object {
        /**
         * How far before midnight the first read of a day looks. Long enough
         * to find the resume of an app somebody fell asleep holding, short
         * enough that the query stays cheap.
         */
        const val INITIAL_LOOKBACK_MILLIS = 8L * 60 * 60 * 1000

        // Written as literals rather than referenced from UsageEvents.Event
        // because those fields arrived in API 28 and this app runs from 26.
        // They are frozen platform constants; older versions simply never
        // emit them, which is the correct behaviour here anyway.
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_SHOWN = 17
        const val DEVICE_SHUTDOWN = 26
    }
}

/** The reader for this process, over the system's own service. */
fun usageReader(context: Context): UsageReader =
    UsageReader(context.getSystemService<UsageStatsManager>())
