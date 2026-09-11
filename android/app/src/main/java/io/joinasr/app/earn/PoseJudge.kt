package io.joinasr.app.earn

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * What a camera activity asks of a stream of poses, one frame at a time:
 * the push-up counter, the plank timer, the wall-sit timer. The screen
 * that shows the camera drives whichever one the activity needs and asks
 * nothing else of it, so a new camera activity is a new judge and a line
 * in [cameraSpec].
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
interface PoseJudge {
    enum class Phase {
        /** No body the model is sure of, or not the parts that matter. */
        NO_BODY,

        /** A body, but not doing the thing: standing, sitting, lying. */
        NOT_IN_POSITION,

        /** In position and ready: the top of a push-up. */
        READY,

        /** Doing it: the bottom of a push-up, a plank being held. */
        WORKING,
    }

    val phase: Phase

    /**
     * One frame. Returns how many units this frame earned (one rep, one
     * whole second held, usually zero), so the caller can award them once
     * rather than compare counts.
     */
    fun observe(pose: BodyPose?, nowMillis: Long): Int

    /** The next thing to do, as a title and a line under it, given what the judge sees. */
    fun coaching(started: Boolean): Pair<String, String>
}

/** The push-up counter as a judge: its two views and four phases folded into the shared four. */
class PushUpJudge(private val counter: PushUpCounter = PushUpCounter()) : PoseJudge {
    override val phase: PoseJudge.Phase
        get() = when (counter.phase) {
            PushUpCounter.Phase.NO_BODY -> PoseJudge.Phase.NO_BODY
            PushUpCounter.Phase.NOT_IN_POSITION -> PoseJudge.Phase.NOT_IN_POSITION
            PushUpCounter.Phase.UP -> PoseJudge.Phase.READY
            PushUpCounter.Phase.DOWN -> PoseJudge.Phase.WORKING
        }

    override fun observe(pose: BodyPose?, nowMillis: Long): Int = if (counter.observe(pose, nowMillis)) 1 else 0

    override fun coaching(started: Boolean): Pair<String, String> {
        val view = counter.view
        val phase = counter.phase
        return when {
            !started -> "Starting the camera" to ""
            phase == PushUpCounter.Phase.NO_BODY ->
                "Looking for you" to "Phone on the floor just ahead of your hands, screen up."
            view == PushUpCounter.View.FRONT && phase == PushUpCounter.Phase.UP ->
                "Go down" to "Bring your chest down towards the phone."
            view == PushUpCounter.View.FRONT ->
                "Push up" to "All the way back up. That is one."
            phase == PushUpCounter.Phase.NOT_IN_POSITION ->
                "Get into a plank" to "Hands under your shoulders, body straight."
            phase == PushUpCounter.Phase.UP ->
                "Now go down" to "Bend your elbows until your chest is near the floor."
            else ->
                "Push up" to "Straighten your arms all the way. That is one."
        }
    }
}

/**
 * Times a position being held: a plank, a wall sit. [position] says
 * whether a pose is the position; a [HoldTimer] says how long it has
 * been, with the settling and the frame-gap rule described there.
 *
 * The clock runs while the position holds and stops while it does not;
 * seconds are handed out whole, as they complete, and the activity keeps
 * the total, so the phone can be put down and picked up again and the
 * count resumes. A break pauses the clock rather than resetting it: the
 * ask is 45 seconds in a plank, and somebody who managed 30, rested, and
 * did 15 more has done 45 seconds in a plank.
 */
class HoldJudge(
    private val position: (BodyPose) -> Boolean,
    private val hasBody: (BodyPose) -> Boolean,
    settleMillis: Long = 400L,
    maxFrameGapMillis: Long = 500L,
    private val coach: (PoseJudge.Phase, Boolean) -> Pair<String, String>,
) : PoseJudge {

    private val timer = HoldTimer(settleMillis, maxFrameGapMillis)

    override var phase: PoseJudge.Phase = PoseJudge.Phase.NO_BODY
        private set

    /** Milliseconds held so far, whole and part. */
    val heldMillis: Long get() = timer.heldMillis

    private var seenCandidate: PoseJudge.Phase? = null
    private var seenSince: Long = 0L
    private val settle = settleMillis

    override fun observe(pose: BodyPose?, nowMillis: Long): Int {
        val seen = when {
            pose == null || !hasBody(pose) -> PoseJudge.Phase.NO_BODY
            position(pose) -> PoseJudge.Phase.WORKING
            else -> PoseJudge.Phase.NOT_IN_POSITION
        }
        // The timer settles "holding or not"; the phase shown settles the
        // same way, so nobody and out-of-position are told apart on the
        // coaching line without flickering either.
        if (seen != phase) {
            if (seen != seenCandidate) {
                seenCandidate = seen
                seenSince = nowMillis
            }
            if (nowMillis - seenSince >= settle) {
                phase = seen
                seenCandidate = null
            }
        } else {
            seenCandidate = null
        }
        return timer.observe(seen == PoseJudge.Phase.WORKING, nowMillis)
    }

    override fun coaching(started: Boolean): Pair<String, String> =
        if (!started) "Starting the camera" to "" else coach(phase, true)
}

