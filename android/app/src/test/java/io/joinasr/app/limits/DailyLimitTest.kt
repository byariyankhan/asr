package io.joinasr.app.limits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLimitTest {

    @Test
    fun `the ladder is fives to an hour, then fifteens to four`() {
        assertEquals(5, DailyLimit.MINIMUM_MINUTES)
        assertEquals(240, DailyLimit.MAXIMUM_MINUTES)
        assertTrue(DailyLimit.Ladder.containsAll(listOf(5, 15, 20, 30, 60, 75, 120, 240)))
        // Nothing between the rungs.
        assertFalse(DailyLimit.Ladder.contains(65))
        assertFalse(DailyLimit.Ladder.contains(7))
        // Strictly increasing, with no repeats where the two ranges meet.
        assertEquals(DailyLimit.Ladder.sorted().distinct(), DailyLimit.Ladder)
    }

    @Test
    fun `plus then minus comes back to where it started`() {
        // The whole reason for a ladder rather than a step size. This fails
        // for any scheme where the step is chosen from the current value.
        for (start in DailyLimit.Ladder.dropLast(1)) {
            assertEquals(start, DailyLimit.decreased(DailyLimit.increased(start)))
        }
        for (start in DailyLimit.Ladder.drop(1)) {
            assertEquals(start, DailyLimit.increased(DailyLimit.decreased(start)))
        }
    }

    @Test
    fun `it stops at both ends rather than running past them`() {
        assertEquals(240, DailyLimit.increased(240))
        assertEquals(5, DailyLimit.decreased(5))
        assertFalse(DailyLimit.canIncrease(240))
        assertFalse(DailyLimit.canDecrease(5))
        assertTrue(DailyLimit.canIncrease(5))
        assertTrue(DailyLimit.canDecrease(240))
    }

    @Test
    fun `a value from nowhere is pulled onto the ladder before it moves`() {
        // A limit saved by an older build, or one this ladder no longer has.
        // Above 60 the rungs are 15 apart, so the halfway point is 67.5.
        assertEquals(60, DailyLimit.snapped(63))
        assertEquals(60, DailyLimit.snapped(67))
        assertEquals(75, DailyLimit.snapped(68))
        assertEquals(5, DailyLimit.snapped(0))
        assertEquals(240, DailyLimit.snapped(9999))
        // And moving from it lands on a real rung, never back on 63.
        assertEquals(75, DailyLimit.increased(63))
        assertEquals(55, DailyLimit.decreased(63))
    }

    @Test
    fun `every chosen app gets a starting limit`() {
        val limits = DailyLimit.defaultsFor(listOf("com.a", "com.b"))
        assertEquals(setOf("com.a", "com.b"), limits.keys)
        assertTrue(limits.values.all { it == DailyLimit.DEFAULT_MINUTES })
        assertTrue(DailyLimit.DEFAULT_MINUTES in DailyLimit.Ladder)
    }
}
