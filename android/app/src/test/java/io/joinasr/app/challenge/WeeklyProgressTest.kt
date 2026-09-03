package io.joinasr.app.challenge

import io.joinasr.app.usage.DayUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyProgressTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val limits = mapOf(instagram to 15, youtube to 30)

    private fun day(index: Int, vararg used: Pair<String, Int>) =
        DayUsage(dayStartMillis = index * 86_400_000L, minutesByPackage = used.toMap())

    @Test
    fun `a day is within limits only when every app is`() {
        // The rule that matters. Somebody who spends the whole allowance in
        // one app has broken that app's limit, and a rule that added the day
        // up would tell them they were fine: 40 and 0 is under 45 in total
        // and well over Instagram's 15.
        val outcomes = WeeklyProgress.outcomes(
            listOf(day(0, instagram to 40, youtube to 0)),
            limits,
        )
        assertFalse(outcomes.single().withinLimits)
        assertEquals(40, outcomes.single().totalMinutes)
    }

    @Test
    fun `exactly at the limit is still within it`() {
        val outcomes = WeeklyProgress.outcomes(
            listOf(day(0, instagram to 15, youtube to 30)),
            limits,
        )
        assertTrue(outcomes.single().withinLimits)
        assertEquals(45, outcomes.single().totalMinutes)
    }

    @Test
    fun `a day with nothing on it is within limits`() {
        val outcomes = WeeklyProgress.outcomes(listOf(day(0)), limits)
        assertTrue(outcomes.single().withinLimits)
        assertEquals(0, outcomes.single().totalMinutes)
    }

    @Test
    fun `apps outside the pact are not counted at all`() {
        val outcomes = WeeklyProgress.outcomes(
            listOf(day(0, instagram to 5, "com.some.other" to 500)),
            limits,
        )
        assertTrue(outcomes.single().withinLimits)
        assertEquals(5, outcomes.single().totalMinutes)
    }

    @Test
    fun `the week counts good days and bad days`() {
        val week = listOf(
            day(0, instagram to 5),
            day(1, instagram to 40),
            day(2, youtube to 10),
            day(3),
            day(4, youtube to 90),
        )
        val outcomes = WeeklyProgress.outcomes(week, limits)
        assertEquals(3, WeeklyProgress.daysWithinLimits(outcomes))
        assertEquals(2, WeeklyProgress.breaches(outcomes))
        val counted = WeeklyProgress.daysWithinLimits(outcomes) + WeeklyProgress.breaches(outcomes)
        assertEquals(outcomes.size, counted)
    }

    @Test
    fun `the allowance is every limit added up`() {
        assertEquals(45, WeeklyProgress.dailyAllowance(limits))
        assertEquals(0, WeeklyProgress.dailyAllowance(emptyMap()))
    }

    @Test
    fun `the chart is tall enough for the days that went over`() {
        // Scaling to the allowance alone would clip exactly the bars worth
        // looking at.
        val outcomes = WeeklyProgress.outcomes(listOf(day(0, youtube to 120)), limits)
        assertEquals(120, WeeklyProgress.chartCeiling(outcomes, allowance = 45))
        assertEquals(45, WeeklyProgress.chartCeiling(outcomes, allowance = 200).coerceAtMost(45))
    }

    @Test
    fun `an empty week still gives the chart something to divide by`() {
        assertTrue(WeeklyProgress.chartCeiling(emptyList(), allowance = 0) >= 1)
    }
}
