package com.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class MotionCountersTest {

    /** Feeds [steps] steps evenly over [millis] from [from], returning what was credited. */
    private fun RunCounter.stride(from: Long, steps: Int, millis: Long, startTotal: Int): Int {
        var credited = 0
        for (i in 1..steps) credited += observe(startTotal + i, from + millis * i / steps)
        return credited
    }

    @Test fun `steps at a running cadence count, walking steps do not`() {
        val counter = RunCounter()
        counter.observe(1_000, 0)
        // 60 steps in 20 s is 180 a minute: a run.
        assertEquals(60, counter.stride(0, 60, 20_000, 1_000))
        // 30 steps in 20 s is 90 a minute: a walk.
        assertEquals(0, counter.stride(20_000, 30, 20_000, 1_060))
        assertEquals(60, counter.credited)
    }

    @Test fun `a window is judged whole, when it closes`() {
        val counter = RunCounter()
        counter.observe(0, 0)
        assertEquals(0, counter.stride(0, 49, 15_000, 0))
        // The window closes on the first reading at or past 20 s, and
        // everything in it is credited then: fifty in twenty seconds.
        assertEquals(50, counter.observe(50, 20_000))
    }

    @Test fun `a reboot's reset starts the window over without counting backwards`() {
        val counter = RunCounter()
        counter.observe(5_000, 0)
        counter.stride(0, 30, 10_000, 5_000)
        assertEquals(0, counter.observe(3, 12_000))
        assertEquals(0, counter.credited)
        assertEquals(60, counter.stride(12_000, 60, 20_000, 3))
    }

    @Test fun `a thousand running steps come in twenty windows`() {
        val counter = RunCounter()
        var total = 100
        var at = 0L
        counter.observe(total, at)
        var credited = 0
        repeat(20) {
            credited += counter.stride(at, 50, 20_000, total)
            total += 50
            at += 20_000
        }
        // Each window ends where the next begins, so its last reading opens the next.
        assertEquals(1_000, counter.credited)
        assertEquals(1_000, credited)
    }

    private fun hpaAt(metres: Float): Float =
        1013.25f * (1f - metres / 44_330f).toDouble().pow(5.255).toFloat()

    /** Feeds pressure for a climb from [fromMetres] to [toMetres] over [millis], stepping the while if [onFoot]. */
    private fun StairsCounter.climb(from: Long, fromMetres: Float, toMetres: Float, millis: Long, onFoot: Boolean, startSteps: Int): Int {
        var credited = 0
        val samples = (millis / 200).toInt().coerceAtLeast(1)
        var steps = startSteps
        for (i in 1..samples) {
            val at = from + millis * i / samples
            if (onFoot) observeSteps(++steps, at)
            credited += observePressure(hpaAt(fromMetres + (toMetres - fromMetres) * i / samples), at)
        }
        return credited
    }

    @Test fun `climbing thirty metres on foot is ten floors`() {
        val counter = StairsCounter()
        counter.observeSteps(100, 0)
        counter.observePressure(hpaAt(20f), 0)
        val credited = counter.climb(0, 20f, 50f, 120_000, onFoot = true, startSteps = 100)
        assertEquals(10, credited)
        assertEquals(10, counter.credited)
    }

    @Test fun `a lift is not a climb, and the stairs after it are only the stairs`() {
        val counter = StairsCounter()
        counter.observeSteps(100, 0)
        counter.observePressure(hpaAt(0f), 0)
        // Up twelve metres standing still.
        assertEquals(0, counter.climb(0, 0f, 12f, 20_000, onFoot = false, startSteps = 100))
        // Then two floors on foot.
        counter.observeSteps(101, 30_000)
        assertEquals(2, counter.climb(30_000, 12f, 18.5f, 30_000, onFoot = true, startSteps = 101))
    }

    @Test fun `walking into a lift leaves a recent step, and the ride still earns nothing`() {
        val counter = StairsCounter()
        counter.observePressure(hpaAt(0f), 0)
        // Walking up to the lift: a step every 600 ms for ten seconds.
        var steps = 0
        for (i in 1..16) counter.observeSteps(++steps, i * 600L)
        counter.observePressure(hpaAt(0f), 9_600)
        // Doors close; three seconds later it rises fifteen metres in twenty.
        assertEquals(0, counter.climb(12_600, 0f, 15f, 20_000, onFoot = false, startSteps = steps))
        assertEquals(0f, counter.ascentMetres, 0.01f)
        // Out of the lift and up two flights on foot: only those.
        assertEquals(2, counter.climb(40_000, 15f, 21.5f, 30_000, onFoot = true, startSteps = steps))
    }

    @Test fun `going down earns nothing and coming back up earns only the way back`() {
        val counter = StairsCounter()
        counter.observeSteps(0, 0)
        counter.observePressure(hpaAt(30f), 0)
        assertEquals(0, counter.climb(0, 30f, 15f, 40_000, onFoot = true, startSteps = 0))
        assertEquals(4, counter.climb(40_000, 15f, 28f, 40_000, onFoot = true, startSteps = 200))
    }

    @Test fun `the barometer's wobble is not a floor`() {
        val counter = StairsCounter()
        counter.observeSteps(0, 0)
        counter.observePressure(hpaAt(10f), 0)
        var credited = 0
        var steps = 0
        for (i in 1..300) {
            val at = i * 200L
            counter.observeSteps(++steps, at)
            val wobble = if (i % 2 == 0) 0.3f else -0.3f
            credited += counter.observePressure(hpaAt(10f + wobble), at)
        }
        assertEquals(0, credited)
        assertEquals(0f, counter.ascentMetres, 0.01f)
    }
}
