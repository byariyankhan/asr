package io.joinasr.app.earn

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
    maxFrameGapMillis: Long = 500L,
    private val breakMillis: Long = 3_000L,
    private val motion: BodyMotion = BodyMotion(),
) : PoseJudge {

    private val hold = HoldJudge(
        position = { pose -> SeatedPose.seated(pose) && !moving },
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

    enum class Reason { NO_BODY, HIPS_UNSEEN, NOT_FACING, NOT_UPRIGHT, STANDING, MOVING, NONE }

    private var moving = false
    private var counted = false
    private var outSince: Long? = null
    private var lastFrameAt: Long? = null

    override fun observe(pose: BodyPose?, nowMillis: Long): Int {
        brokeOff = false
        moving = if (pose != null && SeatedPose.hasBody(pose)) motion.observe(pose, nowMillis) else motion.forget()
        reason = when {
            pose == null || !SeatedPose.hasBody(pose) -> Reason.NO_BODY
            !SeatedPose.hipsSeen(pose) -> Reason.HIPS_UNSEEN
            !SeatedPose.facing(pose) -> Reason.NOT_FACING
            !SeatedPose.upright(pose) -> Reason.NOT_UPRIGHT
            SeatedPose.standing(pose) -> Reason.STANDING
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
    }

    override fun coaching(started: Boolean): Pair<String, String> {
        if (!started) return "Starting the camera" to ""
        return when (phase) {
            PoseJudge.Phase.NO_BODY ->
                "Looking for you" to "Prop the phone up in front of you, a few steps away, with your head, shoulders and hips in the picture."
            PoseJudge.Phase.NOT_IN_POSITION -> when (reason) {
                Reason.HIPS_UNSEEN -> "Move back a little" to "The camera needs to see you down to the hips."
                Reason.NOT_FACING -> "Face the phone" to "Turn to look straight at the camera."
                Reason.NOT_UPRIGHT -> "Sit up" to "Back straight, shoulders over your hips."
                Reason.STANDING -> "Sit down" to "On the floor or a chair, facing the phone."
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

    /** Sitting up: the shoulders above the hips, the line between them near vertical. Slumped forward or lying back is not. */
    fun upright(pose: BodyPose): Boolean {
        if (!hipsSeen(pose)) return false
        val sy = (pose.leftShoulder.y + pose.rightShoulder.y) / 2f
        val hy = (pose.leftHip.y + pose.rightHip.y) / 2f
        if (sy >= hy) return false
        val sx = (pose.leftShoulder.x + pose.rightShoulder.x) / 2f
        val hx = (pose.leftHip.x + pose.rightHip.x) / 2f
        return PoseGeometry.tiltFromHorizontal(Landmark(sx, sy, 1f), Landmark(hx, hy, 1f)) >= 60f
    }

    /**
     * On the feet: a thigh the model can see hanging straight down from
     * the hip and as long as it is when it is not foreshortened. Sitting
     * cross-legged, the thighs go sideways; on a chair facing the camera,
     * they come towards it and look short. Legs out of the picture say
     * nothing either way, and a person who has propped the phone so it
     * sees only their top half is taken at their word about the rest.
     */
    fun standing(pose: BodyPose): Boolean {
        val torso = torsoLength(pose)
        if (torso <= 0f) return false
        return listOf(pose.leftHip to pose.leftKnee, pose.rightHip to pose.rightKnee).any { (hip, knee) ->
            knee.visibility >= MIN_VISIBILITY &&
                knee.y > hip.y &&
                PoseGeometry.tiltFromHorizontal(hip, knee) >= 60f &&
                PoseGeometry.distance(hip, knee) / torso >= 0.6f
        }
    }

    /** The whole position: somebody facing the phone, sitting up, not on their feet. */
    fun seated(pose: BodyPose): Boolean =
        hasBody(pose) && hipsSeen(pose) && facing(pose) && upright(pose) && !standing(pose)
}
