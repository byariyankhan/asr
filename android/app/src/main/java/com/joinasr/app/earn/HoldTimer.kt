package com.joinasr.app.earn

/**
 * The clock behind every timed activity that is judged frame by frame: a
 * plank, a wall sit, a phone lying still. It is told, once per frame,
 * whether the thing is being done, and hands out whole seconds as they
 * complete.
 *
 * A change of state is believed only once it has held for [settleMillis],
 * so one frame of "not" in the middle of a hold is not a break. The
 * settling time before "holding" is believed is not counted; the time
 * before a break is believed is, which is the smaller error and the
 * kinder one.
 *
 * Time is counted frame to frame, and only between frames that are close
 * together: a gap longer than [maxFrameGapMillis] is the source having
 * stopped (the app sent to the background, the screen locked) and is
 * worth nothing, however long it was. A second is the line: a slow phone
 * running the pose model at two or three frames a second is still a
 * source, and a clock that dropped its gaps would run slow for exactly
 * the people whose phones are slow; a screen locked is seconds away.
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class HoldTimer(
    private val settleMillis: Long = 400L,
    private val maxFrameGapMillis: Long = 1_000L,
) {
    /** Whether the hold is believed to be on, as of the last frame. */
    var holding: Boolean = false
        private set

    /** Milliseconds held so far, whole and part. */
    var heldMillis: Long = 0L
        private set

    private var lastFrameAt: Long? = null
    private var candidate: Boolean? = null
    private var candidateSince: Long = 0L
    private var secondsReported = 0L

    /** One frame: is the thing being done right now? Returns the whole seconds this frame completed. */
    fun observe(doing: Boolean, nowMillis: Long): Int {
        if (doing != holding) {
            if (doing != candidate) {
                candidate = doing
                candidateSince = nowMillis
            }
            if (nowMillis - candidateSince >= settleMillis) {
                holding = doing
                candidate = null
                // Entering the hold: the clock starts now, not from the
                // frames spent deciding. Leaving it: they counted.
                if (doing) lastFrameAt = nowMillis
            }
        } else {
            candidate = null
        }
        if (holding) {
            val last = lastFrameAt
            val gap = if (last != null) nowMillis - last else 0L
            if (gap in 1..maxFrameGapMillis) heldMillis += gap
            lastFrameAt = nowMillis
        } else {
            lastFrameAt = null
        }
        val whole = heldMillis / 1_000L
        val earned = (whole - secondsReported).toInt()
        secondsReported = whole
        return earned
    }
}
