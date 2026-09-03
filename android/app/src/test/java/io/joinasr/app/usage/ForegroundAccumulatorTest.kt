package io.joinasr.app.usage

import io.joinasr.app.usage.UsageEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAccumulatorTest {

    private val midnight = 1_772_150_400_000L // an arbitrary but fixed local midnight
    private val oneMinute = 60_000L
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"

    private fun resumed(pkg: String, atMinute: Long) =
        UsageEvent(pkg, Kind.Resumed, midnight + atMinute * oneMinute)

    private fun paused(pkg: String, atMinute: Long) =
        UsageEvent(pkg, Kind.Paused, midnight + atMinute * oneMinute)

    private fun interrupted(atMinute: Long) =
        UsageEvent("android", Kind.Interrupted, midnight + atMinute * oneMinute)

    private fun at(minutes: Long) = midnight + minutes * oneMinute

    @Test
    fun `a session is the time between resuming and pausing`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 10), paused(instagram, 25)), at(30))
        assertEquals(mapOf(instagram to 15), accumulator.minutesByPackage(at(30)))
        assertNull(accumulator.foregroundPackage())
    }

    @Test
    fun `an app still open counts up to now`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 10)), at(17))
        assertEquals(mapOf(instagram to 7), accumulator.minutesByPackage(at(17)))
        assertEquals(instagram, accumulator.foregroundPackage())
        // And keeps counting without any new event arriving, which is what
        // makes a limit fire while somebody is sitting still in one app.
        assertEquals(mapOf(instagram to 20), accumulator.minutesByPackage(at(30)))
    }

    @Test
    fun `one app resuming ends the one before it, pause or no pause`() {
        // Android usually sends the pause, but not always: a process dying
        // takes its pause with it. Only one app is in front at a time, so a
        // resume is enough to know the last one is done.
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0), resumed(youtube, 12)), at(20))
        assertEquals(mapOf(instagram to 12, youtube to 8), accumulator.minutesByPackage(at(20)))
        assertEquals(youtube, accumulator.foregroundPackage())
    }

    @Test
    fun `the screen going off stops the clock`() {
        // Otherwise somebody who falls asleep holding their phone wakes up
        // eight hours over their limit.
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0), interrupted(5)), at(400))
        assertEquals(mapOf(instagram to 5), accumulator.minutesByPackage(at(400)))
        assertNull(accumulator.foregroundPackage())
    }

    @Test
    fun `feeding it a little at a time gives the same answer as all at once`() {
        // The whole reason the accumulator is stateful: the service polls,
        // and a poll must not be able to change the total.
        val all = listOf(
            resumed(instagram, 0),
            paused(instagram, 10),
            resumed(youtube, 10),
            paused(youtube, 40),
        )

        val once = ForegroundAccumulator(midnight)
        once.add(all, at(60))

        val piecemeal = ForegroundAccumulator(midnight)
        piecemeal.add(all.filter { it.timestampMillis <= at(15) }, at(15))
        piecemeal.add(all.filter { it.timestampMillis in (at(15) + 1)..at(60) }, at(60))

        assertEquals(once.minutesByPackage(at(60)), piecemeal.minutesByPackage(at(60)))
        assertEquals(mapOf(instagram to 10, youtube to 30), piecemeal.minutesByPackage(at(60)))
    }

    @Test
    fun `an app left open across midnight is counted from midnight`() {
        // The resume happened yesterday, which is why the reader looks back
        // past midnight. Only the part on this side of it belongs to today.
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(
            listOf(
                UsageEvent(instagram, Kind.Resumed, midnight - 30 * oneMinute),
                paused(instagram, 5),
            ),
            at(60),
        )
        assertEquals(mapOf(instagram to 5), accumulator.minutesByPackage(at(60)))
    }

    @Test
    fun `yesterday's finished sessions contribute nothing`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(
            listOf(
                UsageEvent(youtube, Kind.Resumed, midnight - 120 * oneMinute),
                UsageEvent(youtube, Kind.Paused, midnight - 60 * oneMinute),
            ),
            at(60),
        )
        assertEquals(emptyMap<String, Int>(), accumulator.minutesByPackage(at(60)))
    }

    @Test
    fun `minutes round down, so a limit is reached and not anticipated`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0)), midnight + 14 * oneMinute + 59_000)
        assertEquals(14, accumulator.minutesByPackage(midnight + 14 * oneMinute + 59_000)[instagram])
        assertEquals(15, accumulator.minutesByPackage(midnight + 15 * oneMinute)[instagram])
    }

    @Test
    fun `a pause for something that is not open changes nothing`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0), paused(youtube, 5)), at(10))
        assertEquals(instagram, accumulator.foregroundPackage())
        assertEquals(mapOf(instagram to 10), accumulator.minutesByPackage(at(10)))
    }

    @Test
    fun `events out of order are still timed correctly`() {
        // Two queries merged by a caller need not arrive sorted, and one
        // event in the wrong place would mis-time a whole session.
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(paused(instagram, 20), resumed(instagram, 5)), at(30))
        assertEquals(mapOf(instagram to 15), accumulator.minutesByPackage(at(30)))
    }

    @Test
    fun `anything past the end of the window waits for the next poll`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0), paused(instagram, 50)), at(10))
        assertEquals(mapOf(instagram to 10), accumulator.minutesByPackage(at(10)))
        assertEquals(instagram, accumulator.foregroundPackage())
        assertEquals(at(10), accumulator.cursorMillis)
    }

    @Test
    fun `an app that was never in front is absent, not zero`() {
        val accumulator = ForegroundAccumulator(midnight)
        accumulator.add(listOf(resumed(instagram, 0), paused(instagram, 3)), at(10))
        assertEquals(setOf(instagram), accumulator.minutesByPackage(at(10)).keys)
    }
}
