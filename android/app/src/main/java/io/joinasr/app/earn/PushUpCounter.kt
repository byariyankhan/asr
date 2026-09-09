package io.joinasr.app.earn

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * One body landmark from the pose model, with how sure the model is that it
 * can actually be seen (0..1). Coordinates are in image proportions with
 * the aspect ratio already applied, so that an angle measured between them
 * is the angle in the picture: see [PushUpPose.fromNormalised].
 */
data class Landmark(val x: Float, val y: Float, val visibility: Float)

/**
 * The joints a push-up is judged by. Both sides are carried because from a
 * side view the model reports the far arm through the torso at a low
 * visibility, and the counter picks whichever it can see better.
 */
data class PushUpPose(
    val leftShoulder: Landmark,
    val leftElbow: Landmark,
    val leftWrist: Landmark,
    val leftHip: Landmark,
    val rightShoulder: Landmark,
    val rightElbow: Landmark,
    val rightWrist: Landmark,
    val rightHip: Landmark,
) {
    companion object {
        // MediaPipe Pose Landmarker's fixed 33-point order. The eight used
        // here are the only ones this feature reads; the face, the hands
        // and the feet are discarded on the way in.
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LANDMARK_COUNT = 33

        /**
         * From the model's normalised output: x in 0..1 across the width,
         * y in 0..1 down the height. A 4:3 frame squashes one axis by a
         * third in that space, which turns a straight arm into a bent one,
         * so x is stretched back by [aspectRatio] (width over height)
         * before any angle is taken. Null when the model gave fewer points
         * than a whole body, which it does not do in practice, but a
         * counter that crashes on a short list is not a counter.
         */
        fun fromNormalised(points: List<Landmark>, aspectRatio: Float): PushUpPose? {
            if (points.size < LANDMARK_COUNT) return null
            fun at(index: Int) = points[index].let { it.copy(x = it.x * aspectRatio) }
            return PushUpPose(
                leftShoulder = at(LEFT_SHOULDER),
                leftElbow = at(LEFT_ELBOW),
                leftWrist = at(LEFT_WRIST),
                leftHip = at(LEFT_HIP),
                rightShoulder = at(RIGHT_SHOULDER),
                rightElbow = at(RIGHT_ELBOW),
                rightWrist = at(RIGHT_WRIST),
                rightHip = at(RIGHT_HIP),
            )
        }
    }
}

