package io.joinasr.app.challenge

/** One of the offered lengths, with the word the design puts under it. */
data class DurationPreset(val days: Int, val caption: String)

/**
 * How long a challenge runs.
 *
 * The bounds are a product decision rather than a technical one. Below three
 * days there is nothing to break a habit with, and above ninety a person is
 * committing to something they cannot picture — and this app makes
 * commitments hard to leave, so the length somebody picks in an optimistic
 * minute has to be one they can live with for the rest of it.
 */
object ChallengeDuration {

    val Presets: List<DurationPreset> = listOf(
        DurationPreset(7, "Starter"),
        DurationPreset(14, "Recommended"),
        DurationPreset(21, "Build discipline"),
        DurationPreset(30, "Full reset"),
    )

    const val DEFAULT_DAYS = 14
    const val MINIMUM_DAYS = 3
    const val MAXIMUM_DAYS = 90

    fun clamp(days: Int): Int = days.coerceIn(MINIMUM_DAYS, MAXIMUM_DAYS)

    fun isPreset(days: Int): Boolean = Presets.any { it.days == days }

    /**
     * The line under the tiles. It says something true about the length
     * chosen rather than congratulating the person for choosing anything,
     * which is what most of these lines are for.
     */
    fun note(days: Int): String = when {
        days <= 7 -> "A week is long enough to notice the habit."
        days <= 14 -> "$days days is a strong first challenge."
        days <= 21 -> "Three weeks is where a habit starts to hold."
        days <= 30 -> "A month is a full reset."
        else -> "$days days is a long commitment. You cannot shorten it once it starts."
    }
}
