package io.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationJudgeTest {

    /**
     * A body seen from the front, sitting cross-legged and facing the
     * phone: shoulders 0.16 apart, a torso 0.25 long, knees out to the
     * sides. Shifted by [dx], [dy] to move the whole of it, or just the
     * head by [headDx].
     */
    private fun seated(
        dx: Float = 0f,
        dy: Float = 0f,
        headDx: Float = 0f,
        visibility: Float = 0.95f,
        hipVisibility: Float = visibility,
    ): BodyPose {
        fun at(x: Float, y: Float, v: Float = visibility) = Landmark(x + dx, y + dy, v)
        val ghost = Landmark(0.5f, 0.5f, 0.2f)
        return BodyPose(
            nose = at(0.5f + headDx, 0.30f), leftEye = at(0.52f + headDx, 0.28f), rightEye = at(0.48f + headDx, 0.28f),
            mouthLeft = at(0.51f + headDx, 0.32f), mouthRight = at(0.49f + headDx, 0.32f),
            leftShoulder = at(0.58f, 0.40f), leftElbow = ghost, leftWrist = ghost, leftHip = at(0.55f, 0.65f, hipVisibility),
            rightShoulder = at(0.42f, 0.40f), rightElbow = ghost, rightWrist = ghost, rightHip = at(0.45f, 0.65f, hipVisibility),
            leftKnee = at(0.72f, 0.70f), rightKnee = at(0.28f, 0.70f), leftAnkle = ghost, rightAnkle = ghost,
        )
    }

    private val sitting = seated()

    /** The same body stood up on the spot: everything a torso and a bit higher in the picture, and then still. */
    private val stood = seated(dy = -0.3f)

    /** On a chair, facing the phone: the thighs come towards the camera and look short. */
    private val onAChair = sitting.copy(
        leftKnee = Landmark(0.57f, 0.74f, 0.95f),
        rightKnee = Landmark(0.43f, 0.74f, 0.95f),
    )

    /** Slumped forward: the shoulders have come down and out ahead of the hips. */
    private val slumped = sitting.copy(
        leftShoulder = Landmark(0.70f, 0.58f, 0.95f),
        rightShoulder = Landmark(0.54f, 0.58f, 0.95f),
        nose = Landmark(0.66f, 0.50f, 0.95f),
    )

    /** Side-on to the phone: the shoulders nearly at one point. */
    private val sideOn = sitting.copy(
        leftShoulder = Landmark(0.51f, 0.40f, 0.95f),
        rightShoulder = Landmark(0.49f, 0.40f, 0.95f),
        leftHip = Landmark(0.51f, 0.65f, 0.95f),
        rightHip = Landmark(0.49f, 0.65f, 0.95f),
    )

    /** Lying on one side: the shoulders one above the other. */
    private val onOneSide = sitting.copy(
        leftShoulder = Landmark(0.50f, 0.30f, 0.95f),
        rightShoulder = Landmark(0.50f, 0.46f, 0.95f),
        leftHip = Landmark(0.75f, 0.32f, 0.95f),
        rightHip = Landmark(0.75f, 0.44f, 0.95f),
    )

    @Test fun `cross-legged and facing the phone is the position, on a chair too`() {
        assertTrue(SeatedPose.seated(sitting))
        assertTrue(SeatedPose.seated(onAChair))
    }

    @Test fun `slumping, side-on and lying down are not`() {
        assertFalse(SeatedPose.upright(slumped))
        assertFalse(SeatedPose.facing(sideOn))
        assertFalse(SeatedPose.facing(onOneSide))
    }

    @Test fun `hips out of the picture is a body but not the position`() {
        val cropped = seated(hipVisibility = 0.2f)
        assertTrue(SeatedPose.hasBody(cropped))
        assertFalse(SeatedPose.hipsSeen(cropped))
        assertFalse(SeatedPose.seated(cropped))
    }

    @Test fun `legs out of the picture are not held against a sitter`() {
        val topHalf = sitting.copy(leftKnee = BodyPose.UNSEEN, rightKnee = BodyPose.UNSEEN)
        assertTrue(SeatedPose.seated(topHalf))
    }

    /** Frames every 100 ms from [from] for [millis], all of [pose]; the seconds they earned. */
    private fun MeditationJudge.frames(pose: BodyPose?, from: Long, millis: Long): Int =
        (0..millis step 100).sumOf { observe(pose, from + it) }

    /** The same, with the model's own jitter on every point: a pixel or two, never the same twice. */
    private fun MeditationJudge.jittery(from: Long, millis: Long): Int =
        (0..millis step 100).sumOf { t ->
            val wobble = 0.004f * ((t / 100) % 3 - 1)
            observe(seated(dx = wobble, dy = -wobble), from + t)
        }

    @Test fun `sitting still runs the clock, after the settling`() {
        val judge = MeditationJudge()
        assertEquals(0, judge.frames(sitting, 0, 300))
        assertEquals(PoseJudge.Phase.NO_BODY, judge.phase)
        // Believed at 400 ms; the first whole second is complete at 1,400.
        judge.frames(sitting, 400, 900)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        assertEquals(1, judge.observe(sitting, 1_400))
        assertEquals(10, judge.frames(sitting, 1_500, 10_000))
        assertFalse(judge.brokeOff)
    }

    @Test fun `the model's jitter and a breath are not movement`() {
        val judge = MeditationJudge()
        assertEquals(10, judge.jittery(0, 10_400))
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
    }

    @Test fun `turning the head or shifting on the cushion stops the clock and settling starts it again`() {
        val judge = MeditationJudge()
        judge.frames(sitting, 0, 5_400)
        // The head goes 0.08 of the picture sideways in 100 ms: a third of a torso.
        assertEquals(0, judge.observe(seated(headDx = 0.08f), 5_500))
        judge.frames(seated(headDx = 0.08f), 5_600, 500)
        assertEquals(PoseJudge.Phase.NOT_IN_POSITION, judge.phase)
        assertEquals(MeditationJudge.Reason.MOVING, judge.reason)
        // Still again, in the new place, within the three seconds allowed: no restart.
        val earned = judge.frames(seated(headDx = 0.08f), 6_200, 2_000)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        assertTrue(earned >= 1)
        assertFalse(judge.brokeOff)
    }

    @Test fun `getting up for longer than a moment starts the count over, however still the standing is`() {
        val judge = MeditationJudge()
        assertEquals(20, judge.frames(sitting, 0, 20_400))
        // Up: a second of movement, then a still body whose hips are a torso and more from the seat.
        for (t in 20_500L..23_800L step 100) {
            judge.observe(stood, t)
            assertFalse(judge.brokeOff)
        }
        assertEquals(PoseJudge.Phase.NOT_IN_POSITION, judge.phase)
        assertEquals(MeditationJudge.Reason.LEFT_SEAT, judge.reason)
        assertEquals("Sit back down", judge.coaching(true).first)
        // Three seconds out of position: over.
        judge.observe(stood, 23_900)
        assertTrue(judge.brokeOff)
        // Still standing there, still: a fresh sitting, wherever the hips now are. The camera
        // cannot tell a chair from standing from every angle, so standing still is taken at its word.
        judge.frames(stood, 24_000, 400)
        assertFalse(judge.brokeOff)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        assertEquals(5, judge.frames(stood, 24_500, 5_400))
        assertFalse(judge.brokeOff)
    }

    @Test fun `getting up and sitting straight back down costs the seconds, not the sitting`() {
        val judge = MeditationJudge()
        assertEquals(10, judge.frames(sitting, 0, 10_400))
        judge.frames(stood, 10_500, 900)
        assertEquals(PoseJudge.Phase.NOT_IN_POSITION, judge.phase)
        for (t in 11_500L..16_000L step 100) {
            judge.observe(sitting, t)
            assertFalse(judge.brokeOff)
        }
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
    }

    @Test fun `leaving the picture starts the count over, and coming back does not restart it twice`() {
        val judge = MeditationJudge()
        judge.frames(sitting, 0, 10_400)
        var restarts = 0
        for (t in 10_500L..20_000L step 100) {
            judge.observe(null, t)
            if (judge.brokeOff) restarts++
        }
        assertEquals(1, restarts)
        assertEquals(PoseJudge.Phase.NO_BODY, judge.phase)
        for (t in 20_100L..25_000L step 100) {
            judge.observe(sitting, t)
            assertFalse(judge.brokeOff)
        }
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
    }

    @Test fun `a gap in the frames longer than a break starts the count over, a shorter one only costs the gap`() {
        val judge = MeditationJudge()
        judge.frames(sitting, 0, 10_400)
        // Two seconds without a frame: nothing for the gap, no restart.
        assertEquals(0, judge.observe(sitting, 12_400))
        assertFalse(judge.brokeOff)
        assertEquals(1, judge.frames(sitting, 12_500, 1_000))
        // Five seconds without a frame: the camera was closed; another sitting.
        judge.observe(sitting, 18_500)
        assertTrue(judge.brokeOff)
        judge.observe(sitting, 18_600)
        assertFalse(judge.brokeOff)
    }

    @Test fun `nothing counted yet means nothing to start over`() {
        val judge = MeditationJudge()
        judge.frames(sitting, 0, 300)
        for (t in 400L..5_000L step 100) {
            judge.observe(stood, t)
            assertFalse(judge.brokeOff)
        }
    }

    @Test fun `the coaching names what is wrong`() {
        val judge = MeditationJudge()
        assertEquals("Starting the camera", judge.coaching(false).first)
        judge.frames(null, 0, 500)
        assertEquals("Looking for you", judge.coaching(true).first)
        judge.frames(seated(hipVisibility = 0.2f), 600, 500)
        assertEquals("Move back a little", judge.coaching(true).first)
        judge.frames(sideOn, 1_200, 500)
        assertEquals("Face the phone", judge.coaching(true).first)
        judge.frames(slumped, 1_800, 500)
        assertEquals("Sit up", judge.coaching(true).first)
        // The head came back from the slump within the last second: moving, until that is out of the window.
        judge.frames(sitting, 2_400, 1_400)
        assertEquals("Sit still", judge.coaching(true).first)
    }

    @Test fun `movement is measured against the torso, so distance from the phone does not matter`() {
        // A body half the size in the picture, a room away, with its head moved by the same picture distance.
        fun far(headDx: Float = 0f): BodyPose {
            val pose = seated()
            fun shrink(l: Landmark) = Landmark(0.5f + (l.x - 0.5f) / 2f, 0.5f + (l.y - 0.5f) / 2f, l.visibility)
            return pose.copy(
                nose = shrink(pose.nose).let { it.copy(x = it.x + headDx) },
                leftEye = shrink(pose.leftEye), rightEye = shrink(pose.rightEye),
                mouthLeft = shrink(pose.mouthLeft), mouthRight = shrink(pose.mouthRight),
                leftShoulder = shrink(pose.leftShoulder), rightShoulder = shrink(pose.rightShoulder),
                leftHip = shrink(pose.leftHip), rightHip = shrink(pose.rightHip),
                leftKnee = shrink(pose.leftKnee), rightKnee = shrink(pose.rightKnee),
            )
        }
        // 0.04 of the picture is 0.16 of a torso 0.25 long: under the line.
        val near = BodyMotion()
        near.observe(seated(), 0)
        assertFalse(near.observe(seated(headDx = 0.04f), 100))
        // The same 0.04 on a torso 0.125 long is 0.32: over it.
        val away = BodyMotion()
        away.observe(far(), 0)
        assertTrue(away.observe(far(headDx = 0.04f), 100))
    }
}