/**
 * Counts push-ups from a stream of poses, one frame at a time.
 *
 * The rule is the one every camera counter in the references uses, written
 * down so it can be tested without a camera: a push-up is the elbow going
 * from straight (above [upAboveDegrees]) to bent (below [downBelowDegrees])
 * and back to straight, while the body is in a plank. The gap between the
 * two thresholds is deliberate; an arm hovering at 120° flickers between
 * "up" and "down" on every frame without it.
 *
 * What it refuses, and why:
 *
 *  - Standing up and bending the arms. The shoulder-to-hip line has to be
 *    within [maxTorsoTiltDegrees] of horizontal, so the hips have to be in
 *    the picture and the body has to be down on the floor.
 *  - Half push-ups. Nothing counts until the elbow has been fully bent
 *    *and* fully straightened again, in that order, after a straight start.
 *  - Bouncing. Two reps closer together than [minRepMillis] are one rep;
 *    a real push-up takes longer than that.
 *  - A single confused frame. A phase has to hold for [settleFrames]
 *    frames in a row before it counts as the arm being there.
 *  - The far arm. The arm the model is less sure of is ignored; from a side
 *    view the far one is guessed through the torso.
 *
 * It does not, and cannot, stop somebody else doing the push-ups in front
 * of the phone. Neither does the step counter stop somebody handing their
 * phone to a friend for a walk. The mechanism is friction and honesty, not
 * surveillance, and the reward is ten minutes of Instagram.
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class PushUpCounter(
    private val downBelowDegrees: Float = 95f,
    private val upAboveDegrees: Float = 150f,
    private val minVisibility: Float = 0.5f,
    private val maxTorsoTiltDegrees: Float = 45f,
    private val minRepMillis: Long = 600L,
    private val settleFrames: Int = 2,
) {
    enum class Phase {
        /** No body the model is sure of, or not the parts that matter. */
        NO_BODY,

        /** A body, but standing, kneeling or sitting rather than in a plank. */
        NOT_IN_POSITION,

        /** Arms straight. */
        UP,

        /** Arms bent. */
        DOWN,
    }

    var reps: Int = 0
        private set

    var phase: Phase = Phase.NO_BODY
        private set

    /** True once the arms have been straight in a plank: the start of a rep. */
    private var armed = false
    private var lastRepAt: Long? = null
    private var candidate: Phase? = null
    private var candidateFrames = 0

    /**
     * One frame. Returns true when this frame finished a push-up, so the
     * caller can award it once rather than compare counts.
     */
    fun observe(pose: PushUpPose?, nowMillis: Long): Boolean {
        val arm = pose?.let(::bestArm)
        val torso = pose?.let(::torsoTilt)
        if (arm == null || torso == null) return settle(Phase.NO_BODY, nowMillis)
        if (torso > maxTorsoTiltDegrees) return settle(Phase.NOT_IN_POSITION, nowMillis)
        val angle = angleAt(arm.elbow, arm.shoulder, arm.wrist)
        return when {
            angle >= upAboveDegrees -> settle(Phase.UP, nowMillis)
            angle <= downBelowDegrees -> settle(Phase.DOWN, nowMillis)
            // Between the thresholds: the arm is on its way somewhere. The
            // last settled phase stands until it arrives.
            else -> {
                candidate = null
                candidateFrames = 0
                false
            }
        }
    }

    private fun settle(seen: Phase, nowMillis: Long): Boolean {
        if (seen == candidate) candidateFrames++ else {
            candidate = seen
            candidateFrames = 1
        }
        if (candidateFrames < settleFrames) return false
        if (seen == phase) return false
        val before = phase
        phase = seen
        return when (seen) {
            Phase.NO_BODY, Phase.NOT_IN_POSITION -> {
                // A rep in flight is lost. Coming back means starting from
                // straight arms again, which is what a person does anyway.
                armed = false
                false
            }
            Phase.DOWN -> false
            Phase.UP -> {
                val last = lastRepAt
                val spaced = last == null || nowMillis - last >= minRepMillis
                val counted = before == Phase.DOWN && armed && spaced
                armed = true
                if (counted) {
                    reps++
                    lastRepAt = nowMillis
                }
                counted
            }
        }
    }

    private class Arm(val shoulder: Landmark, val elbow: Landmark, val wrist: Landmark) {
        val visibility get() = minOf(shoulder.visibility, elbow.visibility, wrist.visibility)
    }

    /** The arm the model can see best, or null if it cannot see one whole. */
    private fun bestArm(pose: PushUpPose): Arm? {
        val left = Arm(pose.leftShoulder, pose.leftElbow, pose.leftWrist)
        val right = Arm(pose.rightShoulder, pose.rightElbow, pose.rightWrist)
        val best = if (left.visibility >= right.visibility) left else right
        return best.takeIf { it.visibility >= minVisibility }
    }

    /**
     * Degrees between the shoulder-to-hip line and horizontal, 0..90, on
     * whichever side has a visible shoulder and hip. Null with neither: a
     * body with no hips in the picture cannot be told from a person
     * standing at the edge of the frame.
     */
    private fun torsoTilt(pose: PushUpPose): Float? {
        val sides = listOf(pose.leftShoulder to pose.leftHip, pose.rightShoulder to pose.rightHip)
        val (shoulder, hip) = sides
            .filter { (s, h) -> s.visibility >= minVisibility && h.visibility >= minVisibility }
            .maxByOrNull { (s, h) -> minOf(s.visibility, h.visibility) }
            ?: return null
        val dx = abs(hip.x - shoulder.x)
        val dy = abs(hip.y - shoulder.y)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun angleAt(vertex: Landmark, a: Landmark, b: Landmark): Float {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y
        val magnitude = hypot(ax, ay) * hypot(bx, by)
        if (magnitude == 0f) return 180f
        val cosine = ((ax * bx + ay * by) / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }
}
