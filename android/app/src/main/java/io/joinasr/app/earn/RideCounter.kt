package io.joinasr.app.earn

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Metres ridden at a cycling speed, from GPS fixes.
 *
 * Each fix is compared with the last one accepted: the distance between
 * them over the time between them is a speed, and the distance counts
 * when that speed is a bicycle's, between [minSpeedMps] (8 km/h; slower
 * is walking, or a light) and [maxSpeedMps] (45 km/h; faster is a car).
 * A fix worse than [maxAccuracyMetres] is not a place, and is ignored; a
 * gap longer than [maxGapMillis] between accepted fixes (a tunnel, the
 * app killed) is a stretch nobody measured, and credits nothing. Steps
 * at [maxStepsPerMinute] or more over the last stretch are somebody
 * running, whose speed can be a bicycle's, and credit nothing either.
 *
 * A slow drive in traffic would pass. The phone cannot tell a bicycle
 * from a car at 20 km/h, and the mechanism is honesty. No route is kept:
 * the last fix is the only one remembered, and only until the next.
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class RideCounter(
    private val minSpeedMps: Float = 8f / 3.6f,
    private val maxSpeedMps: Float = 45f / 3.6f,
    private val maxAccuracyMetres: Float = 30f,
    private val maxGapMillis: Long = 15_000L,
    private val maxStepsPerMinute: Float = 100f,
) {
    /** Whole metres credited so far. */
    var credited: Int = 0
        private set

    private var exact = 0.0
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAt: Long? = null

    /**
     * One fix. [stepsPerMinute] is the step cadence over the last stretch,
     * from the step counter, or zero when there is none. Returns the whole
     * metres this fix credited, usually a handful.
     */
    fun observe(latitude: Double, longitude: Double, accuracyMetres: Float, atMillis: Long, stepsPerMinute: Float = 0f): Int {
        if (accuracyMetres > maxAccuracyMetres) return 0
        val before = lastAt
        val fromLat = lastLat
        val fromLon = lastLon
        lastLat = latitude
        lastLon = longitude
        lastAt = atMillis
        if (before == null) return 0
        val dt = atMillis - before
        if (dt <= 0 || dt > maxGapMillis) return 0
        val metres = distanceMetres(fromLat, fromLon, latitude, longitude)
        val speed = metres / (dt / 1000.0)
        if (speed < minSpeedMps || speed > maxSpeedMps) return 0
        if (stepsPerMinute >= maxStepsPerMinute) return 0
        exact += metres
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
