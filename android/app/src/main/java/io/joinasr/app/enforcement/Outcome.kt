package io.joinasr.app.enforcement

import kotlinx.serialization.Serializable

/** How a challenge ended. */
enum class PactResult { Failed, Completed }

/**
 * The moment a limit stopped holding, on a challenge that ended for it.
 *
 * Historical. No challenge ends this way any more; see
 * `Enforcement.overLimit`. Kept so an outcome written by an older build
 * still decodes and still shows the person what happened.
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
    /**
     * Only on outcomes written before going over a limit stopped ending a
     * challenge. Nothing produces one now -- a failure is a give-up, and a
     * spent limit is reported and blocked rather than punished -- but they
     * are on people's phones and are still theirs to read.
     */
    val breach: Breach? = null,
    /**
     * How many witnesses were on the challenge when it ended. What the
     * screen says about them depends on whether the report actually went
     * out, which is [reported].
     */
    val witnesses: Int = 0,
    /** Whether the server has been told. False means it is still queued. */
    val reported: Boolean = false,
    /**
     * Who those witnesses were, in the words the ending screen uses about
     * them. Empty on outcomes written before this was kept, which is why
     * [witnesses] stays: the count is still right on those.
     */
    val witnessesTold: List<WitnessTold> = emptyList(),
)

/** One person who was told how a challenge ended: what to call them, and their pronoun's source. */
@Serializable
data class WitnessTold(val label: String, val gender: String? = null)

/**
 * A finished challenge, back in the shape the sync layer needs to address
 * it. Only ever used to find or create the server's copy of something that
 * has already ended, which is why nothing enforces it.
 */
fun PactOutcome.asPact() = Pact(
    apps = apps,
    startedAtMillis = startedAtMillis,
    durationDays = durationDays,
)
