package io.joinasr.app.challenge

import io.joinasr.app.usage.Day
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Where somebody is in their challenge, as the dashboard says it.
 *
 * Counted in whole local days, not in elapsed milliseconds. Somebody who
 * starts at eleven at night is on day two the next morning, the same as
 * somebody who started that morning — because "day" here means the thing a
 * person crosses off, and a challenge that advanced at 11pm the following
 * night would feel broken to both of them.
 */
data class ChallengeProgress(
    /** 1 on the day it started. Never below 1, never above [totalDays]. */
    val dayNumber: Int,
    val totalDays: Int,
    val daysLeft: Int,
    /** 0 to 100, rounded to the nearest whole. */
    val percent: Int,
    val isComplete: Boolean,
) {
    companion object {

        fun of(
            startedAtMillis: Long,
            durationDays: Int,
            nowMillis: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): ChallengeProgress {
            val total = durationDays.coerceAtLeast(1)
            val started = localDate(startedAtMillis, zone)
            val today = localDate(nowMillis, zone)

            // A clock set backwards, or a pact restored from a phone in a
            // later timezone, can put "today" before the start. Day one is
            // the floor: there is no day zero to show somebody.
            val elapsed = ChronoUnit.DAYS.between(started, today).coerceAtLeast(0)
            val rawDay = elapsed + 1
            val complete = rawDay > total
            val day = rawDay.coerceAtMost(total.toLong()).toInt()

            return ChallengeProgress(
                dayNumber = day,
                totalDays = total,
                daysLeft = (total - day).coerceAtLeast(0),
                percent = Math.round(day * 100f / total).coerceIn(0, 100),
                isComplete = complete,
            )
        }

        /**
         * Uses the same midnight the usage day does, so "day 4" on the
         * challenge card and "today" on the limit rows can never disagree.
         */
        private fun localDate(millis: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(Day.startOfDay(millis, zone)).atZone(zone).toLocalDate()
    }
}
