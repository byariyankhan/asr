package com.joinasr.app.earn

import org.junit.Assert.*
import org.junit.Test

class PhoneFreeSessionTest {
    private val duration = 20 * 60_000L

    @Test fun `using any app while unlocked earns nothing`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, false)
        timer.observe(duration * 5, false)
        assertNull(timer.lockedSinceElapsed)
        assertFalse(timer.complete)
    }

    @Test fun `twenty minutes starts at lock not activity creation`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, false)
        timer.observe(60_000, true)
        timer.observe(duration, true)
        assertFalse(timer.complete)
        timer.observe(duration + 60_000, true)
        assertTrue(timer.complete)
    }

    @Test fun `unlock one millisecond early resets and waits for a new lock`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(duration - 1, false, userPresent = true)
        assertNull(timer.lockedSinceElapsed)
        timer.observe(duration * 2, false)
        assertFalse(timer.complete)
        timer.observe(duration * 3, true)
        assertEquals(duration * 4, timer.deadlineElapsed)
        timer.observe(duration * 4 - 1, true)
        assertFalse(timer.complete)
        timer.observe(duration * 4, true)
        assertTrue(timer.complete)
    }

    @Test fun `native unlock overrides a stale locked keyguard query`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(5_000, true, userPresent = true)
        assertNull(timer.lockedSinceElapsed)
        assertFalse(timer.complete)
    }

    @Test fun `keyguard fallback also resets without a broadcast`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(5_000, false)
        assertNull(timer.lockedSinceElapsed)
        timer.observe(6_000, true)
        assertEquals(6_000L, timer.lockedSinceElapsed)
    }

    @Test fun `notifications AOD alarms and calls over keyguard preserve the interval`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        // Display changes and call UI occluding keyguard still report locked.
        for (now in listOf(10_000L, 20_000L, 30_000L, duration - 1)) {
            timer.observe(now, true)
            assertEquals(0L, timer.lockedSinceElapsed)
            assertFalse(timer.complete)
        }
        timer.observe(duration, true)
        assertTrue(timer.complete)
    }

    @Test fun `unlocking to use apps during a call still resets`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(10_000, true) // call answered over keyguard
        timer.observe(20_000, false, userPresent = true)
        assertNull(timer.lockedSinceElapsed)
        assertFalse(timer.complete)
    }

    @Test fun `unlock at the deadline retains the earned reward`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(duration, false, userPresent = true)
        assertTrue(timer.complete)
    }

    @Test fun `sleep and a delayed alarm never require reopening the UI`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(duration + 60_000, true)
        assertTrue(timer.complete)
        timer.observe(duration + 61_000, false, userPresent = true)
        assertTrue(timer.complete)
    }

    @Test fun `a delayed completion alert cannot punish a late unlock`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(0, true)
        timer.observe(duration + 60_000, false, userPresent = true)
        assertTrue(timer.complete)
    }

    @Test fun `new monitor does not inherit an unverified interval`() {
        val previous = PhoneFreeSession(duration)
        previous.observe(0, true)
        val restarted = PhoneFreeSession(duration)
        restarted.observe(duration, true)
        assertFalse(restarted.complete)
        assertEquals(duration * 2, restarted.deadlineElapsed)
    }

    @Test fun `backwards elapsed clock cannot complete or retain a previous interval`() {
        val timer = PhoneFreeSession(duration)
        timer.observe(60_000, true)
        timer.observe(0, true)
        assertEquals(0L, timer.lockedSinceElapsed)
        assertFalse(timer.complete)
    }
}
