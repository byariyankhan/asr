package io.joinasr.app

import io.joinasr.app.ui.screens.ago
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

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

/**
 * How long ago something happened, from a clock passed in.
 *
 * The clock is a parameter because the screen ticks its own: "9 hr ago" was
 * worked out once during composition, so an hour into looking at it, it
 * still said nine.
 */
class AgoTest {

    private val at = Instant.parse("2026-09-04T09:00:00Z")

    private fun agoAfter(minutes: Long) = ago(at.toString(), at.plus(Duration.ofMinutes(minutes)))

    @Test
    fun `the first minute is just now`() {
        assertEquals("just now", agoAfter(0))
        assertEquals("just now", agoAfter(1))
        assertEquals("2 min ago", agoAfter(2))
    }

    @Test
    fun `it climbs through minutes, hours and days`() {
        assertEquals("59 min ago", agoAfter(59))
        assertEquals("1 hr ago", agoAfter(60))
        assertEquals("23 hr ago", agoAfter(60 * 23))
        assertEquals("yesterday", agoAfter(60 * 24))
        assertEquals("2 days ago", agoAfter(60 * 48))
    }

    /** A clock that has gone backwards is not a thing to show a negative for. */
    @Test
    fun `the future is just now`() {
        assertEquals("just now", agoAfter(-500))
    }

    @Test
    fun `nothing at all still reads as something`() {
        assertEquals("recently", ago(null, at))
        assertEquals("recently", ago("not a timestamp", at))
    }
}
