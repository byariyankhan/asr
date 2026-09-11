package com.joinasr.app.earn

/**
 * Times a meditation on the camera: a person sitting upright in front of
 * the phone, facing it, and keeping still, for seven minutes in one go.
 *
 * What the camera can tell is that somebody sat there and did not move;
 * whether they meditated, nothing on a phone can tell. So the proof is
 * the sitting and the stillness, and the ask is that it be continuous:
 * a clock that ran for four minutes, stopped while the person got up,
 * and ran for three more has timed two sittings, not a meditation, and
 * starts over. That is the one way this differs from the plank, whose
 * breaks pause the clock; the [brokeOff] flag is how the screen hears
 * about it.
 *
 * A frame is in position ([SeatedPose.seated]) and still ([BodyMotion])
 * or it is not; the [HoldJudge] settles that over [settleMillis] so one
 * lost frame is not a break, and the [HoldTimer] behind it hands out the
 * seconds. A break is believed only after [breakMillis] out of position:
 * a scratch, a cough, a model that lost the hips for a moment, none of
 * those cost the minutes done. Getting up does, and so does a gap in the
 * frames that long (the app sent to the background, the camera closed).
 *
 * Not thread-safe: drive it from one thread, with a monotonic clock.
 */
class MeditationJudge(
    settleMillis: Long = 400L,
    maxFrameGapMillis: Long = 1_000L,
    private val breakMillis: Long = 3_000L,
    private val maxSeatDrift: Float = 0.6f,
    private val motion: BodyMotion = BodyMotion(),
) : PoseJudge {

    private val hold = HoldJudge(
        position = { pose -> SeatedPose.seated(pose) && !moving && !leftSeat },
        hasBody = SeatedPose::hasBody,
        settleMillis = settleMillis,
        maxFrameGapMillis = maxFrameGapMillis,
        coach = { _, _ -> "" to "" },
    )

    override val phase: PoseJudge.Phase get() = hold.phase

    /** True for the one frame on which a sitting that had counted was given up on: the count starts over. */
    override var brokeOff: Boolean = false
        private set

    /** Why the last frame was not in position, for the coaching line. */
    var reason: Reason = Reason.NO_BODY
        private set

    enum class Reason { NO_BODY, HIPS_UNSEEN, NOT_FACING, NOT_UPRIGHT, KNEES_UNSEEN, NOT_SEATED, LEFT_SEAT, MOVING, NONE }

    private var moving = false
    private var leftSeat = false
    private var counted = false
    private var outSince: Long? = null
    private var lastFrameAt: Long? = null

    /**
     * Where the hips were when the clock started, in picture units, and
     * the torso length then: the seat. A body more than [maxSeatDrift]
     * torso lengths from it has got up, however still it is standing,
     * and is out of position until the sitting starts over, when the
     * seat is wherever it sits next. The legs say whether a body is seated
     * ([SeatedPose.legsFolded]); the seat says whether it stayed so.
     */
    private var seat: Seat? = null

    private class Seat(val x: Float, val y: Float, val torso: Float)

    override fun observe(pose: BodyPose?, nowMillis: Long): Int {
        brokeOff = false
        moving = if (pose != null && SeatedPose.hasBody(pose)) motion.observe(pose, nowMillis) else motion.forget()
        leftSeat = pose != null && seat?.let { SeatedPose.hipsSeen(pose) && hipsAwayFrom(pose, it) } == true
        reason = when {
            pose == null || !SeatedPose.hasBody(pose) -> Reason.NO_BODY
            !SeatedPose.hipsSeen(pose) -> Reason.HIPS_UNSEEN
            !SeatedPose.facing(pose) -> Reason.NOT_FACING
            !SeatedPose.upright(pose) -> Reason.NOT_UPRIGHT
            !SeatedPose.kneesSeen(pose) -> Reason.KNEES_UNSEEN
            !SeatedPose.legsFolded(pose) -> Reason.NOT_SEATED
            leftSeat -> Reason.LEFT_SEAT
            moving -> Reason.MOVING
            else -> Reason.NONE
        }

        // The frames stopped for longer than a break: whatever was counted
        // was one sitting, and this is another.
        val last = lastFrameAt
        if (counted && last != null && nowMillis - last > breakMillis) startOver()
        lastFrameAt = nowMillis

        val earned = hold.observe(pose, nowMillis)
        if (earned > 0) counted = true

        if (hold.phase == PoseJudge.Phase.WORKING) {
            outSince = null
            // From a frame that is the position itself, not one the settled
            // phase is lagging behind: the first frame after a gap can be
            // a body the model has only half found again, and a seat with
            // no torso to measure by would never be left.
            if (seat == null && pose != null && SeatedPose.seated(pose) && !moving) seat = seatOf(pose)
        } else {
            val since = outSince ?: nowMillis.also { outSince = it }
            if (counted && nowMillis - since >= breakMillis) startOver()
        }
        return earned
    }

    private fun startOver() {
        brokeOff = true
        counted = false
        outSince = null
        seat = null
    }

    /** Only from a frame [SeatedPose.seated] passed, so the torso is a length. */
    private fun seatOf(pose: BodyPose) = Seat(
        x = (pose.leftHip.x + pose.rightHip.x) / 2f,
        y = (pose.leftHip.y + pose.rightHip.y) / 2f,
        torso = SeatedPose.torsoLength(pose),
    )

    private fun hipsAwayFrom(pose: BodyPose, seat: Seat): Boolean {
        if (seat.torso <= 0f) return false
        val dx = (pose.leftHip.x + pose.rightHip.x) / 2f - seat.x
        val dy = (pose.leftHip.y + pose.rightHip.y) / 2f - seat.y
        return kotlin.math.sqrt(dx * dx + dy * dy) / seat.torso > maxSeatDrift
    }

    override fun coaching(started: Boolean): Pair<String, String> {
        if (!started) return "Starting the camera" to ""
        return when (phase) {
            PoseJudge.Phase.NO_BODY ->
                "Looking for you" to "Stand the phone upright in front of you, a couple of steps away, with you in the picture from head to knees."
            PoseJudge.Phase.NOT_IN_POSITION -> when (reason) {
                Reason.HIPS_UNSEEN -> "Move back a little" to "The camera needs to see you down to the knees."
                Reason.NOT_FACING -> "Face the phone" to "Turn to look straight at the camera."
                Reason.NOT_UPRIGHT -> "Sit up" to "Back straight, shoulders over your hips."
                Reason.KNEES_UNSEEN -> "Show your knees" to "Move back, or tilt the phone, until your knees are in the picture."
                Reason.NOT_SEATED -> "Sit down" to "Cross-legged on the floor, or on a chair, facing the phone. Standing does not count."
                Reason.LEFT_SEAT -> "Sit back down" to "Where you were. A few seconds away starts the sitting over."
                Reason.MOVING -> "Settle" to "Keep still. The clock starts when you have."
                else -> "Sit still, facing the phone" to "Back straight, and keep still."
            }
            else -> "Sit still" to "The clock is running. Eyes can close; a tick marks each minute."
        }
    }
}

