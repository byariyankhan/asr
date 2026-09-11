package io.joinasr.app.earn

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Metres ridden on a bicycle, judged thirty seconds at a time from GPS,
 * the step counter and the accelerometer together.
 *
 * Speed alone cannot tell a bicycle from a car at 20 km/h, so a window
 * of [windowMillis] is credited only when everything about it looks like
 * riding and nothing looks like a vehicle, a runner, or a fake:
 *
 *  - **Pace.** The window's mean speed is a bicycle's, [minMeanSpeedMps]
 *    to [maxMeanSpeedMps] (8 to 40 km/h: slower is walking the bike or
 *    a light, faster sustained is a motor), and no single stretch
 *    between fixes exceeds [maxSegmentSpeedMps] (45 km/h, a downhill).
 *  - **Jostle.** A bicycle shakes the phone, in a pocket, a bag, or on
 *    the bars; a phone resting on a car seat, or on a desk under a
 *    spoofed route, does not. The accelerometer's magnitude over the
 *    window has to vary by at least [minJostleMps2].
 *  - **Smoothness.** Legs cannot change speed by [hardChangeMps] in a
 *    couple of seconds; a car braking for a light or pulling away can.
 *    More than [maxHardChanges] such changes in a window is a vehicle.
 *  - **Feet.** Steps at [maxStepsPerMinute] or more over the window is
 *    somebody running, whose speed can be a bicycle's.
 *  - **Honest fixes.** A fix from a mock provider spoils its window. A
 *    fix worse than [maxAccuracyMetres] is not a place and is ignored.
 *    Where the GPS chip reports its own speed (from Doppler, which no
 *    position edit can fake), the window's mean of it has to agree with
 *    the distance covered to within [maxDopplerMismatchMps]. A gap of
 *    more than [maxGapMillis] between fixes is a stretch nobody measured
 *    and adds no distance.
 *
 * None of this is proof. A car crawling in stop-free traffic with the
 * phone in a pocket on a rough road could pass; the aim is that cheating
 * takes more effort than cycling, not that it is impossible, and the
 * mechanism is honesty. Nothing here is a route: the last fix is the
 * only place remembered, and only until the next.
 *
 * Not thread-safe: drive it from one thread, with one monotonic clock for
 * every sensor.
 */
