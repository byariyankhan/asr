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

    /**
     * True for the one frame on which a run that had counted was given up
     * on, for an activity that has to be done in one go: the count starts
     * over. False for every judge whose breaks merely pause the clock.
     */
    val brokeOff: Boolean get() = false

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

    /** Any body at all: a shoulder and a hip the model is sure of on one side, or a face with both shoulders. */
    fun hasBody(pose: BodyPose): Boolean =
        listOf(pose.leftShoulder to pose.leftHip, pose.rightShoulder to pose.rightHip)
            .any { (s, h) -> s.visibility >= MIN_VISIBILITY && h.visibility >= MIN_VISIBILITY } ||
            facing(pose)

    /** A face and both shoulders the model is sure of: a body looking this way. */
    private fun facing(pose: BodyPose): Boolean =
        pose.leftEye.visibility >= MIN_VISIBILITY && pose.rightEye.visibility >= MIN_VISIBILITY &&
            pose.leftShoulder.visibility >= MIN_VISIBILITY && pose.rightShoulder.visibility >= MIN_VISIBILITY

    /**
     * A plank, from whichever way the phone is looking: head-on from the
     * floor ahead of the hands ([plankFront]), which is where a phone
     * stood upright goes, or from the side across the room ([plankSide]).
     */
    fun plank(pose: BodyPose): Boolean = plankFront(pose) || plankSide(pose)

    /**
     * A plank seen from in front: the phone upright on the floor a step
     * ahead of the hands, looking along the body. What that view can see
     * is the face, the shoulders, and the hips behind them, small; the
     * legs are behind the torso and are not asked about.
     *
     * The face is level with the shoulders or below them (in a plank the
     * head hangs in line with the spine; standing, kneeling up or sitting
     * puts the eyes well above the shoulder line), the shoulders are
     * level (facing the phone, not lying on one side), and the hips, when
     * the model has them, sit behind the shoulders: between them across
     * the picture and no lower than a little under the shoulder line. A
     * body on its feet or on a chair has its hips a torso below the
     * shoulders, which is the one thing this view sees plainly. And the
     * arms hold the body up: an elbow the model can see hangs at least
     * [MIN_ELBOW_DROP] of a shoulder width below its shoulder, which it
     * does on the hands (halfway to the floor) and on the forearms (on
     * it), and does not for a body lying face down with its head raised
     * to look at the phone, whose shoulders are a hand above the floor
     * and whose elbows, wherever the arms are, are barely below them.
     * All distances are in shoulder widths, so the phone can be a hand's
     * width away or a stride.
     */
    fun plankFront(pose: BodyPose): Boolean {
        if (!facing(pose)) return false
        val width = PoseGeometry.distance(pose.leftShoulder, pose.rightShoulder)
        if (width <= 0f) return false
        if (PoseGeometry.tiltFromHorizontal(pose.leftShoulder, pose.rightShoulder) > 30f) return false
        val shoulderX = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f
        val shoulderY = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        val eyesY = (pose.leftEye.y + pose.rightEye.y) / 2f
        // Picture y grows downwards: eyes above the shoulder line are smaller.
        if (eyesY < shoulderY - 0.3f * width) return false
        val arms = listOf(pose.leftShoulder to pose.leftElbow, pose.rightShoulder to pose.rightElbow)
            .filter { (_, elbow) -> elbow.visibility >= MIN_VISIBILITY }
        if (arms.isEmpty()) return false
        if (arms.none { (shoulder, elbow) -> elbow.y - shoulder.y >= MIN_ELBOW_DROP * width }) return false
        val hipsSeen = pose.leftHip.visibility >= MIN_VISIBILITY && pose.rightHip.visibility >= MIN_VISIBILITY
        if (hipsSeen) {
            val hipX = (pose.leftHip.x + pose.rightHip.x) / 2f
            val hipY = (pose.leftHip.y + pose.rightHip.y) / 2f
            if (abs(hipX - shoulderX) > 0.6f * width) return false
            if (hipY > shoulderY + 0.35f * width) return false
        }
        return true
    }

    private const val MIN_ELBOW_DROP = 0.45f

    /**
     * What the wall sit needs before it can say anything about the
     * position, by the way the body is facing. Head-on (both shoulders
     * seen, and wide against the torso), every one of the eight points
     * [wallSitFront] reads, so a knee or an ankle out of the picture on
     * one side is "Looking for you" with the shoulders-to-feet line and
     * not "Slide down the wall". Side-on, a whole side, shoulder to ankle.
     */
    fun wallSitBody(pose: BodyPose): Boolean =
        if (facingWide(pose)) WALL_SIT_POINTS.all { it(pose).visibility >= MIN_VISIBILITY } else BodySide.of(pose) != null

    private val WALL_SIT_POINTS: List<(BodyPose) -> Landmark> = listOf(
        { it.leftShoulder }, { it.rightShoulder }, { it.leftHip }, { it.rightHip },
        { it.leftKnee }, { it.rightKnee }, { it.leftAnkle }, { it.rightAnkle },
    )

    /** Both shoulders seen and at least a third of a torso apart: a body turned towards the phone rather than side-on. */
    private fun facingWide(pose: BodyPose): Boolean {
        if (pose.leftShoulder.visibility < MIN_VISIBILITY || pose.rightShoulder.visibility < MIN_VISIBILITY) return false
        val hip = listOf(pose.leftHip, pose.rightHip).filter { it.visibility >= MIN_VISIBILITY }.maxByOrNull { it.visibility }
            ?: return false
        val mid = Landmark((pose.leftShoulder.x + pose.rightShoulder.x) / 2f, (pose.leftShoulder.y + pose.rightShoulder.y) / 2f, 1f)
        val torso = PoseGeometry.distance(mid, hip)
        return torso > 0f && PoseGeometry.distance(pose.leftShoulder, pose.rightShoulder) / torso >= 0.35f
    }

    /**
     * A plank, from the side: the body a straight line from shoulder to
     * ankle, sloping down to the feet by more than lying flat and less
     * than sitting up. Sagging or piked hips are off the line; a person
     * sitting with their legs out has hips far below it; a person
     * standing has a line near vertical. Forearms or hands, either.
     */
    fun plankSide(pose: BodyPose): Boolean {
        val side = BodySide.of(pose) ?: return false
        val slope = PoseGeometry.tiltFromHorizontal(side.shoulder, side.ankle)
        if (slope < 6f || slope > 45f) return false
        // Shoulders above the feet, not the other way round.
        if (side.shoulder.y >= side.ankle.y) return false
        return PoseGeometry.offLine(side.hip, side.shoulder, side.ankle) <= 0.14f
    }

    /**
     * A wall sit, from whichever way the phone is looking: head-on from a
     * phone stood upright low in front ([wallSitFront]), or from the side
     * across the room ([wallSitSide]).
     */
    fun wallSit(pose: BodyPose): Boolean = wallSitFront(pose) || wallSitSide(pose)

    /**
     * A wall sit seen from in front: the phone upright on the floor or a
     * low stool a couple of steps ahead, looking at the person from about
     * knee height or lower. Eight points: both shoulders, hips, knees and
     * ankles, which from that distance a portrait frame holds.
     *
     * The thighs come towards the camera, so each knee sits close under
     * its hip across the picture (within half a torso) and, seen from
     * low down, level with the hip or above it: a knee nearer the lens
     * than the hip at the same height is drawn higher. The shins drop
     * from the knees to the feet, near vertical and at least half a torso
     * long. The back is upright and facing the phone. Standing puts the
     * knees a thigh below the hips; a half squat puts them below too; a
     * cross-legged sitter has the knees out to the sides and the ankles
     * up by them; a torso folded forward is short against the shoulders;
     * a chair passes, as it did from the side. A phone above
     * the hips looks down on the thighs and draws the knees below the
     * hips, which is why the copy says low.
     */
    fun wallSitFront(pose: BodyPose): Boolean {
        val points = listOf(
            pose.leftShoulder, pose.rightShoulder, pose.leftHip, pose.rightHip,
            pose.leftKnee, pose.rightKnee, pose.leftAnkle, pose.rightAnkle,
        )
        if (points.any { it.visibility < MIN_VISIBILITY }) return false
        val shoulderMid = Landmark((pose.leftShoulder.x + pose.rightShoulder.x) / 2f, (pose.leftShoulder.y + pose.rightShoulder.y) / 2f, 1f)
        val hipMid = Landmark((pose.leftHip.x + pose.rightHip.x) / 2f, (pose.leftHip.y + pose.rightHip.y) / 2f, 1f)
        val torso = PoseGeometry.distance(shoulderMid, hipMid)
        if (torso <= 0f) return false
        // Facing the phone, back upright: shoulders level, and between a
        // third of a torso and one and a half torsos apart. Under that is
        // a body side-on; over it is a torso folded down towards the lens,
        // which a slouch or a bend at the waist does and a wall sit does not.
        if (PoseGeometry.tiltFromHorizontal(pose.leftShoulder, pose.rightShoulder) > 30f) return false
        if (PoseGeometry.distance(pose.leftShoulder, pose.rightShoulder) / torso !in 0.35f..1.5f) return false
        if (shoulderMid.y >= hipMid.y || PoseGeometry.tiltFromHorizontal(shoulderMid, hipMid) < 60f) return false
        val legs = listOf(
            Triple(pose.leftHip, pose.leftKnee, pose.leftAnkle),
            Triple(pose.rightHip, pose.rightKnee, pose.rightAnkle),
        )
        return legs.all { (hip, knee, ankle) ->
            val kneeUnderHip = abs(knee.x - hip.x) / torso <= 0.5f && knee.y <= hip.y + 0.15f * torso
            val shinDown = ankle.y > knee.y &&
                PoseGeometry.distance(knee, ankle) / torso >= 0.45f &&
                PoseGeometry.tiltFromHorizontal(knee, ankle) >= 55f
            kneeUnderHip && shinDown
        }
    }

    /**
     * A wall sit, from the side: the back upright, the thigh level, the
     * knee near a right angle. A chair would pass too; the camera cannot
     * see what is behind the legs, and the mechanism is honesty.
     */
    fun wallSitSide(pose: BodyPose): Boolean {
        val side = BodySide.of(pose) ?: return false
        val back = PoseGeometry.tiltFromHorizontal(side.shoulder, side.hip)
        if (back < 55f) return false
        val thigh = PoseGeometry.tiltFromHorizontal(side.hip, side.knee)
        if (thigh > 30f) return false
        val knee = PoseGeometry.angleAt(side.knee, side.hip, side.ankle)
        return knee in 65f..125f
    }
}
