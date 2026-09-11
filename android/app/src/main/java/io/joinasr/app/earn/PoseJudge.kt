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
    maxFrameGapMillis: Long = 1_000L,
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

    /**
     * How far [point] lies below the line through [a] and [b] in the
     * picture (positive: further down the picture than the line at that
     * x; negative: above it), as a fraction of that line's length. On a
     * vertical line there is no below, and the distance comes back with
     * whatever sign it has.
     */
    fun belowLine(point: Landmark, a: Landmark, b: Landmark): Float {
        val length = hypot(b.x - a.x, b.y - a.y)
        if (length == 0f) return 0f
        val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
        val towardsB = if (b.x >= a.x) 1f else -1f
        return cross / length / length * towardsB
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

    /**
     * What the plank needs before it can say anything: one whole side of
     * the body, shoulder, hip, knee and ankle, that the model is sure of
     * ([BodySide]). A phone stood close in front of the face sees a head,
     * shoulders and arms and nothing below, and the model, trained on
     * whole bodies, fills the rest in from habit: hips a torso under the
     * shoulders as if the body were upright, and sure of them; knees and
     * ankles it marks unseen. A judge fed that guessed body timed the
     * founder sitting on his bed leaning over the phone as a plank and
     * refused his real one, and no rule on a head and shoulders can tell
     * those two apart, because the picture is the same. So that view is
     * not judged at all: "Looking for your whole body" until the phone is
     * far enough off to see it.
     */
    fun plankBody(pose: BodyPose): Boolean = BodySide.of(pose) != null

    /**
     * A plank: the body one straight line from shoulder to ankle, held up
     * off the floor, seen by a phone stood upright on the floor a couple
     * of steps away, from the side or from ahead and off to one side,
     * with the whole body in the picture.
     *
     * A straight line is a straight line from wherever the camera looks,
     * so the rule that carries the weight is that the hip lies on the
     * line from the shoulder to the ankle: no more than [MAX_HIP_SAG] of
     * the line's length below it (a sagging plank; or a sphinx or cobra,
     * chest propped up and hips on the floor) and no more than
     * [MAX_HIP_PIKE] above it (hips lifted a little is a beginner's plank;
     * on all fours, in child's pose or a downward dog they are far above).
     * In photographs of planks from the side and from ahead at an angle
     * the model puts the hip within 0.07 of the line, most within 0.04; in
     * photographs of a sphinx or cobra never nearer than 0.06 below it.
     * The knee stays near the line too, no more than [MAX_KNEE_DROP]
     * below it: on all fours the knees are under the hips, a quarter of
     * the line's length down.
     *
     * Then, with the phone on the floor, the shoulders are above the feet
     * in the picture and the line slopes down to them by [MIN_SLOPE] at
     * least: a body lying flat has its shoulders a hand off the floor and
     * comes out under that from the side and only a little over from an
     * angle. And by [MAX_SLOPE] at most: standing, kneeling up and sitting
     * with the legs folded are near vertical. A view from ahead and to one
     * side steepens a plank's line, which is why the limit is 50 and not
     * 45. Last, the arms hold it up: an elbow the model can see hangs at
     * least [MIN_ELBOW_DROP] of the torso's length in the picture below
     * its shoulder; on the hands the elbows are halfway to the floor, on
     * the forearms on it, and lying flat they are level with the
     * shoulders. With no elbow seen the slope decides on its own.
     */
    fun plank(pose: BodyPose): Boolean {
        val side = BodySide.of(pose) ?: return false
        // Picture y grows downwards: the shoulders above the feet are the smaller y.
        if (side.shoulder.y >= side.ankle.y) return false
        val slope = PoseGeometry.tiltFromHorizontal(side.shoulder, side.ankle)
        if (slope < MIN_SLOPE || slope > MAX_SLOPE) return false
        val sag = PoseGeometry.belowLine(side.hip, side.shoulder, side.ankle)
        if (sag > MAX_HIP_SAG || sag < -MAX_HIP_PIKE) return false
        if (PoseGeometry.belowLine(side.knee, side.shoulder, side.ankle) > MAX_KNEE_DROP) return false
        val torso = PoseGeometry.distance(side.shoulder, side.hip)
        if (torso <= 0f) return false
        // An arm the model has whole, shoulder and elbow: an elbow hung off a
        // shoulder it only guessed at measures the guess, not the arm.
        val elbowDrop = listOf(pose.leftShoulder to pose.leftElbow, pose.rightShoulder to pose.rightElbow)
            .filter { (shoulder, elbow) -> shoulder.visibility >= MIN_VISIBILITY && elbow.visibility >= MIN_VISIBILITY }
            .maxOfOrNull { (shoulder, elbow) -> (elbow.y - shoulder.y) / torso }
        return elbowDrop == null || elbowDrop >= MIN_ELBOW_DROP
    }

    private const val MAX_HIP_SAG = 0.05f
    private const val MAX_HIP_PIKE = 0.15f
    private const val MAX_KNEE_DROP = 0.12f
    private const val MIN_SLOPE = 8f
    private const val MAX_SLOPE = 50f
    private const val MIN_ELBOW_DROP = 0.25f

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
