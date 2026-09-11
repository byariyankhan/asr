package io.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldJudgeTest {

    /** A body seen from the left side, given as the four points the hold rules read. */
    private fun body(
        shoulder: Pair<Float, Float>,
        hip: Pair<Float, Float>,
        knee: Pair<Float, Float>,
        ankle: Pair<Float, Float>,
        visibility: Float = 0.95f,
    ): BodyPose {
        fun at(p: Pair<Float, Float>) = Landmark(p.first, p.second, visibility)
        val ghost = Landmark(0.5f, 0.5f, 0.2f)
        val face = Landmark(0.2f, 0.45f, 0.9f)
        return BodyPose(
            nose = face, leftEye = face, rightEye = face.copy(visibility = 0.3f),
            mouthLeft = face, mouthRight = face,
            leftShoulder = at(shoulder), leftElbow = ghost, leftWrist = ghost, leftHip = at(hip),
            rightShoulder = ghost, rightElbow = ghost, rightWrist = ghost, rightHip = ghost,
            leftKnee = at(knee), rightKnee = ghost, leftAnkle = at(ankle), rightAnkle = ghost,
        )
    }

    private val plank = body(0.3f to 0.5f, 0.55f to 0.58f, 0.68f to 0.62f, 0.8f to 0.66f)

    /**
     * A body seen head-on from a phone stood upright on the floor ahead of
     * the hands: a face low in the picture, shoulders [width] across on
     * the line [shoulderY], the eyes [eyesAbove] shoulder widths above
     * that line (negative: below it), and the hips, when seen, [hipsBelow]
     * widths below it and [hipsAcross] widths off centre.
     */
    private fun front(
        eyesAbove: Float,
        hipsBelow: Float = -0.25f,
        hipsAcross: Float = 0f,
        hipVisibility: Float = 0.7f,
        width: Float = 0.4f,
        shoulderY: Float = 0.55f,
    ): BodyPose {
        val eyeY = shoulderY - eyesAbove * width
        val hipY = shoulderY + hipsBelow * width
        val hipX = 0.5f + hipsAcross * width
        val ghost = Landmark(0.5f, 0.5f, 0.2f)
        fun at(x: Float, y: Float, v: Float = 0.9f) = Landmark(x, y, v)
        return BodyPose(
            nose = at(0.5f, eyeY + 0.03f), leftEye = at(0.54f, eyeY), rightEye = at(0.46f, eyeY),
            mouthLeft = at(0.52f, eyeY + 0.06f), mouthRight = at(0.48f, eyeY + 0.06f),
            leftShoulder = at(0.5f + width / 2f, shoulderY), leftElbow = at(0.72f, 0.8f), leftWrist = at(0.7f, 0.95f),
            leftHip = at(hipX + 0.04f, hipY, hipVisibility),
            rightShoulder = at(0.5f - width / 2f, shoulderY), rightElbow = at(0.28f, 0.8f), rightWrist = at(0.3f, 0.95f),
            rightHip = at(hipX - 0.04f, hipY, hipVisibility),
            leftKnee = ghost, rightKnee = ghost, leftAnkle = ghost, rightAnkle = ghost,
        )
    }

    /** Head down in line with the back, hips up behind the shoulders. */
    private val plankFront = front(eyesAbove = -0.15f)

    /**
     * A body facing a phone stood low in front of it: shoulders 0.3 wide
     * on 0.30, hips on 0.55 (a torso of 0.25), and each leg as given,
     * knee then ankle, for the left side and mirrored for the right.
     */
    private fun facingLegs(knee: Pair<Float, Float>, ankle: Pair<Float, Float>, shoulderY: Float = 0.30f): BodyPose {
        val ghost = Landmark(0.5f, 0.5f, 0.2f)
        fun at(x: Float, y: Float) = Landmark(x, y, 0.9f)
        fun mirror(p: Pair<Float, Float>) = at(1f - p.first, p.second)
        return BodyPose(
            nose = at(0.5f, shoulderY - 0.1f), leftEye = at(0.52f, shoulderY - 0.12f), rightEye = at(0.48f, shoulderY - 0.12f),
            mouthLeft = at(0.51f, shoulderY - 0.08f), mouthRight = at(0.49f, shoulderY - 0.08f),
            leftShoulder = at(0.65f, shoulderY), leftElbow = ghost, leftWrist = ghost, leftHip = at(0.58f, 0.55f),
            rightShoulder = at(0.35f, shoulderY), rightElbow = ghost, rightWrist = ghost, rightHip = at(0.42f, 0.55f),
            leftKnee = at(knee.first, knee.second), rightKnee = mirror(knee),
            leftAnkle = at(ankle.first, ankle.second), rightAnkle = mirror(ankle),
        )
    }

    /** Thighs towards the camera, knees drawn a touch above the hips from low down, shins straight to the feet. */
    private val wallSitFront = facingLegs(knee = 0.60f to 0.52f, ankle = 0.60f to 0.78f)

    /** On the feet: knees a thigh below the hips. */
    private val standingFacing = facingLegs(knee = 0.58f to 0.78f, ankle = 0.58f to 1.0f)

    /** A half squat: the knees below the hips, the shins still down. */
    private val halfSquatFacing = facingLegs(knee = 0.60f to 0.66f, ankle = 0.60f to 0.90f)

    /** Cross-legged on the floor: knees out to the sides, ankles up by them. */
    private val crossLeggedFacing = facingLegs(knee = 0.78f to 0.60f, ankle = 0.55f to 0.66f)

    /** Standing facing the phone: eyes well above the shoulders, hips a torso below them. */
    private val standingFront = front(eyesAbove = 0.6f, hipsBelow = 1.4f)

    /** Sitting up facing the phone, as for a meditation. */
    private val sittingFront = front(eyesAbove = 0.5f, hipsBelow = 1.2f)
    private val lyingFlat = body(0.3f to 0.7f, 0.55f to 0.7f, 0.68f to 0.7f, 0.8f to 0.7f)
    private val standing = body(0.5f to 0.2f, 0.5f to 0.5f, 0.5f to 0.7f, 0.5f to 0.9f)
    private val sittingLegsOut = body(0.3f to 0.3f, 0.35f to 0.62f, 0.6f to 0.66f, 0.8f to 0.7f)
    private val saggingHips = body(0.3f to 0.5f, 0.55f to 0.68f, 0.68f to 0.64f, 0.8f to 0.66f)
    private val wallSit = body(0.4f to 0.3f, 0.4f to 0.55f, 0.6f to 0.55f, 0.6f to 0.8f)

    @Test fun `a straight body sloping down to the feet is a plank`() {
        assertTrue(HoldPositions.plankSide(plank))
        assertTrue(HoldPositions.plank(plank))
    }

    @Test fun `lying flat, standing, sitting and sagging are not a plank`() {
        assertFalse(HoldPositions.plank(lyingFlat))
        assertFalse(HoldPositions.plank(standing))
        assertFalse(HoldPositions.plank(sittingLegsOut))
        assertFalse(HoldPositions.plank(saggingHips))
    }

    @Test fun `head-on from the floor, a head down in line with the back over hips behind it is a plank`() {
        assertTrue(HoldPositions.plankFront(plankFront))
        assertTrue(HoldPositions.plank(plankFront))
        // Hips the model has not found: the face and shoulders decide.
        assertTrue(HoldPositions.plank(front(eyesAbove = -0.1f, hipVisibility = 0.2f)))
        // Eyes level with the shoulders, hips level with them: still a plank.
        assertTrue(HoldPositions.plank(front(eyesAbove = 0.1f, hipsBelow = 0.2f)))
    }

    @Test fun `head-on, standing and sitting are not a plank`() {
        assertFalse(HoldPositions.plank(standingFront))
        assertFalse(HoldPositions.plank(sittingFront))
        // Head bowed to shoulder level while standing: the hips give it away.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.2f, hipsBelow = 1.4f)))
        // Hips off to one side: a body turned away, not one receding behind its shoulders.
        assertFalse(HoldPositions.plank(front(eyesAbove = -0.1f, hipsAcross = 0.9f)))
        // Lying on one side in front of the phone: the shoulders one above the other.
        val onOneSide = plankFront.copy(
            leftShoulder = Landmark(0.5f, 0.35f, 0.9f),
            rightShoulder = Landmark(0.5f, 0.75f, 0.9f),
        )
        assertFalse(HoldPositions.plank(onOneSide))
    }

    @Test fun `head-on from low down, thighs at the camera over shins to the feet is a wall sit`() {
        assertTrue(HoldPositions.wallSitFront(wallSitFront))
        assertTrue(HoldPositions.wallSit(wallSitFront))
        // Knees level with the hips, the phone at hip height: still a wall sit.
        assertTrue(HoldPositions.wallSit(facingLegs(knee = 0.60f to 0.56f, ankle = 0.60f to 0.80f)))
    }

    @Test fun `head-on, standing, a half squat, cross-legged and a slouch are not a wall sit`() {
        assertFalse(HoldPositions.wallSit(standingFacing))
        assertFalse(HoldPositions.wallSit(halfSquatFacing))
        assertFalse(HoldPositions.wallSit(crossLeggedFacing))
        // Leaning right over: the shoulders come down towards the hips.
        assertFalse(HoldPositions.wallSit(facingLegs(knee = 0.60f to 0.52f, ankle = 0.60f to 0.78f, shoulderY = 0.50f)))
        // Feet out of the picture: the shins cannot be seen to drop, so not yet.
        assertFalse(HoldPositions.wallSit(wallSitFront.copy(leftAnkle = BodyPose.UNSEEN, rightAnkle = BodyPose.UNSEEN)))
    }

    @Test fun `a face with its shoulders is a body, for the coaching`() {
        assertTrue(HoldPositions.hasBody(standingFront))
        assertFalse(HoldPositions.hasBody(front(eyesAbove = 0.6f, hipVisibility = 0.2f).copy(
            leftShoulder = BodyPose.UNSEEN, rightShoulder = BodyPose.UNSEEN,
        )))
    }

    @Test fun `a plank the other way round in the picture is still a plank`() {
        val mirrored = body(0.7f to 0.5f, 0.45f to 0.58f, 0.32f to 0.62f, 0.2f to 0.66f)
        assertTrue(HoldPositions.plank(mirrored))
    }

    @Test fun `legs out of the picture is no plank at all`() {
        val noLegs = body(0.3f to 0.5f, 0.55f to 0.58f, 0.68f to 0.62f, 0.8f to 0.66f, visibility = 0.95f)
            .copy(leftAnkle = BodyPose.UNSEEN, leftKnee = BodyPose.UNSEEN)
        assertFalse(HoldPositions.plank(noLegs))
        assertTrue(HoldPositions.hasBody(noLegs))
    }

    @Test fun `back upright, thigh level, knee square is a wall sit`() {
        assertTrue(HoldPositions.wallSitSide(wallSit))
        assertTrue(HoldPositions.wallSit(wallSit))
    }

    @Test fun `standing and sitting with the legs out are not a wall sit`() {
        assertFalse(HoldPositions.wallSit(standing))
        assertFalse(HoldPositions.wallSit(sittingLegsOut))
        assertFalse(HoldPositions.wallSit(plank))
    }

    @Test fun `a half squat with the thighs sloping is not a wall sit`() {
        val halfway = body(0.4f to 0.3f, 0.42f to 0.5f, 0.6f to 0.62f, 0.62f to 0.85f)
        assertFalse(HoldPositions.wallSit(halfway))
    }

    private fun judge() = HoldJudge(
        position = HoldPositions::plank,
        hasBody = HoldPositions::hasBody,
        coach = { phase, _ -> phase.name to "" },
    )

    /** Feeds the same pose every 100 ms from [from] for [millis], returning the seconds handed out. */
    private fun HoldJudge.feed(pose: BodyPose?, from: Long, millis: Long): Int =
        (0..millis step 100).sumOf { observe(pose, from + it) }

    @Test fun `the clock starts once the plank has settled and hands out whole seconds`() {
        val judge = judge()
        assertEquals(0, judge.observe(plank, 0))
        assertEquals(PoseJudge.Phase.NO_BODY, judge.phase)
        // Believed at 400 ms; the first second is complete at 1400.
        assertEquals(0, judge.feed(plank, 100, 1_200))
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        assertEquals(1, judge.observe(plank, 1_400))
        assertEquals(4, judge.feed(plank, 1_500, 4_000))
        assertEquals(5_100L, judge.heldMillis)
    }

    @Test fun `a break pauses the clock and a return resumes it`() {
        val judge = judge()
        judge.feed(plank, 0, 3_400)
        assertEquals(3_000L, judge.heldMillis)
        // Standing up: believed after 400 ms, which still count; then nothing does.
        judge.feed(standing, 3_500, 2_000)
        assertEquals(PoseJudge.Phase.NOT_IN_POSITION, judge.phase)
        assertEquals(3_400L, judge.heldMillis)
        // Back down: the settling time is not counted, the rest is.
        judge.feed(plank, 5_600, 400)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        assertEquals(3_400L, judge.heldMillis)
        judge.feed(plank, 6_100, 1_000)
        assertEquals(4_500L, judge.heldMillis)
    }

    @Test fun `a frame or two of a lost hip is not a break`() {
        val judge = judge()
        judge.feed(plank, 0, 2_400)
        judge.observe(null, 2_500)
        judge.observe(standing, 2_600)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        judge.feed(plank, 2_700, 1_000)
        assertEquals(3_300L, judge.heldMillis)
    }

    @Test fun `a gap in the frames is the camera stopped, and is worth nothing`() {
        val judge = judge()
        judge.feed(plank, 0, 2_400)
        assertEquals(2_000L, judge.heldMillis)
        // The app went to the background for 45 seconds with the plank
        // entered; the first frame back must not be 45 seconds of plank.
        assertEquals(0, judge.observe(plank, 47_400))
        assertEquals(2_000L, judge.heldMillis)
        assertEquals(PoseJudge.Phase.WORKING, judge.phase)
        // And counting picks up from there, frame to frame.
        judge.feed(plank, 47_500, 1_000)
        assertEquals(3_100L, judge.heldMillis)
    }

    @Test fun `nobody in the picture is nobody, not somebody out of position`() {
        val judge = judge()
        judge.feed(null, 0, 1_000)
        assertEquals(PoseJudge.Phase.NO_BODY, judge.phase)
        judge.feed(standing, 1_100, 1_000)
        assertEquals(PoseJudge.Phase.NOT_IN_POSITION, judge.phase)
        assertEquals(0L, judge.heldMillis)
    }

    @Test fun `the coaching follows the phase, after the camera has started`() {
        val judge = judge()
        assertEquals("Starting the camera", judge.coaching(started = false).first)
        assertEquals("NO_BODY", judge.coaching(started = true).first)
        judge.feed(plank, 0, 1_000)
        assertEquals("WORKING", judge.coaching(started = true).first)
    }
}
