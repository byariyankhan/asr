package com.joinasr.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop's answer to a pass that threw.
 *
 * Written because the screen-off path had no answer at all: it caught the
 * failure, reported it and went straight round again. One pass that keeps
 * failing was therefore a bare `while (true)` with a crash report inside
 * it -- a pinned core and thousands of identical reports an hour, on a
 * loop that is meant to run for days in somebody's pocket.
 */
class LoopRecoveryTest {

    @Test
    fun `one unlucky pass costs what a quiet pass costs`() {
        // Not a new penalty for a one-off: the old code waited IDLE_MILLIS
        // after a failed tick, and a single failure still does.
        assertEquals(Enforcement.IDLE_MILLIS, LoopRecovery.backoffMillis(1))
        // Nothing below one is meaningful; it must not compute a negative
        // shift or a zero wait, which would be the spin all over again.
        assertEquals(Enforcement.IDLE_MILLIS, LoopRecovery.backoffMillis(0))
        assertEquals(Enforcement.IDLE_MILLIS, LoopRecovery.backoffMillis(-3))
    }

    @Test
    fun `waiting doubles while it keeps failing`() {
        assertEquals(Enforcement.IDLE_MILLIS * 2, LoopRecovery.backoffMillis(2))
        assertEquals(Enforcement.IDLE_MILLIS * 4, LoopRecovery.backoffMillis(3))
        assertEquals(Enforcement.IDLE_MILLIS * 8, LoopRecovery.backoffMillis(4))
    }

    @Test
    fun `and stops doubling, so the loop still comes back on its own`() {
        assertEquals(LoopRecovery.MAX_BACKOFF_MILLIS, LoopRecovery.backoffMillis(20))
        assertEquals(LoopRecovery.MAX_BACKOFF_MILLIS, LoopRecovery.backoffMillis(5_000))
        // The shift is what a huge count could break; nothing may overflow
        // into a negative wait, which delay() would take as no wait at all.
        assertEquals(LoopRecovery.MAX_BACKOFF_MILLIS, LoopRecovery.backoffMillis(Int.MAX_VALUE))
    }

    @Test
    fun `every wait is long enough to be a wait`() {
        for (failures in 1..1_000) {
            val wait = LoopRecovery.backoffMillis(failures)
            assertTrue("failure $failures waited $wait", wait >= Enforcement.IDLE_MILLIS)
            assertTrue("failure $failures waited $wait", wait <= LoopRecovery.MAX_BACKOFF_MILLIS)
        }
    }

    @Test
    fun `a failure is reported, then the same failure ever more rarely`() {
        val reported = (1..64).filter { LoopRecovery.shouldReport(it) }
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64), reported)
    }

    @Test
    fun `an hour of failing leaves a handful of reports, not thousands`() {
        // At the capped wait, an hour is 12 passes. Even at the shortest,
        // what reaches Crashlytics is single figures -- which is the point:
        // the report must not become the load it is reporting.
        val passesInAnHour = 3_600_000 / Enforcement.IDLE_MILLIS.toInt()
        val reports = (1..passesInAnHour).count { LoopRecovery.shouldReport(it) }
        assertTrue("$reports reports from $passesInAnHour passes", reports <= 10)
        assertFalse(LoopRecovery.shouldReport(0))
    }
}
