package com.joinasr.app.earn

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
 * Every reading is put in the window its own timestamp falls in, and a
 * window is judged only once a fix arrives [graceMillis] past its end:
 * the step counter and the accelerometer are delivered in batches a few
 * seconds late, and a step taken just before the boundary belongs to the
 * window before it, whenever it happens to arrive. Where the chip gives
 * no Doppler speed, consecutive positional speeds stand in for it at
 * [hardChangePositionalMps], wider, because positions are noisier.
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
    private val hardChangePositionalMps: Float = 4.5f,
    private val maxHardChanges: Int = 1,
    private val maxDopplerMismatchMps: Float = 2.5f,
    private val graceMillis: Long = 6_000L,
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
    private var lastSegmentSpeed: Float? = null
    private var lastStepTotal: Int? = null

    /** When the first fix arrived: windows are counted from here. */
    private var origin: Long? = null

    /** What one window has seen so far, by window index; judged and dropped once its grace has passed. */
    private class Window {
        var metres = 0.0
        var dopplerSum = 0f
        var dopplerCount = 0
        var hardChanges = 0
        var mock = false
        var tooFast = false
        var steps = 0
        var motionSum = 0.0
        var motionSquares = 0.0
        var motionCount = 0
    }

    private val windows = sortedMapOf<Long, Window>()
    private var judged = -1L

    private fun windowOf(atMillis: Long): Window? {
        val start = origin ?: return null
        val index = (atMillis - start) / windowMillis
        if (index <= judged) return null
        return windows.getOrPut(index) { Window() }
    }

    /** One reading of the step counter's total. */
    fun observeSteps(total: Int, atMillis: Long) {
        val last = lastStepTotal
        lastStepTotal = total
        if (last == null || total <= last) return
        windowOf(atMillis)?.let { it.steps += total - last }
    }

    /** One accelerometer sample, in m/s². */
    fun observeMotion(x: Float, y: Float, z: Float, atMillis: Long) {
        val window = windowOf(atMillis) ?: return
        val magnitude = sqrt(x * x + y * y + z * z).toDouble()
        window.motionSum += magnitude
        window.motionSquares += magnitude * magnitude
        window.motionCount++
    }

    /**
     * One GPS fix. [speedMps] is the chip's own speed when it reports one,
     * [mock] whether the fix came from a mock provider. Returns the whole
     * metres credited by any window this fix let close, usually zero.
     */
    fun observeFix(
        latitude: Double,
        longitude: Double,
        accuracyMetres: Float,
        speedMps: Float?,
        mock: Boolean,
        atMillis: Long,
    ): Int {
        if (origin == null) origin = atMillis
        val window = windowOf(atMillis)
        if (mock) window?.mock = true
        if (accuracyMetres > maxAccuracyMetres) return judgeDue(atMillis)
        val before = lastAt
        val fromLat = lastLat
        val fromLon = lastLon
        val fromSpeed = lastSpeed
        val fromSegmentSpeed = lastSegmentSpeed
        lastLat = latitude
        lastLon = longitude
        lastAt = atMillis
        lastSpeed = speedMps
        if (before != null && window != null) {
            val dt = atMillis - before
            if (dt in 1..maxGapMillis) {
                val metres = distanceMetres(fromLat, fromLon, latitude, longitude)
                val segmentSpeed = (metres / (dt / 1000.0)).toFloat()
                if (segmentSpeed > maxSegmentSpeedMps) window.tooFast = true else window.metres += metres
                // A change of speed legs could not make: from the chip's
                // speeds where it gives them, else from the positions,
                // which are noisier and get a wider margin.
                if (dt <= 2_000L) {
                    val v1 = fromSpeed
                    val v2 = speedMps
                    val hard = if (v1 != null && v2 != null) {
                        abs(v2 - v1) >= hardChangeMps
                    } else {
                        fromSegmentSpeed != null && abs(segmentSpeed - fromSegmentSpeed) >= hardChangePositionalMps
                    }
                    if (hard) window.hardChanges++
                }
                lastSegmentSpeed = segmentSpeed
            } else {
                lastSegmentSpeed = null
            }
        }
        if (speedMps != null && window != null) {
            window.dopplerSum += speedMps
            window.dopplerCount++
        }
        return judgeDue(atMillis)
    }

    /** Judges every window whose end plus grace is behind [atMillis], oldest first. */
    private fun judgeDue(atMillis: Long): Int {
        val start = origin ?: return 0
        var earned = 0
        while (true) {
            val index = judged + 1
            val end = start + (index + 1) * windowMillis
            if (atMillis < end + graceMillis) break
            val window = windows.remove(index)
            judged = index
            if (window != null) earned += judge(window)
        }
        return earned
    }

    private fun judge(window: Window): Int {
        val seconds = windowMillis / 1000.0
        val meanSpeed = (window.metres / seconds).toFloat()
        val cadence = window.steps * 60_000f / windowMillis
        val jostle = if (window.motionCount >= 5) {
            val mean = window.motionSum / window.motionCount
            sqrt((window.motionSquares / window.motionCount - mean * mean).coerceAtLeast(0.0)).toFloat()
        } else {
            0f
        }
        val doppler = if (window.dopplerCount >= 3) window.dopplerSum / window.dopplerCount else null
        val refusal = when {
            window.mock -> "a fix from a mock provider"
            window.tooFast -> "a stretch faster than a bicycle"
            meanSpeed < minMeanSpeedMps -> "too slow to be riding"
            meanSpeed > maxMeanSpeedMps -> "too fast, sustained, to be riding"
            window.hardChanges > maxHardChanges -> "speed changes a vehicle makes and legs do not"
            cadence >= maxStepsPerMinute -> "running, not riding"
            jostle < minJostleMps2 -> "the phone was not moving with a bicycle"
            doppler != null && abs(doppler - meanSpeed) > maxDopplerMismatchMps -> "the chip's speed and the positions disagree"
            else -> null
        }
        lastRefusal = refusal
        if (refusal == null) exact += window.metres
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
