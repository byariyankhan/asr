package io.joinasr.app.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** One finished (or running) day, and what each app got out of it. */
data class DayUsage(
    val dayStartMillis: Long,
    val minutesByPackage: Map<String, Int>,
) {
    fun totalMinutes(packages: Collection<String>): Int =
        packages.sumOf { minutesByPackage[it] ?: 0 }

    /** This day with minutes spent elsewhere -- on the phone before this one -- added in. */
    fun plus(elsewhere: Map<String, Int>): DayUsage {
        if (elsewhere.isEmpty()) return this
        val merged = minutesByPackage.toMutableMap()
        for ((packageName, minutes) in elsewhere) {
            merged[packageName] = (merged[packageName] ?: 0) + minutes
        }
        return copy(minutesByPackage = merged)
    }

    /**
     * This day, never below what was written down while it was happening.
     * Android forgets an app's events when it is uninstalled, and a week
     * that quietly loses a day is a week nobody can be judged on. See
     * [io.joinasr.app.enforcement.UsageFloor].
     */
    fun atLeast(kept: Map<String, Int>): DayUsage {
        if (kept.isEmpty()) return this
        val merged = minutesByPackage.toMutableMap()
        for ((packageName, minutes) in kept) {
            merged[packageName] = maxOf(merged[packageName] ?: 0, minutes)
        }
        return copy(minutesByPackage = merged)
    }
}

/**
 * The last few days of foreground time, a day at a time.
 *
 * No database. Android keeps its usage events for weeks and will answer for
 * any window, so a week of history is seven queries rather than a table this
 * app has to write to, migrate and keep correct across reinstalls. It is
 * also the same measurement the enforcement loop uses, through the same
 * [ForegroundAccumulator], so the progress screen and the block screen can
 * never disagree about a number.
 *
 * Read once when a screen opens rather than polled: yesterday does not
 * change.
 */
object UsageHistory {

    /**
     * Days ending with today, oldest first.
     *
     * Each day is measured on its own accumulator seeded at that day's
     * midnight, with the query reaching back before it — an app left open
     * across midnight belongs partly to each side, and reading from midnight
     * alone would lose the first stretch of the morning.
     */
    suspend fun lastDays(
        context: Context,
        days: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayUsage> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService<UsageStatsManager>()
            ?: return@withContext emptyList()

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(nextMidnight, nowMillis)

            val accumulator = ForegroundAccumulator(start)
            val events = runCatching {
                manager.queryEvents(start - LOOKBACK_MILLIS, end)
            }.getOrNull()

            if (events != null) {
                val translated = mutableListOf<UsageEvent>()
                val event = android.app.usage.UsageEvents.Event()
                while (events.getNextEvent(event)) {
                    val packageName = event.packageName ?: continue
                    val kind = kindOf(event.eventType) ?: continue
                    translated += UsageEvent(packageName, kind, event.timeStamp)
                }
                accumulator.add(translated, end)
            }

            DayUsage(start, accumulator.minutesByPackage(end))
        }
    }

    @Suppress("DEPRECATION")
    private fun kindOf(eventType: Int): UsageEvent.Kind? = when (eventType) {
        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEvent.Kind.Resumed
        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEvent.Kind.Paused
        SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> UsageEvent.Kind.Interrupted
        else -> null
    }

    private const val LOOKBACK_MILLIS = 8L * 60 * 60 * 1000

    // Same frozen platform constants UsageReader explains: these fields
    // arrived in API 28 and this app runs from 26.
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17
    private const val DEVICE_SHUTDOWN = 26
}
