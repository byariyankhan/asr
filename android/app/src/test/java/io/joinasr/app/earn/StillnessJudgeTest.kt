package io.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessJudgeTest {

    /** Samples every 100 ms from [from] for [millis]: a phone on a table, with a hair of sensor noise. */
    private fun StillnessJudge.rest(from: Long, millis: Long): Int =
        (0..millis step 100).sumOf { observe(0.02f * (it % 3), 0.01f, 9.81f, from + it) }

    /** Samples every 100 ms: a phone in a hand, moving a little each time. */
    private fun StillnessJudge.fidget(from: Long, millis: Long): Int =
        (0..millis step 100).sumOf { observe(if ((it / 100) % 2 == 0L) 1.2f else 0.2f, 0.3f, 9.6f, from + it) }

    @Test fun `a phone left on a table is still, after a second of it`() {
        val judge = StillnessJudge()
        assertEquals(0, judge.rest(0, 900))
        assertFalse(judge.still)
        // Believed at one second; the first second of stillness is complete at two.
        judge.rest(1_000, 900)
        assertTrue(judge.still)
        assertEquals(1, judge.observe(0f, 0f, 9.81f, 2_000))
        assertEquals(8, judge.rest(2_100, 8_000))
    }

    @Test fun `a phone picked up stops the clock and a phone put down starts it again`() {
        val judge = StillnessJudge()
        judge.rest(0, 5_000)
        assertEquals(4_000L, judge.heldMillis)
        judge.fidget(5_100, 3_000)
        assertFalse(judge.still)
        // Only the second spent deciding it had moved was counted.
        assertEquals(5_000L, judge.heldMillis)
        // Settling into stillness again is not counted; what follows is.
        judge.rest(8_200, 3_000)
        assertTrue(judge.still)
        assertEquals(6_900L, judge.heldMillis)
    }

    @Test fun `a single bump is not a pick-up`() {
        val judge = StillnessJudge()
        judge.rest(0, 3_000)
        judge.observe(2.5f, 0f, 9.81f, 3_100)
        judge.rest(3_200, 1_000)
        assertTrue(judge.still)
        assertEquals(3_200L, judge.heldMillis)
    }

    @Test fun `a gap in the samples is the screen gone, and is worth nothing`() {
        val judge = StillnessJudge()
        judge.rest(0, 3_000)
        assertEquals(2_000L, judge.heldMillis)
        assertEquals(0, judge.observe(0f, 0f, 9.81f, 63_000))
        assertEquals(2_000L, judge.heldMillis)
    }
}
