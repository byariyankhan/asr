package io.joinasr.app.challenge

import io.joinasr.app.ui.greetingFor
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, dhaka).toInstant().toEpochMilli()

    private fun progress(startedAt: Long, days: Int, now: Long) =
        ChallengeProgress.of(startedAt, days, now, dhaka)

    @Test
    fun `the day it starts is day one, not day zero`() {
        val started = at(2026, 9, 3, hour = 9)
        val same = progress(started, 14, at(2026, 9, 3, hour = 23))
        assertEquals(1, same.dayNumber)
        assertEquals(13, same.daysLeft)
        assertFalse(same.isComplete)
    }

    @Test
    fun `it advances at midnight, not twenty-four hours after starting`() {
        // Somebody who starts at eleven at night is on day two the next
        // morning, the same as somebody who started that morning. A
        // challenge that ticked over at 11pm the following night would feel
        // broken to both of them.
        val lateStart = at(2026, 9, 3, hour = 23)
        val nextMorning = progress(lateStart, 14, at(2026, 9, 4, hour = 7))
        assertEquals(2, nextMorning.dayNumber)
    }

    @Test
    fun `the design's own numbers come out`() {
        // Figma 13 shows Day 4, 10 days left, 29% for a 14-day challenge.
        val started = at(2026, 9, 1)
        val today = progress(started, 14, at(2026, 9, 4))
        assertEquals(4, today.dayNumber)
        assertEquals(10, today.daysLeft)
        assertEquals(29, today.percent)
    }

    @Test
    fun `the last day is a hundred per cent and not yet complete`() {
        val started = at(2026, 9, 1)
        val last = progress(started, 14, at(2026, 9, 14))
        assertEquals(14, last.dayNumber)
        assertEquals(0, last.daysLeft)
        assertEquals(100, last.percent)
        assertFalse(last.isComplete)
    }

    @Test
    fun `the day after the last day is complete, and stops counting`() {
        val started = at(2026, 9, 1)
        val after = progress(started, 14, at(2026, 9, 15))
        assertTrue(after.isComplete)
        assertEquals(14, after.dayNumber)
        assertEquals(0, after.daysLeft)
        // A month later it still says fourteen rather than forty-five.
        val muchLater = progress(started, 14, at(2026, 10, 15))
        assertEquals(14, muchLater.dayNumber)
        assertTrue(muchLater.isComplete)
    }

    @Test
    fun `a clock set backwards cannot produce day zero`() {
        // Somebody changing their phone's date, or a pact carried to a
        // phone in an earlier timezone. Day one is the floor: there is no
        // day zero to show anybody.
        val started = at(2026, 9, 10)
        val before = progress(started, 14, at(2026, 9, 1))
        assertEquals(1, before.dayNumber)
        assertEquals(13, before.daysLeft)
        assertFalse(before.isComplete)
    }

    @Test
    fun `a one-day challenge is complete the day after it starts`() {
        val started = at(2026, 9, 3)
        assertEquals(100, progress(started, 1, at(2026, 9, 3)).percent)
        assertFalse(progress(started, 1, at(2026, 9, 3)).isComplete)
        assertTrue(progress(started, 1, at(2026, 9, 4)).isComplete)
    }

    @Test
    fun `the offered lengths are the ones the design draws`() {
        assertEquals(listOf(7, 14, 21, 30), ChallengeDuration.Presets.map { it.days })
        assertEquals("Recommended", ChallengeDuration.Presets.first { it.days == 14 }.caption)
        assertEquals(14, ChallengeDuration.DEFAULT_DAYS)
        assertTrue(ChallengeDuration.isPreset(30))
        assertFalse(ChallengeDuration.isPreset(45))
    }

    @Test
    fun `a custom length is held inside the bounds`() {
        assertEquals(ChallengeDuration.MINIMUM_DAYS, ChallengeDuration.clamp(0))
        assertEquals(ChallengeDuration.MINIMUM_DAYS, ChallengeDuration.clamp(-40))
        assertEquals(ChallengeDuration.MAXIMUM_DAYS, ChallengeDuration.clamp(9999))
        assertEquals(45, ChallengeDuration.clamp(45))
        assertTrue(ChallengeDuration.MINIMUM_DAYS < ChallengeDuration.DEFAULT_DAYS)
        assertTrue(ChallengeDuration.DEFAULT_DAYS < ChallengeDuration.MAXIMUM_DAYS)
    }

    @Test
    fun `every offered length has something true to say about it`() {
        for (days in ChallengeDuration.MINIMUM_DAYS..ChallengeDuration.MAXIMUM_DAYS) {
            assertTrue("no note for $days days", ChallengeDuration.note(days).isNotBlank())
        }
        assertTrue(ChallengeDuration.note(14).contains("14"))
    }

    @Test
    fun `the greeting matches the hour, and two in the morning is not morning`() {
        assertEquals("GOOD MORNING", greetingFor(5))
        assertEquals("GOOD MORNING", greetingFor(11))
        assertEquals("GOOD AFTERNOON", greetingFor(12))
        assertEquals("GOOD AFTERNOON", greetingFor(17))
        assertEquals("GOOD EVENING", greetingFor(18))
        assertEquals("GOOD EVENING", greetingFor(23))
        // The hour somebody most needs a screen-time app.
        assertEquals("GOOD EVENING", greetingFor(2))
        assertEquals("GOOD EVENING", greetingFor(4))
    }
}
