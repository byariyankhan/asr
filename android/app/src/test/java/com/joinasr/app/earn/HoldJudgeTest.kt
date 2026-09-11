package com.joinasr.app.earn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldJudgeTest {

    /**
     * A body seen from the left side, given as the four points the hold
     * rules read, with the near elbow a little forward of the shoulder
     * and 0.12 of the picture below it, as an arm holding the body up
     * from a phone on the floor comes out.
     */
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
        val elbow = Landmark(shoulder.first + 0.02f, shoulder.second + 0.12f, visibility)
        return BodyPose(
            nose = face, leftEye = face, rightEye = face.copy(visibility = 0.3f),
            mouthLeft = face, mouthRight = face,
            leftShoulder = at(shoulder), leftElbow = elbow, leftWrist = ghost, leftHip = at(hip),
            rightShoulder = ghost, rightElbow = ghost, rightWrist = ghost, rightHip = ghost,
            leftKnee = at(knee), rightKnee = ghost, leftAnkle = at(ankle), rightAnkle = ghost,
        )
    }

    private val plank = body(0.3f to 0.5f, 0.55f to 0.58f, 0.68f to 0.62f, 0.8f to 0.66f)

    // Fixtures below are the pose model's own output (MediaPipe Pose Landmarker
    // lite, the model the app ships) on photographs and on frames from the
    // founder's phone, x scaled by the picture's aspect ratio as
    // BodyPose.fromNormalised does. Regenerate with tools in the session
    // notes rather than by hand; the numbers are the point.

    /** A hands plank photographed from the side (Wikimedia Commons, Plank.jpg): the model's own output. */
    private val sidePlankPhoto = BodyPose(
        nose = Landmark(1.193f, 0.518f, 1.00f),
        leftEye = Landmark(1.203f, 0.495f, 1.00f),
        rightEye = Landmark(1.202f, 0.497f, 1.00f),
        mouthLeft = Landmark(1.164f, 0.518f, 1.00f),
        mouthRight = Landmark(1.166f, 0.519f, 1.00f),
        leftShoulder = Landmark(1.028f, 0.475f, 1.00f),
        leftElbow = Landmark(0.976f, 0.634f, 0.39f),
        leftWrist = Landmark(0.986f, 0.784f, 0.56f),
        leftHip = Landmark(0.705f, 0.550f, 1.00f),
        rightShoulder = Landmark(1.042f, 0.501f, 1.00f),
        rightElbow = Landmark(0.995f, 0.675f, 1.00f),
        rightWrist = Landmark(1.013f, 0.843f, 0.99f),
        rightHip = Landmark(0.698f, 0.568f, 1.00f),
        leftKnee = Landmark(0.466f, 0.656f, 0.41f),
        rightKnee = Landmark(0.461f, 0.680f, 0.97f),
        leftAnkle = Landmark(0.236f, 0.703f, 0.71f),
        rightAnkle = Landmark(0.197f, 0.734f, 0.98f),
    )

    /** A hands plank on medicine balls photographed from ahead and to one side (Plank_on_a_pair_of_medicine_balls.jpg). */
    private val diagonalPlankPhoto = BodyPose(
        nose = Landmark(0.336f, 0.273f, 1.00f),
        leftEye = Landmark(0.333f, 0.246f, 1.00f),
        rightEye = Landmark(0.332f, 0.251f, 1.00f),
        mouthLeft = Landmark(0.361f, 0.277f, 1.00f),
        mouthRight = Landmark(0.361f, 0.279f, 1.00f),
        leftShoulder = Landmark(0.445f, 0.272f, 1.00f),
        leftElbow = Landmark(0.500f, 0.427f, 1.00f),
        leftWrist = Landmark(0.493f, 0.599f, 0.99f),
        leftHip = Landmark(0.740f, 0.377f, 1.00f),
        rightShoulder = Landmark(0.467f, 0.290f, 1.00f),
        rightElbow = Landmark(0.519f, 0.429f, 0.21f),
        rightWrist = Landmark(0.523f, 0.583f, 0.42f),
        rightHip = Landmark(0.752f, 0.369f, 1.00f),
        leftKnee = Landmark(0.966f, 0.462f, 0.99f),
        rightKnee = Landmark(0.942f, 0.418f, 0.32f),
        leftAnkle = Landmark(1.230f, 0.512f, 0.99f),
        rightAnkle = Landmark(1.129f, 0.332f, 0.56f),
    )

    /** A forearm plank photographed from the side (Forearm_Plank_Phalakasana_variant_outdoor_class.jpg). */
    private val forearmPlankPhoto = BodyPose(
        nose = Landmark(0.226f, 0.507f, 1.00f),
        leftEye = Landmark(0.214f, 0.495f, 1.00f),
        rightEye = Landmark(0.223f, 0.478f, 1.00f),
        mouthLeft = Landmark(0.238f, 0.514f, 0.99f),
        mouthRight = Landmark(0.244f, 0.507f, 1.00f),
        leftShoulder = Landmark(0.247f, 0.570f, 1.00f),
        leftElbow = Landmark(0.237f, 0.738f, 0.97f),
        leftWrist = Landmark(0.234f, 0.654f, 0.77f),
        leftHip = Landmark(0.546f, 0.575f, 1.00f),
        rightShoulder = Landmark(0.338f, 0.450f, 1.00f),
        rightElbow = Landmark(0.420f, 0.545f, 0.26f),
        rightWrist = Landmark(0.362f, 0.573f, 0.16f),
        rightHip = Landmark(0.585f, 0.494f, 1.00f),
        leftKnee = Landmark(0.822f, 0.677f, 0.99f),
        rightKnee = Landmark(0.881f, 0.615f, 0.56f),
        leftAnkle = Landmark(1.110f, 0.761f, 0.98f),
        rightAnkle = Landmark(1.121f, 0.725f, 0.57f),
    )

    /** A sphinx: chest propped on the elbows, hips on the floor (IMG_0549_2_Sphinx.jpg). */
    private val sphinxPhoto = BodyPose(
        nose = Landmark(0.233f, 0.299f, 1.00f),
        leftEye = Landmark(0.244f, 0.276f, 1.00f),
        rightEye = Landmark(0.244f, 0.273f, 1.00f),
        mouthLeft = Landmark(0.248f, 0.319f, 1.00f),
        mouthRight = Landmark(0.247f, 0.316f, 1.00f),
        leftShoulder = Landmark(0.361f, 0.428f, 1.00f),
        leftElbow = Landmark(0.366f, 0.649f, 1.00f),
        leftWrist = Landmark(0.192f, 0.697f, 0.99f),
        leftHip = Landmark(0.673f, 0.634f, 1.00f),
        rightShoulder = Landmark(0.393f, 0.378f, 1.00f),
        rightElbow = Landmark(0.411f, 0.574f, 0.09f),
        rightWrist = Landmark(0.242f, 0.650f, 0.23f),
        rightHip = Landmark(0.678f, 0.592f, 1.00f),
        leftKnee = Landmark(0.997f, 0.634f, 0.98f),
        rightKnee = Landmark(0.988f, 0.616f, 0.21f),
        leftAnkle = Landmark(1.336f, 0.637f, 0.99f),
        rightAnkle = Landmark(1.267f, 0.618f, 0.63f),
    )

    /** The lowest cobra in the set, chest barely off the floor (A_style_of_bhujangasana.JPG). */
    private val lowCobraPhoto = BodyPose(
        nose = Landmark(0.252f, 0.330f, 1.00f),
        leftEye = Landmark(0.274f, 0.322f, 1.00f),
        rightEye = Landmark(0.263f, 0.312f, 1.00f),
        mouthLeft = Landmark(0.274f, 0.356f, 1.00f),
        mouthRight = Landmark(0.264f, 0.347f, 1.00f),
        leftShoulder = Landmark(0.390f, 0.489f, 1.00f),
        leftElbow = Landmark(0.389f, 0.676f, 1.00f),
        leftWrist = Landmark(0.252f, 0.694f, 0.99f),
        leftHip = Landmark(0.665f, 0.590f, 1.00f),
        rightShoulder = Landmark(0.365f, 0.382f, 1.00f),
        rightElbow = Landmark(0.345f, 0.467f, 0.40f),
        rightWrist = Landmark(0.221f, 0.537f, 0.69f),
        rightHip = Landmark(0.654f, 0.517f, 1.00f),
        leftKnee = Landmark(0.889f, 0.637f, 0.99f),
        rightKnee = Landmark(0.843f, 0.520f, 0.23f),
        leftAnkle = Landmark(1.127f, 0.624f, 0.99f),
        rightAnkle = Landmark(1.011f, 0.493f, 0.44f),
    )

    /** Sitting cross-legged on the floor (Cross-legged_sitting_woman.jpg). */
    private val crossLeggedPhoto = BodyPose(
        nose = Landmark(0.328f, 0.500f, 1.00f),
        leftEye = Landmark(0.343f, 0.482f, 1.00f),
        rightEye = Landmark(0.312f, 0.484f, 1.00f),
        mouthLeft = Landmark(0.338f, 0.516f, 1.00f),
        mouthRight = Landmark(0.315f, 0.516f, 1.00f),
        leftShoulder = Landmark(0.403f, 0.590f, 1.00f),
        leftElbow = Landmark(0.417f, 0.711f, 0.91f),
        leftWrist = Landmark(0.474f, 0.762f, 0.86f),
        leftHip = Landmark(0.364f, 0.786f, 1.00f),
        rightShoulder = Landmark(0.249f, 0.589f, 1.00f),
        rightElbow = Landmark(0.222f, 0.704f, 0.90f),
        rightWrist = Landmark(0.180f, 0.748f, 0.88f),
        rightHip = Landmark(0.275f, 0.785f, 1.00f),
        leftKnee = Landmark(0.471f, 0.797f, 0.98f),
        rightKnee = Landmark(0.170f, 0.786f, 0.99f),
        leftAnkle = Landmark(0.275f, 0.859f, 0.78f),
        rightAnkle = Landmark(0.361f, 0.867f, 0.90f),
    )

    /** Standing, seen from the side (A_standing_young_man_naked_and_viewed_in_full...). */
    private val standingPhoto = BodyPose(
        nose = Landmark(0.382f, 0.241f, 1.00f),
        leftEye = Landmark(0.400f, 0.226f, 1.00f),
        rightEye = Landmark(0.366f, 0.225f, 1.00f),
        mouthLeft = Landmark(0.395f, 0.257f, 1.00f),
        mouthRight = Landmark(0.371f, 0.256f, 1.00f),
        leftShoulder = Landmark(0.450f, 0.317f, 1.00f),
        leftElbow = Landmark(0.459f, 0.425f, 0.98f),
        leftWrist = Landmark(0.460f, 0.528f, 0.96f),
        leftHip = Landmark(0.411f, 0.495f, 1.00f),
        rightShoulder = Landmark(0.319f, 0.305f, 1.00f),
        rightElbow = Landmark(0.303f, 0.413f, 0.92f),
        rightWrist = Landmark(0.292f, 0.514f, 0.89f),
        rightHip = Landmark(0.341f, 0.489f, 1.00f),
        leftKnee = Landmark(0.403f, 0.658f, 0.97f),
        rightKnee = Landmark(0.347f, 0.655f, 0.98f),
        leftAnkle = Landmark(0.405f, 0.796f, 0.98f),
        rightAnkle = Landmark(0.352f, 0.797f, 0.98f),
    )

    /** Lying flat on the stomach on a bed (Home_Care_Bed_Stomach-lying_Position.png). */
    private val lyingFlatPhoto = BodyPose(
        nose = Landmark(0.442f, 0.409f, 1.00f),
        leftEye = Landmark(0.430f, 0.379f, 1.00f),
        rightEye = Landmark(0.422f, 0.398f, 1.00f),
        mouthLeft = Landmark(0.477f, 0.403f, 1.00f),
        mouthRight = Landmark(0.469f, 0.421f, 1.00f),
        leftShoulder = Landmark(0.635f, 0.348f, 1.00f),
        leftElbow = Landmark(0.775f, 0.535f, 0.91f),
        leftWrist = Landmark(0.558f, 0.564f, 0.82f),
        leftHip = Landmark(1.028f, 0.356f, 1.00f),
        rightShoulder = Landmark(0.607f, 0.425f, 1.00f),
        rightElbow = Landmark(0.722f, 0.552f, 0.39f),
        rightWrist = Landmark(0.519f, 0.561f, 0.48f),
        rightHip = Landmark(1.014f, 0.394f, 1.00f),
        leftKnee = Landmark(1.312f, 0.432f, 0.92f),
        rightKnee = Landmark(1.312f, 0.419f, 0.52f),
        leftAnkle = Landmark(1.554f, 0.371f, 0.90f),
        rightAnkle = Landmark(1.573f, 0.347f, 0.71f),
    )

    /** The founder's hands plank with the phone upright on the bed 40 cm from his face: the model's output for that frame. Hips guessed behind the torso with full confidence, knees and ankles not seen. */
    private val closeHandsPlankFrame = BodyPose(
        nose = Landmark(0.300f, 0.403f, 1.00f),
        leftEye = Landmark(0.347f, 0.357f, 1.00f),
        rightEye = Landmark(0.241f, 0.359f, 1.00f),
        mouthLeft = Landmark(0.354f, 0.437f, 1.00f),
        mouthRight = Landmark(0.290f, 0.453f, 1.00f),
        leftShoulder = Landmark(0.504f, 0.370f, 1.00f),
        leftElbow = Landmark(0.578f, 0.544f, 0.96f),
        leftWrist = Landmark(0.623f, 0.718f, 0.96f),
        leftHip = Landmark(0.430f, 0.555f, 1.00f),
        rightShoulder = Landmark(0.170f, 0.462f, 1.00f),
        rightElbow = Landmark(0.134f, 0.655f, 0.99f),
        rightWrist = Landmark(0.144f, 0.827f, 0.98f),
        rightHip = Landmark(0.276f, 0.583f, 1.00f),
        leftKnee = Landmark(0.452f, 0.610f, 0.08f),
        rightKnee = Landmark(0.315f, 0.680f, 0.18f),
        leftAnkle = Landmark(0.422f, 0.646f, 0.11f),
        rightAnkle = Landmark(0.328f, 0.703f, 0.25f),
    )

    /** The founder sitting on the bed leaning over the same phone: the frame the old head-on rule timed as a plank. */
    private val closeSittingFrame = BodyPose(
        nose = Landmark(0.301f, 0.434f, 1.00f),
        leftEye = Landmark(0.353f, 0.370f, 1.00f),
        rightEye = Landmark(0.255f, 0.369f, 1.00f),
        mouthLeft = Landmark(0.342f, 0.459f, 1.00f),
        mouthRight = Landmark(0.272f, 0.458f, 1.00f),
        leftShoulder = Landmark(0.538f, 0.476f, 1.00f),
        leftElbow = Landmark(0.663f, 0.764f, 0.94f),
        leftWrist = Landmark(0.666f, 1.022f, 0.80f),
        leftHip = Landmark(0.434f, 0.774f, 0.96f),
        rightShoulder = Landmark(0.112f, 0.447f, 1.00f),
        rightElbow = Landmark(0.036f, 0.678f, 0.77f),
        rightWrist = Landmark(0.027f, 0.864f, 0.56f),
        rightHip = Landmark(0.252f, 0.772f, 0.94f),
        leftKnee = Landmark(0.446f, 0.782f, 0.29f),
        rightKnee = Landmark(0.217f, 0.811f, 0.13f),
        leftAnkle = Landmark(0.392f, 0.888f, 0.14f),
        rightAnkle = Landmark(0.293f, 0.877f, 0.09f),
    )

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

    private val lyingFlat = body(0.3f to 0.7f, 0.55f to 0.7f, 0.68f to 0.7f, 0.8f to 0.7f)
    private val standing = body(0.5f to 0.2f, 0.5f to 0.5f, 0.5f to 0.7f, 0.5f to 0.9f)
    private val sittingLegsOut = body(0.3f to 0.3f, 0.35f to 0.62f, 0.6f to 0.66f, 0.8f to 0.7f)
    private val saggingHips = body(0.3f to 0.5f, 0.55f to 0.68f, 0.68f to 0.64f, 0.8f to 0.66f)
    private val wallSit = body(0.4f to 0.3f, 0.4f to 0.55f, 0.6f to 0.55f, 0.6f to 0.8f)

    @Test fun `a straight body sloping down to the feet is a plank`() {
        assertTrue(HoldPositions.plankBody(plank))
        assertTrue(HoldPositions.plank(plank))
    }

    @Test fun `lying flat, standing, sitting and sagging are not a plank`() {
        assertFalse(HoldPositions.plank(lyingFlat))
        assertFalse(HoldPositions.plank(standing))
        assertFalse(HoldPositions.plank(sittingLegsOut))
        assertFalse(HoldPositions.plank(saggingHips))
    }

    @Test fun `photographed from the side, planks on the hands and on the forearms are planks`() {
        assertTrue(HoldPositions.plankBody(sidePlankPhoto))
        assertTrue(HoldPositions.plank(sidePlankPhoto))
        assertTrue(HoldPositions.plank(forearmPlankPhoto))
    }

    @Test fun `photographed from ahead and off to one side, a plank is still a plank`() {
        // A straight line is a straight line from wherever the camera looks.
        assertTrue(HoldPositions.plank(diagonalPlankPhoto))
    }

    @Test fun `a sphinx and a cobra, chest propped up and hips on the floor, are not a plank`() {
        assertTrue(HoldPositions.plankBody(sphinxPhoto))
        assertFalse(HoldPositions.plank(sphinxPhoto))
        // The lowest cobra in the set, chest barely off the floor: the hips are still under the line.
        assertFalse(HoldPositions.plank(lowCobraPhoto))
    }

    @Test fun `lying flat, sitting cross-legged and standing are not a plank`() {
        assertFalse(HoldPositions.plank(lyingFlatPhoto))
        assertFalse(HoldPositions.plank(crossLeggedPhoto))
        assertFalse(HoldPositions.plank(standingPhoto))
        assertFalse(HoldPositions.plank(lyingFlat))
        assertFalse(HoldPositions.plank(standing))
        assertFalse(HoldPositions.plank(sittingLegsOut))
    }

    @Test fun `on all fours, in child's pose and kneeling up are not a plank`() {
        // On all fours from a phone on the floor: shoulders and hips level, knees under the hips,
        // so the hip is a quarter of the line above it and the knee well below.
        assertFalse(HoldPositions.plank(body(0.3f to 0.4f, 0.55f to 0.4f, 0.55f to 0.62f, 0.75f to 0.66f)))
        // Child's pose: hips high on the heels, shoulders down by the floor.
        assertFalse(HoldPositions.plank(body(0.3f to 0.62f, 0.55f to 0.45f, 0.62f to 0.62f, 0.75f to 0.64f)))
        // Kneeling up: the line is near vertical.
        assertFalse(HoldPositions.plank(body(0.5f to 0.2f, 0.5f to 0.5f, 0.5f to 0.7f, 0.65f to 0.72f)))
    }

    @Test fun `a plank with the hips a little high or a little low is still a plank, and a sag is not`() {
        // Hips lifted a touch, a beginner's plank: 0.08 of the line above it.
        assertTrue(HoldPositions.plank(body(0.3f to 0.5f, 0.55f to 0.54f, 0.68f to 0.62f, 0.8f to 0.66f)))
        // Hips a touch low: 0.03 below.
        assertTrue(HoldPositions.plank(body(0.3f to 0.5f, 0.55f to 0.595f, 0.68f to 0.62f, 0.8f to 0.66f)))
        assertFalse(HoldPositions.plank(saggingHips))
    }

    @Test fun `lying flat with the arms on the body line is not a plank, however the line slopes`() {
        // A body flat on the floor seen from an angle that draws its line at 10 degrees: the elbows,
        // on the line, say it is on the floor.
        val flat = body(0.3f to 0.5f, 0.55f to 0.545f, 0.68f to 0.57f, 0.8f to 0.59f)
            .copy(leftElbow = Landmark(0.25f, 0.51f, 0.9f))
        assertFalse(HoldPositions.plank(flat))
        // The same line with an elbow hanging a third of the line's length under it is held up.
        assertTrue(HoldPositions.plank(flat.copy(leftElbow = Landmark(0.28f, 0.66f, 0.9f))))
        // Seen at a steep angle, so the line runs down the picture at 45 degrees, an arm lying
        // along the body puts its elbow well below the shoulder in the picture and still on the
        // line: not held up. The same elbow square to the line is.
        val steep = body(0.3f to 0.3f, 0.45f to 0.45f, 0.55f to 0.55f, 0.65f to 0.65f)
        assertFalse(HoldPositions.plank(steep.copy(leftElbow = Landmark(0.42f, 0.42f, 0.9f))))
        assertTrue(HoldPositions.plank(steep.copy(leftElbow = Landmark(0.25f, 0.42f, 0.9f))))
        // An elbow the model sees on the far side, hung off a shoulder it only guessed at, says
        // nothing either way: the near arm decides.
        assertTrue(HoldPositions.plank(plank.copy(rightElbow = Landmark(0.55f, 0.48f, 0.9f))))
        assertFalse(HoldPositions.plank(flat.copy(rightElbow = Landmark(0.28f, 0.9f, 0.9f))))
        // No arm seen whole: a body that has not shown it is held up is not timed, however straight
        // and sloping its line.
        assertFalse(HoldPositions.plank(plank.copy(leftElbow = BodyPose.UNSEEN)))
        assertFalse(HoldPositions.plank(plank.copy(leftElbow = BodyPose.UNSEEN, rightElbow = Landmark(0.55f, 0.7f, 0.9f))))
    }

    @Test fun `the phone stood close in front of the face is not a body to judge, plank or no plank`() {
        // The founder's own frames: a hands plank and, from the same phone, sitting on the bed
        // leaning over it. The model finds hips for both, sure of them, a torso under the shoulders;
        // it marks the knees and ankles unseen. Neither is judged.
        assertFalse(HoldPositions.plankBody(closeHandsPlankFrame))
        assertFalse(HoldPositions.plankBody(closeSittingFrame))
        assertFalse(HoldPositions.plank(closeHandsPlankFrame))
        assertFalse(HoldPositions.plank(closeSittingFrame))
        val judge = judge()
        judge.feed(closeSittingFrame, 0, 1_000)
        assertEquals(PoseJudge.Phase.NO_BODY, judge.phase)
        assertEquals(0L, judge.heldMillis)
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

    @Test fun `a whole side is a body for the plank, and a face with its shoulders is not`() {
        assertTrue(HoldPositions.plankBody(plank))
        assertTrue(HoldPositions.plankBody(standing))
        assertFalse(HoldPositions.plankBody(plank.copy(leftAnkle = BodyPose.UNSEEN)))
        assertFalse(HoldPositions.wallSitBody(closeSittingFrame))
        assertTrue(HoldPositions.wallSitBody(wallSitFront))
        assertTrue(HoldPositions.wallSitBody(wallSit))
        // Head-on with one foot out of the picture: not yet a body to judge, whatever the other side shows.
        assertFalse(HoldPositions.wallSitBody(wallSitFront.copy(rightAnkle = BodyPose.UNSEEN)))
        assertFalse(HoldPositions.wallSitBody(wallSitFront.copy(leftKnee = BodyPose.UNSEEN)))
        // Side-on, the far side is guessed through the body and is not asked for.
        assertTrue(HoldPositions.wallSitBody(wallSit.copy(rightKnee = BodyPose.UNSEEN, rightAnkle = BodyPose.UNSEEN)))
    }

    @Test fun `a plank the other way round in the picture is still a plank`() {
        val mirrored = body(0.7f to 0.5f, 0.45f to 0.58f, 0.32f to 0.62f, 0.2f to 0.66f)
        assertTrue(HoldPositions.plank(mirrored))
    }

    @Test fun `legs out of the picture is no plank at all`() {
        val noLegs = body(0.3f to 0.5f, 0.55f to 0.58f, 0.68f to 0.62f, 0.8f to 0.66f, visibility = 0.95f)
            .copy(leftAnkle = BodyPose.UNSEEN, leftKnee = BodyPose.UNSEEN)
        assertFalse(HoldPositions.plank(noLegs))
        assertFalse(HoldPositions.plankBody(noLegs))
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
        hasBody = HoldPositions::plankBody,
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
