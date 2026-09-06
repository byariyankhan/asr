package io.joinasr.app.challenge

import io.joinasr.app.usage.DayUsage

/** One day of the week, judged against the pact. */
data class DayOutcome(
    val dayStartMillis: Long,
    /** Foreground minutes across every app under a limit. */
    val totalMinutes: Int,
    /** True when no single app went over its own limit that day. */
    val withinLimits: Boolean,
)

/**
 * What the progress screen says about the last seven days.
 *
 * The judgement worth being careful about is [DayOutcome.withinLimits]: a
 * day is within limits when *every* app stayed under *its own* limit, not
 * when the total stayed under the total. Somebody who spends their whole
 * allowance in one app has broken that app's limit, and a rule that added
 * the day up would tell them they were fine.
 */
object WeeklyProgress {

    /**
     * @param judgedFrom for an app added to the challenge after it started,
     *   the start of the local day it came in on. On the days before that
     *   the app was not under a limit, so it is neither judged nor counted
     *   in the total; the day it was added counts whole, because the limit
     *   applies to the whole of that day from the moment it is set. An app
     *   with no entry was there from the start.
     */
    fun outcomes(
        days: List<DayUsage>,
        limits: Map<String, Int>,
        judgedFrom: Map<String, Long> = emptyMap(),
    ): List<DayOutcome> =
        days.map { day ->
            val underLimit = limits.filterKeys { (judgedFrom[it] ?: Long.MIN_VALUE) <= day.dayStartMillis }
            DayOutcome(
                dayStartMillis = day.dayStartMillis,
                totalMinutes = day.totalMinutes(underLimit.keys),
                withinLimits = underLimit.all { (packageName, limit) ->
                    (day.minutesByPackage[packageName] ?: 0) <= limit
                },
            )
        }

    fun daysWithinLimits(outcomes: List<DayOutcome>): Int = outcomes.count { it.withinLimits }

    fun breaches(outcomes: List<DayOutcome>): Int = outcomes.count { !it.withinLimits }

    /** The sum of every limit: what a day is allowed in total. */
    fun dailyAllowance(limits: Map<String, Int>): Int = limits.values.sum()

    /**
     * The height of the tallest bar the chart has to draw.
     *
     * The allowance, unless a day went past it — a chart scaled to the
     * allowance alone would clip exactly the days worth looking at, which
     * are the ones somebody went over.
     */
    fun chartCeiling(outcomes: List<DayOutcome>, allowance: Int): Int =
        maxOf(allowance, outcomes.maxOfOrNull { it.totalMinutes } ?: 0).coerceAtLeast(1)
}
