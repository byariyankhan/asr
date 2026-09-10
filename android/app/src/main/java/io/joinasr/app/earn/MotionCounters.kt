package io.joinasr.app.earn

import kotlin.math.pow

/**
 * Steps taken at a running pace, from the phone's step counter.
 *
 * The counter is a running total with no timestamps of its own, so pace
 * is read from how many steps arrive in each window of [windowMillis]:
 * a window at [cadenceStepsPerMinute] or above is running and its steps
 * count; a slower one is walking and its steps do not. Twenty seconds is
 * long enough that a burst of three fast steps is not a run and short
 * enough that a run that stops for a light loses one window, not a
 * minute. No GPS, no distance: cadence is the whole of it, which is how a
 * watch tells the two apart as well.
 *
 * A total below the last one is a reboot's reset: the window starts over
 * from the new number, and nothing is counted backwards.
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class RunCounter(
    private val cadenceStepsPerMinute: Int = 140,
    private val windowMillis: Long = 20_000L,
) {
    /** Running steps credited so far. */
    var credited: Int = 0
        private set

    private var windowStartTotal: Int? = null
    private var windowStartAt: Long = 0L
    private var lastTotal: Int? = null

    /** One reading of the total. Returns the running steps this reading credited, usually zero. */
    fun observe(total: Int, atMillis: Long): Int {
        val last = lastTotal
        lastTotal = total
        val start = windowStartTotal
        if (start == null || last == null || total < last) {
            windowStartTotal = total
            windowStartAt = atMillis
            return 0
        }
        val elapsed = atMillis - windowStartAt
        if (elapsed < windowMillis) return 0
        val steps = total - start
        windowStartTotal = total
        windowStartAt = atMillis
        val cadence = steps * 60_000.0 / elapsed
        if (cadence < cadenceStepsPerMinute) return 0
        credited += steps
        return steps
    }
}

/**
 * Floors climbed, from the barometer while the step counter moves.
 *
 * Pressure falls as the phone rises, about a hectopascal every eight
 * metres near sea level; the standard atmosphere turns a reading into an
 * altitude, and a rise in that altitude of [metresPerFloor] is a floor.
 * A floor is taken as 2.8 m, a little under a typical storey, so that ten
 * real floors do not come out as nine through the smoothing's lag or a
 * building with low ceilings; the error is in the person's favour.
 * Only a rise made on foot counts: a step has to have arrived within
 * [stepRecencyMillis], which is what keeps a lift from being a climb. A
 * rise without steps, or a descent, moves the reference and credits
 * nothing, so the person who takes the lift up and the stairs down earns
 * nothing, and the one who takes the stairs up after the lift earns only
 * the stairs.
 *
 * The barometer is noisy by a few tenths of a metre and drifts with the
 * weather by less than a metre an hour; readings are smoothed and a rise
 * has to reach [deadbandMetres] before it is banked, which is smaller
 * than any floor and larger than any wobble. Readings are absolute, so
 * the phone may be anywhere in the building when the activity starts.
 *
 * Not thread-safe: drive it from one thread, with one monotonic clock for
 * both sensors.
 */
class StairsCounter(
    private val metresPerFloor: Float = 2.8f,
    private val stepRecencyMillis: Long = 8_000L,
    private val deadbandMetres: Float = 0.8f,
    private val smoothing: Float = 0.3f,
) {
    /** Floors credited so far. */
    var credited: Int = 0
        private set

    /** Metres climbed on foot so far, whole floors and part. */
    var ascentMetres: Float = 0f
        private set

    private var smoothed: Float? = null
    private var reference: Float? = null
    private var lastStepAt: Long? = null
    private var lastTotal: Int? = null

    /** One reading of the step counter's total. */
    fun observeSteps(total: Int, atMillis: Long) {
        val last = lastTotal
        lastTotal = total
        if (last != null && total > last) lastStepAt = atMillis
    }

    /** One reading of the barometer, in hectopascals. Returns the floors this reading credited, usually zero. */
    fun observePressure(hectopascals: Float, atMillis: Long): Int {
        val altitude = altitudeMetres(hectopascals)
        val level = smoothed?.let { it + (altitude - it) * smoothing } ?: altitude
        smoothed = level
        val base = reference ?: run {
            reference = level
            return 0
        }
        val rise = level - base
        val onFoot = lastStepAt?.let { atMillis - it <= stepRecencyMillis } == true
        when {
            rise >= deadbandMetres && onFoot -> {
                ascentMetres += rise
                reference = level
            }
            // A rise not made on foot, or a descent: follow it, bank nothing.
            rise >= deadbandMetres || rise <= -deadbandMetres -> reference = level
        }
        val floors = (ascentMetres / metresPerFloor).toInt()
        val earned = floors - credited
        credited = floors
        return earned
    }

    private companion object {
        const val SEA_LEVEL_HPA = 1013.25f

        /** The standard atmosphere, as SensorManager.getAltitude computes it. */
        fun altitudeMetres(hectopascals: Float): Float =
            44_330f * (1f - (hectopascals / SEA_LEVEL_HPA).toDouble().pow(1.0 / 5.255).toFloat())
    }
}
