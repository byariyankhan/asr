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