/**
 * Whether a body is moving, from where its head and shoulders were over
 * the last [windowMillis]. Distances are in torso lengths (shoulders to
 * hips), so a person near the phone and a person across the room are
 * held to the same rule, and the model's own jitter, a few pixels, is a
 * small fraction of a torso at any distance.
 *
 * The nose may drift by [maxNoseDrift] torso lengths in a window and the
 * point between the shoulders by [maxShoulderDrift] before the body is
 * called moving: a breath, a slow sway and a nod are under both; a head
 * turned to look at something, a shift on the cushion and getting up are
 * over one or the other.
 */
class BodyMotion(
    private val windowMillis: Long = 1_000L,
    private val maxNoseDrift: Float = 0.2f,
    private val maxShoulderDrift: Float = 0.12f,
) {
    private class Sample(val at: Long, val noseX: Float, val noseY: Float, val midX: Float, val midY: Float)

    private val recent = ArrayDeque<Sample>()

    /** One frame with a body in it. True when the body moved more than a meditation does. */
    fun observe(pose: BodyPose, nowMillis: Long): Boolean {
        val torso = SeatedPose.torsoLength(pose)
        val sample = Sample(
            at = nowMillis,
            noseX = pose.nose.x,
            noseY = pose.nose.y,
            midX = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f,
            midY = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f,
        )
        while (recent.isNotEmpty() && nowMillis - recent.first().at > windowMillis) recent.removeFirst()
        val moved = torso > 0f && recent.any { earlier ->
            val nose = distance(sample.noseX, sample.noseY, earlier.noseX, earlier.noseY) / torso
            val shoulders = distance(sample.midX, sample.midY, earlier.midX, earlier.midY) / torso
            nose > maxNoseDrift || shoulders > maxShoulderDrift
        }
        recent.addLast(sample)
        return moved
    }

    /** No body this frame: the trail is dropped, so where it was is not held against where it will be. */
    fun forget(): Boolean {
        recent.clear()
        return false
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/** The seated, facing, upright position the meditation judge times, as rules on a front-view pose. */
object SeatedPose {
    private const val MIN_VISIBILITY = 0.5f

    /** A face and both shoulders the model is sure of: somebody, facing roughly this way. */
    fun hasBody(pose: BodyPose): Boolean =
        pose.nose.visibility >= MIN_VISIBILITY &&
            pose.leftShoulder.visibility >= MIN_VISIBILITY &&
            pose.rightShoulder.visibility >= MIN_VISIBILITY

    /** Both hips seen: the picture reaches down far enough to tell sitting up from slumping. */
    fun hipsSeen(pose: BodyPose): Boolean =
        pose.leftHip.visibility >= MIN_VISIBILITY && pose.rightHip.visibility >= MIN_VISIBILITY

    /** Shoulders to hips, down the middle, in picture units; zero when the hips are not seen. */
    fun torsoLength(pose: BodyPose): Float {
        if (!hipsSeen(pose)) return 0f
        val sx = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f
        val sy = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        val hx = (pose.leftHip.x + pose.rightHip.x) / 2f
        val hy = (pose.leftHip.y + pose.rightHip.y) / 2f
        return PoseGeometry.distance(Landmark(sx, sy, 1f), Landmark(hx, hy, 1f))
    }

    /**
     * Facing the camera, near enough: the shoulders level (a body on its
     * side has them one above the other) and wide against the torso (a
     * body side-on has them nearly at one point).
     */
    fun facing(pose: BodyPose): Boolean {
        val torso = torsoLength(pose)
        if (torso <= 0f) return false
        if (PoseGeometry.tiltFromHorizontal(pose.leftShoulder, pose.rightShoulder) > 25f) return false
        return PoseGeometry.distance(pose.leftShoulder, pose.rightShoulder) / torso >= 0.35f
    }

    /**
     * Sitting up: the shoulders above the hips, the line between them
     * near vertical, and the torso not folded down towards the lens (a
     * body bent double at the waist has a torso a fraction of its
     * shoulder width in the picture, and looks "vertical" only because
     * there is so little of it). Slumped forward or lying back is not.
     */
    fun upright(pose: BodyPose): Boolean {
        val torso = torsoLength(pose)
        if (torso <= 0f) return false
        val sy = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        val hy = (pose.leftHip.y + pose.rightHip.y) / 2f
        if (sy >= hy) return false
        if (PoseGeometry.distance(pose.leftShoulder, pose.rightShoulder) / torso > 1.5f) return false
        val sx = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f
        val hx = (pose.leftHip.x + pose.rightHip.x) / 2f
        return PoseGeometry.tiltFromHorizontal(Landmark(sx, sy, 1f), Landmark(hx, hy, 1f)) >= 60f
    }

    /** Both knees seen: the picture reaches far enough down to say whether the body is sitting. */
    fun kneesSeen(pose: BodyPose): Boolean =
        pose.leftKnee.visibility >= MIN_VISIBILITY && pose.rightKnee.visibility >= MIN_VISIBILITY

    /**
     * The legs of a body that is sitting, seen from in front with the
     * phone at about the height of the body or lower: each thigh either
     * goes sideways (cross-legged on the floor: the knee out to the side,
     * the hip-to-knee line within [MAX_FOLDED_THIGH_TILT] of horizontal)
     * or comes towards the camera and looks short (on a chair: the knee
     * under the hip but the line no longer than [MAX_FORESHORTENED_THIGH]
     * of a torso). A body on its feet has a thigh that hangs straight down
     * and as long as the torso, and fails both; so does one kneeling up
     * or sitting back on its heels, whose thighs are long and near
     * vertical from the front, and which is asked to sit another way.
     * The ankles are not asked about: from a phone on the floor a chair
     * sitter's hip, knee and ankle line up as straight and as long as a
     * standing leg's, and a rule on them refused real sitters.
     *
     * The founder's ask is that the clock never runs for a body on its
     * feet, so the picture has to include the knees, and a phone high
     * enough above a chair to foreshorten nothing is the one placement
     * this refuses a real sitter; the copy says where the phone goes.
     */
    fun legsFolded(pose: BodyPose): Boolean {
        val torso = torsoLength(pose)
        if (torso <= 0f || !kneesSeen(pose)) return false
        return listOf(pose.leftHip to pose.leftKnee, pose.rightHip to pose.rightKnee).all { (hip, knee) ->
            PoseGeometry.tiltFromHorizontal(hip, knee) <= MAX_FOLDED_THIGH_TILT ||
                PoseGeometry.distance(hip, knee) / torso <= MAX_FORESHORTENED_THIGH
        }
    }

    private const val MAX_FOLDED_THIGH_TILT = 40f
    private const val MAX_FORESHORTENED_THIGH = 0.55f

    /**
     * The whole position: somebody facing the phone, sitting up, with
     * the legs of somebody sitting. A body standing in front of the phone,
     * however still, is not it; neither is one whose knees the camera
     * cannot see, which could be either.
     */
    fun seated(pose: BodyPose): Boolean =
        hasBody(pose) && hipsSeen(pose) && facing(pose) && upright(pose) && legsFolded(pose)
}
