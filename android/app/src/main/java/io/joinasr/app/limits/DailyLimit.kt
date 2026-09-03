package io.joinasr.app.limits

/**
 * The daily limit a person can set for one app, and the values it can take.
 *
 * A ladder of allowed values rather than a step size, because a step size has
 * to change as the number grows — nobody sets a four-hour limit in five
 * minute increments, and nobody wants a fifteen-minute jump between 15 and
 * 30 — and a step size that changes gives an asymmetric control: press plus
 * then minus and you are somewhere new. Moving along a fixed ladder cannot
 * do that.
 *
 * The ladder is five minutes to an hour in fives, then to four hours in
 * fifteens. Four hours is the top on purpose: this is a commitment app, and
 * a "limit" longer than a working afternoon is not one.
 */
object DailyLimit {

    val Ladder: List<Int> = ((5..60 step 5) + (75..240 step 15)).toList()

    val MINIMUM_MINUTES: Int = Ladder.first()
    val MAXIMUM_MINUTES: Int = Ladder.last()

    /**
     * What a newly chosen app starts at. Half an hour is deliberately not
     * generous: the person is about to reduce it or raise it, and a default
     * that already feels comfortable is one nobody thinks about.
     */
    const val DEFAULT_MINUTES: Int = 30

    /**
     * The nearest allowed value. Anything can arrive here — a limit restored
     * from an older version of the app, or one this ladder no longer
     * contains — and the answer has to be on the ladder or plus and minus
     * would walk somewhere unreachable.
     */
    fun snapped(minutes: Int): Int = Ladder.minBy { kotlin.math.abs(it - minutes) }

    fun increased(minutes: Int): Int {
        val from = snapped(minutes)
        return Ladder.firstOrNull { it > from } ?: MAXIMUM_MINUTES
    }

    fun decreased(minutes: Int): Int {
        val from = snapped(minutes)
        return Ladder.lastOrNull { it < from } ?: MINIMUM_MINUTES
    }

    fun canIncrease(minutes: Int): Boolean = snapped(minutes) < MAXIMUM_MINUTES

    fun canDecrease(minutes: Int): Boolean = snapped(minutes) > MINIMUM_MINUTES

    /** A starting limit for every app the person chose, in the given order. */
    fun defaultsFor(packageNames: List<String>): Map<String, Int> =
        packageNames.associateWith { DEFAULT_MINUTES }
}
