package io.joinasr.app.enforcement

import kotlinx.serialization.Serializable

/** How a challenge ended. */
enum class PactResult { Failed, Completed }

/**
 * The moment a limit stopped holding.
 *
 * Recorded with the numbers that were true when it happened rather than
 * recomputed later: the day's counts reset at midnight, and a failure screen
 * that reads "0 of 15 min used" the next morning would be worse than no
 * screen at all.
 */
@Serializable
data class Breach(
    val packageName: String,
    val label: String,
    val limitMinutes: Int,
    val usedMinutes: Int,
    val atMillis: Long,
    val dayNumber: Int,
)

/**
 * A finished challenge, kept after the pact itself is gone.
 *
 * Separate from [Pact] because a pact is a thing being enforced and this is
 * a thing that happened. The enforcement loop reads pacts and must never see
 * this; the screens read this and must never be able to bring a challenge
 * back. That the two are different files is the point.
 */
@Serializable
data class PactOutcome(
    val result: PactResult,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationDays: Int,
    val apps: List<PactApp>,
    /** Present exactly when [result] is [PactResult.Failed]. */
    val breach: Breach? = null,
    /**
     * How many witnesses were on the challenge when it ended. What the
     * screen says about them depends on whether the report actually went
     * out, which is [reported].
     */
    val witnesses: Int = 0,
    /** Whether the server has been told. False means it is still queued. */
    val reported: Boolean = false,
)
