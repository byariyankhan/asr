package io.joinasr.app.usage

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val london = ZoneId.of("Europe/London")

    private fun millis(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `the day starts at the local midnight, not at UTC`() {
        val evening = millis(dhaka, 2026, 9, 3, 23, 30)
        val expected = millis(dhaka, 2026, 9, 3, 0, 0)
        assertEquals(expected, Day.startOfDay(evening, dhaka))
    }

    @Test
    fun `the same instant belongs to different days in different places`() {
        // 20:30 in Dhaka is 15:30 in London on the same date, but half an
        // hour past midnight in Dhaka is still the previous evening in
        // London -- which is the whole reason the zone is a parameter.
        val instant = millis(dhaka, 2026, 9, 4, 0, 30)
        assertEquals(millis(dhaka, 2026, 9, 4, 0, 0), Day.startOfDay(instant, dhaka))
        assertEquals(millis(london, 2026, 9, 3, 0, 0), Day.startOfDay(instant, london))
    }

    @Test
    fun `midnight itself belongs to the day it begins`() {
        val midnight = millis(dhaka, 2026, 9, 3, 0, 0)
        assertEquals(midnight, Day.startOfDay(midnight, dhaka))
    }

    @Test
    fun `two times on the same date are the same day, one second apart is not`() {
        val late = millis(dhaka, 2026, 9, 3, 23, 59)
        val early = millis(dhaka, 2026, 9, 3, 0, 1)
        val next = millis(dhaka, 2026, 9, 4, 0, 0)
        assertTrue(Day.isSameDay(late, early, dhaka))
        assertFalse(Day.isSameDay(late, next, dhaka))
    }

    @Test
    fun `the next midnight is the calendar's, not today's plus twenty-four hours`() {
        // London springs forward on 29 March 2026: the day is 23 hours
        // long, so midnight plus a day lands at 01:00 on the 30th.
        val noon = millis(london, 2026, 3, 29, 12, 0)
        assertEquals(millis(london, 2026, 3, 30, 0, 0), Day.nextMidnight(noon, london))
        assertEquals(23L * 60 * 60 * 1000, Day.nextMidnight(noon, london) - Day.startOfDay(noon, london))
        // An ordinary day, for comparison.
        val evening = millis(dhaka, 2026, 9, 3, 23, 30)
        assertEquals(millis(dhaka, 2026, 9, 4, 0, 0), Day.nextMidnight(evening, dhaka))
    }

    @Test
    fun `a day that does not start at midnight still starts where the clock says`() {
        // Lord Howe Island shifts by thirty minutes; several zones have had
        // a DST change land exactly on midnight, so "midnight" is whatever
        // the first instant of the date is, not 00:00 by construction.
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        val noon = millis(lordHowe, 2026, 10, 4, 12, 0)
        val start = Day.startOfDay(noon, lordHowe)
        assertEquals(
            java.time.LocalDate.of(2026, 10, 4),
            java.time.Instant.ofEpochMilli(start).atZone(lordHowe).toLocalDate(),
        )
        assertTrue(start <= noon)
    }
}
