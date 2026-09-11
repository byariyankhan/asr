package io.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCounterTest {

    /** One degree of latitude is about 111 km, so this many degrees is [metres] due north. */
    private fun north(metres: Double) = metres / 111_320.0

    /** Fixes every [everyMillis] along a road heading north at [kmh], for [seconds]. Returns what was credited. */
    private fun RideCounter.ride(
        from: Long,
        kmh: Double,
        seconds: Int,
        everyMillis: Long = 2_000L,
        accuracy: Float = 8f,
        cadence: Float = 0f,
        startLat: Double = 23.8,
    ): Int {
        var credited = 0
        val fixes = seconds * 1000 / everyMillis
        val perFix = kmh / 3.6 * everyMillis / 1000.0
        for (i in 0..fixes) {
            credited += observe(startLat + north(perFix * i), 90.4, accuracy, from + i * everyMillis, cadence)
        }
        return credited
    }

    @Test fun `twenty kilometres an hour is a ride, and the metres add up`() {
        val counter = RideCounter()
        // 20 km/h for 9 minutes is 3 km, within a metre of rounding.
        val credited = counter.ride(0, 20.0, 540)
        assertTrue("$credited", credited in 2_990..3_010)
        assertEquals(credited, counter.credited)
    }

    @Test fun `walking pace and a car's pace both credit nothing`() {
        val counter = RideCounter()
        assertEquals(0, counter.ride(0, 5.0, 120))
        assertEquals(0, counter.ride(200_000, 60.0, 120))
    }

    @Test fun `running at a bicycle's speed is running`() {
        val counter = RideCounter()
        assertEquals(0, counter.ride(0, 12.0, 120, cadence = 160f))
        // The same two minutes without the steps: 400 m, near enough.
        val riding = counter.ride(200_000, 12.0, 120, cadence = 0f)
        assertTrue("$riding", riding in 380..400)
    }

    @Test fun `a fix that is not a place is ignored, and the ride goes on from the last good one`() {
        val counter = RideCounter()
        counter.ride(0, 20.0, 20)
        val before = counter.credited
        // A bad fix a kilometre off, then back on the road: the bad one is
        // never compared with anything.
        assertEquals(0, counter.observe(23.8 + north(1_000.0), 90.4, 80f, 22_000))
        assertTrue(counter.ride(24_000, 20.0, 20, startLat = 23.8 + north(20.0 / 3.6 * 22)) > 0)
        assertTrue(counter.credited > before)
    }

    @Test fun `a gap in the fixes is a stretch nobody measured`() {
        val counter = RideCounter()
        counter.ride(0, 20.0, 20)
        val before = counter.credited
        // Sixty seconds later and a kilometre on: no continuity, no credit.
        assertEquals(0, counter.observe(23.8 + north(1_000.0), 90.4, 8f, 80_000))
        assertEquals(before, counter.credited)
    }
}
