package io.joinasr.app.enforcement

import io.joinasr.app.usage.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A day belongs to the person, not to the handset.
 *
 * Signing in on a second phone used to hand back a whole fresh allowance,
 * because a phone can only measure its own screen and the new one opens on
 * zero. Thirty minutes of Instagram became sixty for the cost of signing in
 * -- once per phone, every day.
 */
class CarriedUsageTest {

    @Test
    fun `a new phone carries the whole day it did not see`() {
        val carried = CarriedUsage.elsewhere(
            serverTotals = mapOf("com.instagram.android" to 30),
            ownSoFar = emptyMap(),
        )
        assertEquals(mapOf("com.instagram.android" to 30), carried)
    }

    /**
     * The subtraction, which is the part that is easy to get wrong. A
     * reinstall on the *same* phone reads back a total that includes this
     * handset's own morning, and the counter is about to measure that same
     * morning again.
     */
    @Test
    fun `a reinstall does not count its own morning twice`() {
        val carried = CarriedUsage.elsewhere(
            serverTotals = mapOf("com.instagram.android" to 30),
            ownSoFar = mapOf("com.instagram.android" to 10),
        )
        assertEquals(mapOf("com.instagram.android" to 20), carried)
    }

    /** The phone can be ahead of the server: the last summary is minutes old. */
    @Test
    fun `a phone ahead of the server carries nothing rather than negative minutes`() {
        val carried = CarriedUsage.elsewhere(
            serverTotals = mapOf("com.instagram.android" to 30),
            ownSoFar = mapOf("com.instagram.android" to 34),
        )
        assertEquals(emptyMap<String, Int>(), carried)
    }

    @Test
    fun `apps nobody has opened elsewhere are left out`() {
        val carried = CarriedUsage.elsewhere(
            serverTotals = mapOf("com.instagram.android" to 30, "com.google.android.youtube" to 0),
            ownSoFar = emptyMap(),
        )
        assertEquals(mapOf("com.instagram.android" to 30), carried)
    }

    @Test
    fun `the merged day is what everything downstream decides against`() {
        val snapshot = UsageSnapshot(
            minutesByPackage = mapOf("com.instagram.android" to 4, "com.google.android.youtube" to 9),
            foregroundPackage = "com.instagram.android",
            dayStartMillis = 0,
        )

        val merged = snapshot.plus(mapOf("com.instagram.android" to 26))

        assertEquals(30, merged.minutesByPackage["com.instagram.android"])
        // Untouched, and the rest of the snapshot with it.
        assertEquals(9, merged.minutesByPackage["com.google.android.youtube"])
        assertEquals("com.instagram.android", merged.foregroundPackage)
    }

    @Test
    fun `nothing carried is the same snapshot`() {
        val snapshot = UsageSnapshot(mapOf("com.instagram.android" to 4), null, 0)
        assertEquals(snapshot, snapshot.plus(emptyMap()))
    }
}
