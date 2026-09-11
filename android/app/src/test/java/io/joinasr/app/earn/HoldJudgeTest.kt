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
     * widths below it and [hipsAcross] widths off centre. The eyes are
     * [eyeGap] apart: a fifth of the shoulder width unless said, which is
     * a head on top of its shoulders from a stride away, and more when
     * the head is nearer the phone than the shoulders.
     */
    private fun front(
        eyesAbove: Float,
        hipsBelow: Float = -0.25f,
        hipsAcross: Float = 0f,
        hipVisibility: Float = 0.7f,
        width: Float = 0.4f,
        shoulderY: Float = 0.55f,
        /** How far below the shoulder line the elbows and wrists are, in shoulder widths. */
        elbowsBelow: Float = 0.625f,
        wristsBelow: Float = 1.0f,
        eyeGap: Float = 0.08f,
    ): BodyPose {
        val eyeY = shoulderY - eyesAbove * width
        val hipY = shoulderY + hipsBelow * width
        val hipX = 0.5f + hipsAcross * width
        val elbowY = shoulderY + elbowsBelow * width
        val wristY = shoulderY + wristsBelow * width
        val ghost = Landmark(0.5f, 0.5f, 0.2f)
        fun at(x: Float, y: Float, v: Float = 0.9f) = Landmark(x, y, v)
        return BodyPose(
            nose = at(0.5f, eyeY + 0.03f), leftEye = at(0.5f + eyeGap / 2f, eyeY), rightEye = at(0.5f - eyeGap / 2f, eyeY),
            mouthLeft = at(0.52f, eyeY + 0.06f), mouthRight = at(0.48f, eyeY + 0.06f),
            leftShoulder = at(0.5f + width / 2f, shoulderY), leftElbow = at(0.72f, elbowY), leftWrist = at(0.7f, wristY),
            leftHip = at(hipX + 0.04f, hipY, hipVisibility),
            rightShoulder = at(0.5f - width / 2f, shoulderY), rightElbow = at(0.28f, elbowY), rightWrist = at(0.3f, wristY),
            rightHip = at(hipX - 0.04f, hipY, hipVisibility),
            leftKnee = ghost, rightKnee = ghost, leftAnkle = ghost, rightAnkle = ghost,
        )
    }

    /** Head down in line with the back, hips up behind the shoulders, from a stride away. */
    private val plankFront = front(eyesAbove = -0.15f)

    /**
     * The two positions from the founder's phone, upright on a bed 30 to
     * 50 cm ahead of the hands, as the model drew them: shoulders 0.6 of
     * the frame across, the face raised to look at the screen 0.4 to 0.65
     * widths above the shoulder line and half again as big as a face on
     * top of its shoulders (eyes 0.3 of a width apart), hips behind the
     * torso where the model does not find them. On the hands the elbows
     * are in the picture, only 0.4 of a width under the shoulders; the
     * wrists are below the frame. On the forearms the arms are below the
     * frame altogether.
     */
    private val closeHandsPlank = front(
        eyesAbove = 0.4f, width = 0.6f, eyeGap = 0.18f, hipVisibility = 0.2f, elbowsBelow = 0.4f,
    ).copy(leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN)
    private val closeForearmPlank = front(
        eyesAbove = 0.65f, width = 0.64f, eyeGap = 0.18f, hipVisibility = 0.2f,
    ).copy(leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN, leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN)

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
        // Hips the model has not found: the face, shoulders and arms decide.
        assertTrue(HoldPositions.plank(front(eyesAbove = -0.1f, hipVisibility = 0.2f)))
        // Eyes level with the shoulders, hips level with them: still a plank.
        assertTrue(HoldPositions.plank(front(eyesAbove = 0.1f, hipsBelow = 0.2f)))
        // The phone a hand's width away: the hips, level with the shoulders but a torso further
        // off, are drawn well under them from the floor. Not as far under as a body on its feet.
        assertTrue(HoldPositions.plank(front(eyesAbove = 0.3f, hipsBelow = 0.7f, width = 0.6f, eyeGap = 0.15f)))
    }

    @Test fun `close head-on, a plank on the hands with only the elbows in the picture is a plank`() {
        assertTrue(HoldPositions.plankFront(closeHandsPlank))
        assertTrue(HoldPositions.plank(closeHandsPlank))
        // A frame that draws the elbows a touch higher: the face, half again its size on top of
        // the shoulders, still says the head is a foot nearer the phone than they are.
        assertTrue(HoldPositions.plank(front(
            eyesAbove = 0.4f, width = 0.6f, eyeGap = 0.18f, hipVisibility = 0.2f, elbowsBelow = 0.34f,
        ).copy(leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN)))
        // One elbow found, the other lost in the torso: enough.
        assertTrue(HoldPositions.plank(closeHandsPlank.copy(rightElbow = BodyPose.UNSEEN)))
        // Head turned to the floor rather than the screen, eyes level with the shoulders: still a plank.
        assertTrue(HoldPositions.plank(closeHandsPlank.copy(
            leftEye = closeHandsPlank.leftEye.copy(y = 0.55f), rightEye = closeHandsPlank.rightEye.copy(y = 0.55f),
        )))
    }

    @Test fun `close head-on, a plank on the forearms with the arms below the frame is a plank`() {
        assertTrue(HoldPositions.plankFront(closeForearmPlank))
        assertTrue(HoldPositions.plank(closeForearmPlank))
        // The phone a little further off, elbows on the floor a width and more under the shoulders,
        // the wrists ahead of them on the floor: a forearm held flat under a body held high.
        assertTrue(HoldPositions.plank(front(
            eyesAbove = 0.5f, width = 0.5f, eyeGap = 0.13f, hipVisibility = 0.2f, elbowsBelow = 1.3f, wristsBelow = 1.35f,
        )))
        // The same from a stride away, with the hips in: the arms say it.
        assertTrue(HoldPositions.plank(front(eyesAbove = 0.2f, hipsBelow = 0.3f, elbowsBelow = 1.4f, wristsBelow = 1.4f)))
    }

    @Test fun `head-on, lying face down is not a plank, however the head and arms are held`() {
        // Head raised to look at the phone, arms beside the body: shoulders a hand above the floor,
        // so the elbows are barely below them.
        assertFalse(HoldPositions.plank(front(eyesAbove = -0.15f, elbowsBelow = 0.2f, wristsBelow = 0.4f)))
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.3f, hipsBelow = 0.5f, elbowsBelow = 0.25f, wristsBelow = 0.45f)))
        // Arms stretched out on the floor towards the phone: the elbows and wrists lower, still under
        // what an arm holding a body up comes to.
        assertFalse(HoldPositions.plank(front(eyesAbove = -0.15f, elbowsBelow = 0.35f, wristsBelow = 0.7f)))
        // A sphinx: chest propped on the elbows, forearms flat on the floor ahead of them. The
        // elbows are as far under the shoulders as a hands plank's, but the wrists are level with
        // them, and the shoulders are not a width above the floor.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.3f, hipsBelow = 0.5f, elbowsBelow = 0.66f, wristsBelow = 0.72f)))
        // The same up close, with the raised face drawn big: the flat forearms still say lying.
        assertFalse(HoldPositions.plank(front(
            eyesAbove = 0.5f, width = 0.6f, eyeGap = 0.15f, hipVisibility = 0.2f, elbowsBelow = 0.66f, wristsBelow = 0.8f,
        )))
        // Arms out of the picture and the face no bigger than a head on top of its shoulders: not
        // enough to call it a plank, from a stride away or a step.
        val unseenArms = plankFront.copy(
            leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN,
            leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN,
        )
        assertFalse(HoldPositions.plank(unseenArms))
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.3f, width = 0.55f, eyeGap = 0.11f, hipVisibility = 0.2f).copy(
            leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN,
            leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN,
        )))
        // One elbow seen, on the floor, the wrists out of the picture: a plank.
        assertTrue(HoldPositions.plank(plankFront.copy(rightElbow = BodyPose.UNSEEN, leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN)))
    }

    @Test fun `head-on, a plank on the hands from a stride away is held up by its wrists`() {
        // From a stride the elbows come out under half a width below the shoulders; the wrists, on
        // the floor an arm's length down, come out well over one.
        val stride = front(eyesAbove = -0.15f, elbowsBelow = 0.44f, wristsBelow = 1.25f)
        assertTrue(HoldPositions.plank(stride))
        // The wrists alone will do when the elbows are out of the picture.
        assertTrue(HoldPositions.plank(stride.copy(leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN)))
    }

    @Test fun `head-on, kneeling is not a plank`() {
        // Up on the knees facing the phone: the hips a torso below the shoulders.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.6f, hipsBelow = 1.2f)))
        // Kneeling right in front of the phone with the hips below the frame: the arms hang as low
        // as a plank's, but the face is no bigger than a head on top of its shoulders.
        assertFalse(HoldPositions.plank(front(
            eyesAbove = 0.6f, width = 0.5f, eyeGap = 0.085f, hipVisibility = 0.2f, elbowsBelow = 0.6f, wristsBelow = 1.1f,
        )))
        // Kneeling with the arms folded, hips below the frame.
        assertFalse(HoldPositions.plank(front(
            eyesAbove = 0.6f, width = 0.5f, eyeGap = 0.085f, hipVisibility = 0.2f, elbowsBelow = 0.25f, wristsBelow = 0.2f,
        )))
        // Kneeling with the arms out of the picture.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.6f, width = 0.5f, eyeGap = 0.085f, hipVisibility = 0.2f).copy(
            leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN,
            leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN,
        )))
    }

    @Test fun `a slow phone's frames, a fraction of a second apart, still add up`() {
        val judge = judge()
        judge.feed(plank, 0, 2_400)
        assertEquals(2_000L, judge.heldMillis)
        // Three frames a second, then two: every gap counts.
        judge.observe(plank, 2_800)
        judge.observe(plank, 3_300)
        judge.observe(plank, 3_900)
        assertEquals(3_500L, judge.heldMillis)
        // Two seconds without a frame is the camera stopped, not a slow one.
        judge.observe(plank, 5_900)
        assertEquals(3_500L, judge.heldMillis)
    }

    @Test fun `head-on, standing and sitting are not a plank`() {
        assertFalse(HoldPositions.plank(standingFront))
        assertFalse(HoldPositions.plank(sittingFront))
        // Head bowed to shoulder level while standing: the hips give it away.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.2f, hipsBelow = 1.4f)))
        // Standing close to a phone propped at chest height, hips below the frame, arms hanging as
        // low as a plank's: the face is no bigger than a head on top of its shoulders.
        assertFalse(HoldPositions.plank(front(
            eyesAbove = 0.6f, width = 0.5f, eyeGap = 0.085f, hipVisibility = 0.2f, elbowsBelow = 0.65f, wristsBelow = 1.2f,
        )))
        // The same with the arms out of the picture.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.6f, width = 0.5f, eyeGap = 0.085f, hipVisibility = 0.2f).copy(
            leftElbow = BodyPose.UNSEEN, rightElbow = BodyPose.UNSEEN,
            leftWrist = BodyPose.UNSEEN, rightWrist = BodyPose.UNSEEN,
        )))
        // Sitting on a chair with the hands on the knees, hips in the picture.
        assertFalse(HoldPositions.plank(front(eyesAbove = 0.5f, hipsBelow = 1.2f, elbowsBelow = 0.6f, wristsBelow = 1.0f)))
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

    @Test fun `a face with its shoulders is a body for the plank, and not a body for the wall sit`() {
        assertTrue(HoldPositions.hasBody(standingFront))
        assertFalse(HoldPositions.wallSitBody(standingFront))
        assertTrue(HoldPositions.wallSitBody(wallSitFront))
        assertTrue(HoldPositions.wallSitBody(wallSit))
        // Head-on with one foot out of the picture: not yet a body to judge, whatever the other side shows.
        assertFalse(HoldPositions.wallSitBody(wallSitFront.copy(rightAnkle = BodyPose.UNSEEN)))
        assertFalse(HoldPositions.wallSitBody(wallSitFront.copy(leftKnee = BodyPose.UNSEEN)))
        // Side-on, the far side is guessed through the body and is not asked for.
        assertTrue(HoldPositions.wallSitBody(wallSit.copy(rightKnee = BodyPose.UNSEEN, rightAnkle = BodyPose.UNSEEN)))
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
