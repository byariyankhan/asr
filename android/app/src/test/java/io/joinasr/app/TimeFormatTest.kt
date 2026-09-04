package io.joinasr.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `under an hour is minutes only`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("45m", formatMinutes(45))
        assertEquals("59m", formatMinutes(59))
    }

    @Test
    fun `a whole hour drops the minutes`() {
        assertEquals("1h", formatMinutes(60))
        assertEquals("3h", formatMinutes(180))
    }

    @Test
    fun `an hour and change shows both`() {
        assertEquals("1h 20m", formatMinutes(80))
        assertEquals("2h 5m", formatMinutes(125))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minutes are a bug, not a display case`() {
        formatMinutes(-1)
    }
}

/**
 * Six screens counted days and every one wrote `"$n days"`, so the first day
 * of every challenge read "1 days" — including in thirty-point type on the
 * card a witness sees.
 */
class DaysLabelTest {

    @Test
    fun `one day is singular`() {
        assertEquals("1 day", daysLabel(1))
        assertEquals("1 DAY", daysLabelUpper(1))
    }

    @Test
    fun `everything else is plural, including none`() {
        assertEquals("0 days", daysLabel(0))
        assertEquals("2 days", daysLabel(2))
        assertEquals("49 days", daysLabel(49))
        assertEquals("49 DAYS", daysLabelUpper(49))
    }
}
