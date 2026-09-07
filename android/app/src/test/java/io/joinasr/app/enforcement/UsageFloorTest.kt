package io.joinasr.app.enforcement

import io.joinasr.app.usage.DayUsage
import io.joinasr.app.usage.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A day's minutes cannot be given back by uninstalling the app that spent
 * them.
 *
 * Android throws a package's usage events away when the package goes, so
 * uninstalling Instagram and installing it again emptied its day: "30 of 30
 * min" became "0 of 30 min", the block screen came down, and the allowance
 * was there to spend again -- as often as somebody liked, with the witnesses
 * told nothing, because as far as this phone could see nothing had been used.
 */
class UsageFloorTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"

    @Test
    fun `a day emptied by a reinstall keeps the minutes it held`() {
        // The exact shape of it: the package is not in the reading at all,
        // because the system has no events left to report for it.
        val kept = UsageFloor.highest(
            kept = mapOf(instagram to 30),
            measured = mapOf(youtube to 5),
        )

        assertEquals(mapOf(instagram to 30, youtube to 5), kept)
    }

    @Test
    fun `the higher figure wins, and the two are never added`() {
        // Two accounts of the same afternoon -- what the system can still
        // remember, and what this app wrote down while it could. Adding them
        // would count it twice and lock somebody out at fifteen minutes.
        assertEquals(
            mapOf(instagram to 30),
            UsageFloor.highest(kept = mapOf(instagram to 30), measured = mapOf(instagram to 12)),
        )
        assertEquals(
            mapOf(instagram to 30),
            UsageFloor.highest(kept = mapOf(instagram to 12), measured = mapOf(instagram to 30)),
        )
    }

    @Test
    fun `an ordinary minute still counts`() {
        // The common case by far: nothing was uninstalled and the day is
        // simply going on. Whatever this writes down has to be the new
        // reading, or the counter would stop.
        val kept = UsageFloor.highest(mapOf(instagram to 12), mapOf(instagram to 13, youtube to 1))

        assertEquals(mapOf(instagram to 13, youtube to 1), kept)
    }

    @Test
    fun `an empty side changes nothing`() {
        assertEquals(mapOf(instagram to 30), UsageFloor.highest(mapOf(instagram to 30), emptyMap()))
        assertEquals(mapOf(instagram to 30), UsageFloor.highest(emptyMap(), mapOf(instagram to 30)))
        assertEquals(emptyMap<String, Int>(), UsageFloor.highest(emptyMap(), emptyMap()))
    }

    @Test
    fun `the loop decides against the day, not against the reading`() {
        // What the enforcement loop actually composes: this phone's reading,
        // raised to what the day is known to have held, then the minutes
        // spent on the phone this challenge came from added on top. The
        // first is a maximum and the second is a sum, and they are different
        // facts about the same day.
        val reading = UsageSnapshot(
            minutesByPackage = mapOf(youtube to 5),
            foregroundPackage = instagram,
            dayStartMillis = 0,
        )

        val whole = reading
            .atLeast(mapOf(instagram to 30, youtube to 5))
            .plus(mapOf(instagram to 4))

        assertEquals(mapOf(instagram to 34, youtube to 5), whole.minutesByPackage)
    }

    @Test
    fun `a day in the week view keeps what it held`() {
        val day = DayUsage(dayStartMillis = 0, minutesByPackage = mapOf(youtube to 5))

        assertEquals(
            mapOf(instagram to 30, youtube to 5),
            day.atLeast(mapOf(instagram to 30)).minutesByPackage,
        )
    }
}