class RideCounter(
    private val windowMillis: Long = 30_000L,
    private val minMeanSpeedMps: Float = 8f / 3.6f,
    private val maxMeanSpeedMps: Float = 40f / 3.6f,
    private val maxSegmentSpeedMps: Float = 45f / 3.6f,
    private val maxAccuracyMetres: Float = 20f,
    private val maxGapMillis: Long = 15_000L,
    private val maxStepsPerMinute: Float = 100f,
    private val minJostleMps2: Float = 0.3f,
    private val hardChangeMps: Float = 3f,
    private val maxHardChanges: Int = 1,
    private val maxDopplerMismatchMps: Float = 2.5f,
) {
    /** Whole metres credited so far. */
    var credited: Int = 0
        private set

    /** Why the last window was refused, or null when it was credited or none has closed. For the log, never the server. */
    var lastRefusal: String? = null
        private set

    private var exact = 0.0

    // The last fix, the only place remembered.
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAt: Long? = null
    private var lastSpeed: Float? = null

    // The window being judged.
    private var windowStartAt: Long? = null
    private var windowMetres = 0.0
    private var dopplerSum = 0f
    private var dopplerCount = 0
    private var hardChanges = 0
    private var mock = false
    private var tooFast = false
    private var windowSteps = 0
    private var motionSum = 0.0
    private var motionSquares = 0.0
    private var motionCount = 0
    private var lastStepTotal: Int? = null

    /** One reading of the step counter's total. */
    fun observeSteps(total: Int, atMillis: Long) {
        val last = lastStepTotal
        lastStepTotal = total
        if (last != null && total > last) windowSteps += total - last
    }

    /** One accelerometer sample, in m/s². */
    fun observeMotion(x: Float, y: Float, z: Float, atMillis: Long) {
        val magnitude = sqrt(x * x + y * y + z * z).toDouble()
        motionSum += magnitude
        motionSquares += magnitude * magnitude
        motionCount++
    }

    /**
     * One GPS fix. [speedMps] is the chip's own speed when it reports one,
     * [mock] whether the fix came from a mock provider. Returns the whole
     * metres credited when this fix closed a window, usually zero.
     */
    fun observeFix(
        latitude: Double,
        longitude: Double,
        accuracyMetres: Float,
        speedMps: Float?,
        mock: Boolean,
        atMillis: Long,
    ): Int {
        if (mock) this.mock = true
        if (accuracyMetres > maxAccuracyMetres) return closeIfDue(atMillis)
        val before = lastAt
        val fromLat = lastLat
        val fromLon = lastLon
        val fromSpeed = lastSpeed
        lastLat = latitude
        lastLon = longitude
        lastAt = atMillis
        lastSpeed = speedMps
        if (windowStartAt == null) windowStartAt = atMillis
        if (before != null) {
            val dt = atMillis - before
            if (dt in 1..maxGapMillis) {
                val metres = distanceMetres(fromLat, fromLon, latitude, longitude)
                val segmentSpeed = (metres / (dt / 1000.0)).toFloat()
                if (segmentSpeed > maxSegmentSpeedMps) tooFast = true else windowMetres += metres
                // A change of speed legs could not make: from the chip's
                // speeds where it gives them, else from the positions.
                val v1 = fromSpeed
                val v2 = speedMps
                if (v1 != null && v2 != null && dt <= 2_000L && abs(v2 - v1) >= hardChangeMps) hardChanges++
            }
        }
        if (speedMps != null) {
            dopplerSum += speedMps
            dopplerCount++
        }
        return closeIfDue(atMillis)
    }

    /** Judges the window if [atMillis] is past its end, and starts the next. */
    private fun closeIfDue(atMillis: Long): Int {
        val start = windowStartAt ?: return 0
        val elapsed = atMillis - start
        if (elapsed < windowMillis) return 0
        val seconds = elapsed / 1000.0
        val meanSpeed = (windowMetres / seconds).toFloat()
        val cadence = windowSteps * 60_000f / elapsed
        val jostle = if (motionCount >= 5) {
            val mean = motionSum / motionCount
            sqrt((motionSquares / motionCount - mean * mean).coerceAtLeast(0.0)).toFloat()
        } else {
            0f
        }
        val doppler = if (dopplerCount >= 3) dopplerSum / dopplerCount else null
        val refusal = when {
            mock -> "a fix from a mock provider"
            tooFast -> "a stretch faster than a bicycle"
            meanSpeed < minMeanSpeedMps -> "too slow to be riding"
            meanSpeed > maxMeanSpeedMps -> "too fast, sustained, to be riding"
            hardChanges > maxHardChanges -> "speed changes a vehicle makes and legs do not"
            cadence >= maxStepsPerMinute -> "running, not riding"
            jostle < minJostleMps2 -> "the phone was not moving with a bicycle"
            doppler != null && abs(doppler - meanSpeed) > maxDopplerMismatchMps -> "the chip's speed and the positions disagree"
            else -> null
        }
        lastRefusal = refusal
        if (refusal == null) exact += windowMetres
        windowStartAt = atMillis
        windowMetres = 0.0
        dopplerSum = 0f
        dopplerCount = 0
        hardChanges = 0
        mock = false
        tooFast = false
        windowSteps = 0
        motionSum = 0.0
        motionSquares = 0.0
        motionCount = 0
        val whole = exact.toInt()
        val earned = whole - credited
        credited = whole
        return earned
    }

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_000.0

        /** Great-circle distance, which over a few metres is the distance. */
        fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_METRES * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
