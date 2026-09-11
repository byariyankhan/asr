package io.joinasr.app.earn

import kotlin.math.sqrt

/**
 * Whether the phone is lying still, from the accelerometer, and for how
 * long: the proof behind a meditation. Not that anybody meditated, which
 * nothing on a phone can tell, but that the phone was put down and left
 * for ten minutes with the breathing guide on its screen.
 *
 * A sample is "still" when the acceleration vector has moved less than
 * [maxJitter] m/s² since the last sample, which a phone on a table or a
 * lap does not exceed and a phone in a hand or a pocket does. The
 * [HoldTimer] settles that over [settleMillis] and hands out the seconds.
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class StillnessJudge(
    private val maxJitter: Float = 0.35f,
    settleMillis: Long = 1_000L,
    maxFrameGapMillis: Long = 1_000L,
) {
    private val timer = HoldTimer(settleMillis, maxFrameGapMillis)
    private var last: FloatArray? = null

    /** True while the phone is believed to be still. */
    val still: Boolean get() = timer.holding

    val heldMillis: Long get() = timer.heldMillis

    /** One accelerometer sample. Returns the whole seconds of stillness this sample completed. */
    fun observe(x: Float, y: Float, z: Float, nowMillis: Long): Int {
        val previous = last
        last = floatArrayOf(x, y, z)
        val moved = if (previous == null) {
            0f
        } else {
            val dx = x - previous[0]
            val dy = y - previous[1]
            val dz = z - previous[2]
            sqrt(dx * dx + dy * dy + dz * dz)
        }
        return timer.observe(moved <= maxJitter, nowMillis)
    }
}
