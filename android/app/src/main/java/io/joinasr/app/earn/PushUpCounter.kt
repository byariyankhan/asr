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
 * The points a push-up is judged by. Both sides are carried because from a
 * side view the model reports the far arm through the torso at a low
 * visibility, and the counter picks whichever it can see better. The eyes
 * and the mouth are for the other view, the phone on the floor looking
 * up: how big the face is in the picture is how close it is, and a face
 * is measured two ways (across the eyes, and eyes to mouth) because a
 * head turned to the side shrinks one and a head bowed to the floor
 * shrinks the other, never both.
 */
data class PushUpPose(
    val nose: Landmark,
    val leftEye: Landmark,
    val rightEye: Landmark,
    val mouthLeft: Landmark,
    val mouthRight: Landmark,
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
        const val NOSE = 0
        const val LEFT_EYE = 2
        const val RIGHT_EYE = 5
        const val MOUTH_LEFT = 9
        const val MOUTH_RIGHT = 10
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
                nose = at(NOSE),
                leftEye = at(LEFT_EYE),
                rightEye = at(RIGHT_EYE),
                mouthLeft = at(MOUTH_LEFT),
                mouthRight = at(MOUTH_RIGHT),
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
 * Two views, chosen frame by frame from what the model can see, written
 * down so both can be tested without a camera.
 *
 * **From the side** (the phone propped up across the room): a push-up is
 * the elbow going from straight (above [upAboveDegrees]) to bent (below
 * [downBelowDegrees]) and back to straight, while the body is in a plank.
 * The gap between the two thresholds is deliberate; an arm hovering at
 * 120° flickers between "up" and "down" on every frame without it.
 *
 * **From the floor** (the phone flat, screen up, just ahead of the hands,
 * which is where people actually put it): the face comes down towards the
 * lens and goes back up. Distance is read from the size of the face in
 * the picture, against the smallest it has been while up: down is
 * [frontDownRatio] times closer, up is back within [frontUpRatio]. The
 * baseline is whatever "up" the person has, so the phone can be anywhere
 * on the floor and a set started from the bottom simply begins on the
 * next rep. Nobody has to look at the phone: the face is measured across
 * the eyes and from eyes to mouth and the larger is taken, so a head
 * bowed to the floor or turned aside changes the reading little.
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
    private val frontDownRatio: Float = 1.35f,
    private val frontUpRatio: Float = 1.12f,
    /** Nobody holds the bottom of a push-up this long: the phone moved. */
    private val frontHeldDownMillis: Long = 4_000L,
) {
    enum class View {
        /** Nothing the model is sure of. */
        NONE,

        /** Shoulders, an arm and a hip in view: elbow angles. */
        SIDE,

        /** A face looking down at the lens: distance. */
        FRONT,
    }

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

    var view: View = View.NONE
        private set

    /** True once the arms have been straight in a plank: the start of a rep. */
    private var armed = false
    private var topScale: Float? = null
    private var smoothedScale: Float? = null
    private var downSince: Long? = null
    private var candidateView: View? = null
    private var candidateViewFrames = 0
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
        // A body in a plank seen from the side is the side view. Failing
        // that, a face is the floor view: from the floor the model often
        // guesses an arm and a hip out of the foreshortened body, standing
        // upright, and judged as a side view they would read as "not in a
        // plank". A body with no face and no plank is somebody standing.
        val seen = when {
            arm != null && torso != null && torso <= maxTorsoTiltDegrees -> View.SIDE
            pose != null && faceSize(pose) != null -> View.FRONT
            arm != null && torso != null -> View.SIDE
            else -> View.NONE
        }
        if (seen != view) {
            // Believed only once it holds, like a phase: one frame in which
            // the model lost the hips is not a change of view, and a rep
            // in flight survives it.
            if (seen == candidateView) candidateViewFrames++ else {
                candidateView = seen
                candidateViewFrames = 1
            }
            if (candidateViewFrames < settleFrames) return false
            // A different way of looking is a different ruler: a rep in
            // flight is lost, and the front view's baseline starts over.
            // The phase starts over too, so the new view's first "up" is a
            // transition that arms the counter rather than a repeat of the
            // old view's "up" that settle() would ignore.
            view = seen
            phase = Phase.NO_BODY
            armed = false
            topScale = null
            smoothedScale = null
            downSince = null
            candidate = null
            candidateFrames = 0
        }
        candidateView = null
        candidateViewFrames = 0
        return when (seen) {
            View.NONE -> settle(Phase.NO_BODY, nowMillis)
            View.SIDE -> side(arm!!, torso!!, nowMillis)
            View.FRONT -> front(pose!!, nowMillis)
        }
    }

    private fun side(arm: Arm, torso: Float, nowMillis: Long): Boolean {
        if (torso > maxTorsoTiltDegrees) return settle(Phase.NOT_IN_POSITION, nowMillis)
        val angle = angleAt(arm.elbow, arm.shoulder, arm.wrist)
        return when {
            angle >= upAboveDegrees -> settle(Phase.UP, nowMillis)
            angle <= downBelowDegrees -> settle(Phase.DOWN, nowMillis)
            else -> between()
        }
    }

    private fun front(pose: PushUpPose, nowMillis: Long): Boolean {
        val raw = faceSize(pose) ?: return settle(Phase.NO_BODY, nowMillis)
        // Light smoothing: the model's points shiver by a percent or two
        // frame to frame, and a threshold should not; heavier than this
        // and the count lags a fast set by half a rep.
        val scale = smoothedScale?.let { it * 0.3f + raw * 0.7f } ?: raw
        smoothedScale = scale
        // The farthest the face has been while up is "up". It only ever
        // moves further away, so a set begun from the floor finds its top
        // on the first rise and counts from the next rep.
        val top = topScale?.let { if (phase != Phase.DOWN) minOf(it, scale) else it } ?: scale
        topScale = top
        val ratio = scale / top
        return when {
            ratio >= frontDownRatio -> {
                val since = downSince ?: nowMillis.also { downSince = it }
                if (nowMillis - since >= frontHeldDownMillis) {
                    // Held "down" far longer than a push-up takes: the phone
                    // was moved closer. Where the face is now is the new up,
                    // and nothing is owed for getting there.
                    topScale = scale
                    downSince = null
                    armed = false
                    settle(Phase.UP, nowMillis)
                } else {
                    settle(Phase.DOWN, nowMillis)
                }
            }
            ratio <= frontUpRatio -> {
                downSince = null
                settle(Phase.UP, nowMillis)
            }
            else -> between()
        }
    }

    /** Between the thresholds: on the way somewhere. The last settled phase stands. */
    private fun between(): Boolean {
        candidate = null
        candidateFrames = 0
        return false
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

    /**
     * How big the face is in the picture, or null with no face the model
     * is sure of: both eyes are needed. The larger of two measures, across
     * the eyes and from the eyes to the mouth, because turning the head
     * shrinks the first and bowing it shrinks the second, and a person
     * doing push-ups looks at the floor, at the wall, anywhere but the
     * phone. Whichever way the phone lies, both are plain distances.
     */
    private fun faceSize(pose: PushUpPose): Float? {
        if (pose.leftEye.visibility < minVisibility || pose.rightEye.visibility < minVisibility) return null
        val eyes = hypot(pose.leftEye.x - pose.rightEye.x, pose.leftEye.y - pose.rightEye.y)
        val eyesToMouth = if (
            pose.mouthLeft.visibility >= minVisibility && pose.mouthRight.visibility >= minVisibility
        ) {
            val eyeX = (pose.leftEye.x + pose.rightEye.x) / 2f
            val eyeY = (pose.leftEye.y + pose.rightEye.y) / 2f
            val mouthX = (pose.mouthLeft.x + pose.mouthRight.x) / 2f
            val mouthY = (pose.mouthLeft.y + pose.mouthRight.y) / 2f
            hypot(eyeX - mouthX, eyeY - mouthY)
        } else {
            0f
        }
        return maxOf(eyes, eyesToMouth).takeIf { it > 0f }
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