/** Angles and lines between landmarks, shared by every judge. */
internal object PoseGeometry {
    /** Degrees at [vertex] between the lines to [a] and [b]; 180 for a straight line. */
    fun angleAt(vertex: Landmark, a: Landmark, b: Landmark): Float {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y
        val magnitude = hypot(ax, ay) * hypot(bx, by)
        if (magnitude == 0f) return 180f
        val cosine = ((ax * bx + ay * by) / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    /** Degrees between the line from [a] to [b] and horizontal, 0..90, whichever way it slopes. */
    fun tiltFromHorizontal(a: Landmark, b: Landmark): Float {
        val dx = abs(b.x - a.x)
        val dy = abs(b.y - a.y)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    /** How far [point] lies off the line through [a] and [b], as a fraction of that line's length. */
    fun offLine(point: Landmark, a: Landmark, b: Landmark): Float {
        val length = hypot(b.x - a.x, b.y - a.y)
        if (length == 0f) return 0f
        val cross = abs((b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x))
        return cross / length / length
    }

    fun distance(a: Landmark, b: Landmark): Float = hypot(b.x - a.x, b.y - a.y)
}

/**
 * One side of the body, seen whole: shoulder, hip, knee and ankle the
 * model is sure of, on whichever side it is surer of. Null when neither
 * side is all there: a body with its legs out of the picture cannot be
 * told to be in a plank or in a wall sit, and is not said to be.
 */
internal class BodySide(val shoulder: Landmark, val hip: Landmark, val knee: Landmark, val ankle: Landmark) {
    val visibility get() = minOf(shoulder.visibility, hip.visibility, knee.visibility, ankle.visibility)

    companion object {
        fun of(pose: BodyPose, minVisibility: Float = 0.5f): BodySide? {
            val left = BodySide(pose.leftShoulder, pose.leftHip, pose.leftKnee, pose.leftAnkle)
            val right = BodySide(pose.rightShoulder, pose.rightHip, pose.rightKnee, pose.rightAnkle)
            val best = if (left.visibility >= right.visibility) left else right
            return best.takeIf { it.visibility >= minVisibility }
        }
    }
}

/** The positions the hold judges time, as rules on a [BodySide]. */
object HoldPositions {
    private const val MIN_VISIBILITY = 0.5f

    /** Any body at all: a shoulder and a hip the model is sure of, on one side. */
    fun hasBody(pose: BodyPose): Boolean =
        listOf(pose.leftShoulder to pose.leftHip, pose.rightShoulder to pose.rightHip)
            .any { (s, h) -> s.visibility >= MIN_VISIBILITY && h.visibility >= MIN_VISIBILITY }

    /**
     * A plank, from the side: the body a straight line from shoulder to
     * ankle, sloping down to the feet by more than lying flat and less
     * than sitting up. Sagging or piked hips are off the line; a person
     * sitting with their legs out has hips far below it; a person
     * standing has a line near vertical. Forearms or hands, either.
     */
    fun plank(pose: BodyPose): Boolean {
        val side = BodySide.of(pose) ?: return false
        val slope = PoseGeometry.tiltFromHorizontal(side.shoulder, side.ankle)
        if (slope < 6f || slope > 45f) return false
        // Shoulders above the feet, not the other way round.
        if (side.shoulder.y >= side.ankle.y) return false
        return PoseGeometry.offLine(side.hip, side.shoulder, side.ankle) <= 0.14f
    }

    /**
     * A wall sit, from the side: the back upright, the thigh level, the
     * knee near a right angle. A chair would pass too; the camera cannot
     * see what is behind the legs, and the mechanism is honesty.
     */
    fun wallSit(pose: BodyPose): Boolean {
        val side = BodySide.of(pose) ?: return false
        val back = PoseGeometry.tiltFromHorizontal(side.shoulder, side.hip)
        if (back < 55f) return false
        val thigh = PoseGeometry.tiltFromHorizontal(side.hip, side.knee)
        if (thigh > 30f) return false
        val knee = PoseGeometry.angleAt(side.knee, side.hip, side.ankle)
        return knee in 65f..125f
    }
}
