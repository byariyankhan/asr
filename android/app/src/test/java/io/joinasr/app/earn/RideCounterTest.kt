package io.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCounterTest {

    /** One degree of latitude is about 111 km, so this many degrees is [metres] due north. */
    private fun north(metres: Double) = metres / 111_320.0

    /**
     * A journey north at [kmh] with a fix every second for [seconds],
     * the chip reporting its speed (unless [doppler] is false), the
     * accelerometer sampled five times a second with [jostle] of shake,
     * and [cadence] steps a minute. Returns what was credited.
     */
    private fun RideCounter.journey(
        from: Long,
        kmh: Double,
        seconds: Int,
        jostle: Float = 0.8f,
        cadence: Float = 0f,
        accuracy: Float = 6f,
        doppler: Boolean = true,
        mock: Boolean = false,
        startLat: Double = 23.8,
        speedNoise: (Int) -> Float = { 0f },
    ): Int {
        var credited = 0
        val mps = kmh / 3.6
        var steps = 0
        var stepDue = 0.0
        for (i in 0..seconds) {
            val at = from + i * 1_000L
            // Five motion samples a second: road bumps, which are vertical
            // and add to gravity, of ±jostle.
            for (k in 0 until 5) {
                val bump = if ((i * 5 + k) % 2 == 0) jostle else -jostle
                observeMotion(0.1f, 0.2f, 9.81f + bump, at + k * 200L)
            }
            stepDue += cadence / 60f
            while (stepDue >= 1f) {
                steps++
                stepDue -= 1f
            }
            observeSteps(steps, at)
            credited += observeFix(
                startLat + north(mps * i), 90.4, accuracy,
                if (doppler) (mps.toFloat() + speedNoise(i)) else null, mock, at,
            )
        }
        return credited
    }

    @Test fun `a real ride at twenty kilometres an hour is credited, window by window`() {
        val counter = RideCounter()
        // 20 km/h for 9 minutes is 3 km; the last part-window is not yet judged.
        val credited = counter.journey(0, 20.0, 540)
        assertTrue("$credited", credited in 2_950..3_010)
        assertNull(counter.lastRefusal)
    }

    @Test fun `walking the bike and a fast car both credit nothing`() {
        val counter = RideCounter()
        assertEquals(0, counter.journey(0, 5.0, 90))
        assertEquals("too slow to be riding", counter.lastRefusal)
        assertEquals(0, counter.journey(200_000, 60.0, 90))
        assertEquals("a stretch faster than a bicycle", counter.lastRefusal)
    }

    @Test fun `a phone lying still in a car credits nothing however cycling-like the speed`() {
        val counter = RideCounter()
        assertEquals(0, counter.journey(0, 22.0, 120, jostle = 0.05f))
        assertEquals("the phone was not moving with a bicycle", counter.lastRefusal)
    }

    @Test fun `stop-and-go traffic is a vehicle, not legs`() {
        val counter = RideCounter()
        // 25 km/h with the chip's speed lurching by 4 m/s every ten seconds: braking and pulling away.
        val credited = counter.journey(0, 25.0, 90, speedNoise = { i -> if (i % 10 == 0) 4f else 0f })
        assertEquals(0, credited)
        assertEquals("speed changes a vehicle makes and legs do not", counter.lastRefusal)
    }

    @Test fun `running at a bicycle's speed is running`() {
        val counter = RideCounter()
        assertEquals(0, counter.journey(0, 12.0, 90, cadence = 160f))
        assertEquals("running, not riding", counter.lastRefusal)
    }

    @Test fun `a mock location spoils its window`() {
        val counter = RideCounter()
        assertEquals(0, counter.journey(0, 20.0, 90, mock = true))
        assertEquals("a fix from a mock provider", counter.lastRefusal)
    }

    @Test fun `positions that move while the chip says the phone is still are not a ride`() {
        val counter = RideCounter()
        // A spoofed path: positions advance at 20 km/h, the chip's own speed reads zero.
        val credited = counter.journey(0, 20.0, 90, speedNoise = { -(20f / 3.6f) })
        assertEquals(0, credited)
        assertEquals("the chip's speed and the positions disagree", counter.lastRefusal)
    }

    @Test fun `a teleport spoils its window, and honest riding after it is still a ride`() {
        val counter = RideCounter()
        counter.journey(0, 20.0, 20)
        // Five kilometres away one second later, then on at a cycling pace from there.
        val far = 23.8 + north(5_000.0)
        assertEquals(0, counter.observeFix(far, 90.4, 6f, 5.6f, false, 21_000))
        // The window with the jump in it closes at 30 s and is refused whole.
        assertEquals(0, counter.journey(22_000, 20.0, 8, startLat = far))
        assertEquals("a stretch faster than a bicycle", counter.lastRefusal)
        // The next window is riding, from wherever the phone now is.
        val after = counter.journey(31_000, 20.0, 30, startLat = far + north(20.0 / 3.6 * 9))
        assertTrue("$after", after > 140)
        assertNull(counter.lastRefusal)
    }

    @Test fun `a fix that is not a place is ignored and the ride goes on`() {
        val counter = RideCounter()
        var credited = counter.journey(0, 20.0, 40)
        assertEquals(0, counter.observeFix(23.8 + north(1_000.0), 90.4, 80f, 5.6f, false, 41_000))
        credited += counter.journey(42_000, 20.0, 60, startLat = 23.8 + north(20.0 / 3.6 * 42))
        assertTrue("$credited", credited > 400)
    }

    @Test fun `without the chip's speed the positions decide, and an honest ride still counts`() {
        val counter = RideCounter()
        // Four windows of thirty seconds at 5.56 m/s.
        val credited = counter.journey(0, 20.0, 120, doppler = false)
        assertTrue("$credited", credited in 650..680)
    }

    @Test fun `a red light in the window is not a car`() {
        val counter = RideCounter()
        // Twenty seconds at 25 km/h, ten seconds stopped, the chip's speed
        // easing to zero and back rather than lurching: still a bicycle.
        var credited = 0
        val mps = 25.0 / 3.6
        var lat = 23.8
        var speed = mps
        for (i in 0..59) {
            val at = i * 1_000L
            val stopped = i % 30 in 20..29
            val target = if (stopped) 0.0 else mps
            speed += (target - speed).coerceIn(-2.0, 2.0)
            lat += north(speed)
            for (k in 0 until 5) observeMotionJostle(counter, at + k * 200L, if (stopped) 0.4f else 0.8f, i * 5 + k)
            credited += counter.observeFix(lat, 90.4, 6f, speed.toFloat(), false, at)
        }
        // One window closed, at 30 s: twenty seconds of riding and ten at
        // the light, which is still a bicycle's mean pace.
        assertTrue("$credited", credited > 100)
        assertNull(counter.lastRefusal)
    }

    private fun observeMotionJostle(counter: RideCounter, at: Long, jostle: Float, n: Int) {
        counter.observeMotion(0.1f, 0.2f, 9.81f + if (n % 2 == 0) jostle else -jostle, at)
    }
}
