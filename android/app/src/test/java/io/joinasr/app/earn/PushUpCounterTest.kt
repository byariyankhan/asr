package io.joinasr.app.earn

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PushUpCounterTest {

    /**
     * A body seen from the left side: shoulder at the left of the frame,
     * hip to the right, elbow below the shoulder, and the wrist placed so
     * the elbow makes [elbowDegrees]. [torsoDegrees] tilts the hip up
     * from horizontal, 0 being a flat plank and 90 standing.
     */
    private fun pose(
        elbowDegrees: Double,
        torsoDegrees: Double = 0.0,
        leftVisibility: Float = 0.95f,
        rightVisibility: Float = 0.2f,
        hipVisibility: Float = 0.95f,
    ): PushUpPose {
        val shoulder = Landmark(0.3f, 0.5f, leftVisibility)
        val torso = Math.toRadians(torsoDegrees)
        val hip = Landmark(
            (0.3 + 0.5 * cos(torso)).toFloat(),
            (0.5 - 0.5 * sin(torso)).toFloat(),
            hipVisibility,
        )
        val elbow = Landmark(shoulder.x, shoulder.y + 0.15f, leftVisibility)
        val bend = Math.toRadians(elbowDegrees)
        // Elbow-to-shoulder points straight up; rotate it by the elbow
        // angle to place the wrist.
        val wrist = Landmark(
            (elbow.x + 0.15 * sin(bend)).toFloat(),
            (elbow.y - 0.15 * cos(bend)).toFloat(),
            leftVisibility,
        )
        val ghost = Landmark(0.5f, 0.5f, rightVisibility)
        // A face in profile: the near eye seen, the far one behind the
        // nose and reported with the low visibility the model gives an
        // occluded point. Without both eyes there is no face to measure,
        // so a side view is never judged by the front rule.
        val eye = Landmark(0.2f, 0.45f, 0.9f)
        return PushUpPose(
            nose = Landmark(0.15f, 0.47f, 0.9f),
            leftEye = eye, rightEye = eye.copy(x = 0.22f, visibility = 0.3f),
            mouthLeft = Landmark(0.19f, 0.5f, 0.9f), mouthRight = Landmark(0.2f, 0.5f, 0.9f),
            leftShoulder = shoulder, leftElbow = elbow, leftWrist = wrist, leftHip = hip,
            rightShoulder = ghost, rightElbow = ghost, rightWrist = ghost, rightHip = ghost,
        )
    }

    /**
     * The phone on the floor looking up: a face whose eyes are [eyeGap]
     * apart in the picture and whose mouth is [eyesToMouth] below them
     * (the same as the gap unless said otherwise, which is roughly a
     * face), shoulders half-visible, no hips at all.
     */
    private fun face(
        eyeGap: Float,
        eyeVisibility: Float = 0.95f,
        eyesToMouth: Float = eyeGap,
    ): PushUpPose {
        val left = Landmark(0.5f - eyeGap / 2, 0.4f, eyeVisibility)
        val right = Landmark(0.5f + eyeGap / 2, 0.4f, eyeVisibility)
        val shoulder = Landmark(0.5f, 0.9f, 0.6f)
        val gone = Landmark(0.5f, 1.2f, 0.05f)
        return PushUpPose(
            nose = Landmark(0.5f, 0.5f, eyeVisibility),
            leftEye = left, rightEye = right,
            mouthLeft = Landmark(0.48f, 0.4f + eyesToMouth, eyeVisibility),
            mouthRight = Landmark(0.52f, 0.4f + eyesToMouth, eyeVisibility),
            leftShoulder = shoulder, leftElbow = gone, leftWrist = gone, leftHip = gone,
            rightShoulder = shoulder, rightElbow = gone, rightWrist = gone, rightHip = gone,
        )
    }

    /** Feeds [frames] identical poses [gapMillis] apart, returning how many reps completed. */
    private fun PushUpCounter.hold(pose: PushUpPose?, from: Long, frames: Int = 3, gapMillis: Long = 50): Int =
        (0 until frames).count { observe(pose, from + it * gapMillis) }

    @Test fun `straight, bent, straight is one push-up`() {
        val counter = PushUpCounter()
        assertEquals(0, counter.hold(pose(175.0), 0))
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        assertEquals(0, counter.hold(pose(80.0), 1_000))
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(pose(175.0), 2_000))
        assertEquals(1, counter.reps)
    }

    @Test fun `seven push-ups count seven`() {
        val counter = PushUpCounter()
        var at = 0L
        counter.hold(pose(170.0), at)
        repeat(7) {
            at += 1_000
            counter.hold(pose(85.0), at)
            at += 1_000
            counter.hold(pose(170.0), at)
        }
        assertEquals(7, counter.reps)
    }

    @Test fun `starting from the floor does not count the first rise`() {
        val counter = PushUpCounter()
        assertEquals(0, counter.hold(pose(80.0), 0))
        assertEquals(0, counter.hold(pose(175.0), 1_000))
        assertEquals(0, counter.reps)
        assertEquals(0, counter.hold(pose(80.0), 2_000))
        assertEquals(1, counter.hold(pose(175.0), 3_000))
    }

    @Test fun `a half push-up earns nothing`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        counter.hold(pose(120.0), 1_000)
        counter.hold(pose(175.0), 2_000)
        assertEquals(0, counter.reps)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
    }

    @Test fun `flicker across the up threshold is not a rep`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        counter.hold(pose(80.0), 1_000)
        // One frame straight, one frame bent, one frame straight: nothing
        // has held long enough to be believed.
        assertFalse(counter.observe(pose(175.0), 2_000))
        assertFalse(counter.observe(pose(80.0), 2_050))
        assertFalse(counter.observe(pose(175.0), 2_100))
        assertEquals(0, counter.reps)
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
    }

    @Test fun `bouncing faster than a push-up takes is one push-up`() {
        val counter = PushUpCounter(minRepMillis = 600)
        counter.hold(pose(175.0), 0, gapMillis = 10)
        counter.hold(pose(80.0), 100, gapMillis = 10)
        assertEquals(1, counter.hold(pose(175.0), 200, gapMillis = 10))
        counter.hold(pose(80.0), 300, gapMillis = 10)
        assertEquals(0, counter.hold(pose(175.0), 400, gapMillis = 10))
        counter.hold(pose(80.0), 500, gapMillis = 10)
        assertEquals(1, counter.hold(pose(175.0), 900, gapMillis = 10))
        assertEquals(2, counter.reps)
    }

    @Test fun `standing up and bending the arms is not a push-up`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0, torsoDegrees = 85.0), 0)
        assertEquals(PushUpCounter.Phase.NOT_IN_POSITION, counter.phase)
        counter.hold(pose(80.0, torsoDegrees = 85.0), 1_000)
        counter.hold(pose(175.0, torsoDegrees = 85.0), 2_000)
        assertEquals(0, counter.reps)
    }

    @Test fun `a slightly raised plank still counts`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0, torsoDegrees = 20.0), 0)
        counter.hold(pose(80.0, torsoDegrees = 20.0), 1_000)
        assertEquals(1, counter.hold(pose(175.0, torsoDegrees = 20.0), 2_000))
    }

    @Test fun `hips out of the picture means nobody is in position`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0, hipVisibility = 0.1f), 0)
        assertEquals(PushUpCounter.Phase.NO_BODY, counter.phase)
        counter.hold(pose(80.0, hipVisibility = 0.1f), 1_000)
        counter.hold(pose(175.0, hipVisibility = 0.1f), 2_000)
        assertEquals(0, counter.reps)
    }

    @Test fun `losing the body mid-rep drops that rep and needs a fresh start`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        counter.hold(pose(80.0), 1_000)
        counter.hold(null, 2_000)
        assertEquals(PushUpCounter.Phase.NO_BODY, counter.phase)
        assertEquals(0, counter.hold(pose(175.0), 3_000))
        counter.hold(pose(80.0), 4_000)
        assertEquals(1, counter.hold(pose(175.0), 5_000))
    }

    @Test fun `a single dropped frame does not lose the rep`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        counter.hold(pose(80.0), 1_000)
        assertFalse(counter.observe(null, 1_500))
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(pose(175.0), 2_000))
    }

    @Test fun `the arm the model can see decides`() {
        val counter = PushUpCounter()
        // Left arm barely visible, right arm (the ghost point) is a dot:
        // nothing usable on either side.
        counter.hold(pose(175.0, leftVisibility = 0.3f, rightVisibility = 0.2f), 0)
        assertEquals(PushUpCounter.Phase.NO_BODY, counter.phase)
    }

    @Test fun `from the floor, the face coming close and going back is one push-up`() {
        val counter = PushUpCounter()
        assertEquals(0, counter.hold(face(0.10f), 0))
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        assertEquals(0, counter.hold(face(0.18f), 1_000))
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(face(0.10f), 2_000))
        assertEquals(1, counter.reps)
    }

    @Test fun `from the floor, seven push-ups count seven wherever the phone lies`() {
        val counter = PushUpCounter()
        var at = 0L
        counter.hold(face(0.07f), at)
        repeat(7) {
            at += 1_000
            counter.hold(face(0.14f), at)
            at += 1_000
            counter.hold(face(0.07f), at)
        }
        assertEquals(7, counter.reps)
    }

    @Test fun `a nod towards the phone is not a push-up`() {
        val counter = PushUpCounter()
        counter.hold(face(0.10f), 0)
        counter.hold(face(0.12f), 1_000)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        assertEquals(0, counter.hold(face(0.10f), 2_000))
    }

    @Test fun `from the floor, starting at the bottom counts from the next rep`() {
        val counter = PushUpCounter()
        counter.hold(face(0.18f), 0)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        // Rising: the baseline follows the face away, nothing is owed.
        assertEquals(0, counter.hold(face(0.10f), 1_000))
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        counter.hold(face(0.18f), 2_000)
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(face(0.10f), 3_000))
    }

    @Test fun `holding the bottom for seconds means the phone moved, and rebaselines`() {
        val counter = PushUpCounter()
        counter.hold(face(0.10f), 0)
        counter.hold(face(0.18f), 1_000)
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        // Still "down" five seconds later: this is the new up, uncounted.
        assertEquals(0, counter.hold(face(0.18f), 6_000))
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        assertEquals(0, counter.reps)
        // And the set continues from there.
        counter.hold(face(0.30f), 7_000)
        assertEquals(1, counter.hold(face(0.18f), 8_000))
    }

    @Test fun `switching views mid-rep drops the rep`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        counter.hold(pose(80.0), 1_000)
        assertEquals(PushUpCounter.View.SIDE, counter.view)
        counter.hold(face(0.18f), 2_000)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        assertEquals(0, counter.hold(face(0.10f), 3_000))
        assertEquals(0, counter.reps)
    }

    @Test fun `looking at the floor, not the phone, still counts`() {
        val counter = PushUpCounter()
        // A bowed head foreshortens the eyes-to-mouth line; across the
        // eyes it is unchanged, and that is the measure that holds.
        counter.hold(face(0.10f, eyesToMouth = 0.05f), 0)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        counter.hold(face(0.18f, eyesToMouth = 0.09f), 1_000)
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(face(0.10f, eyesToMouth = 0.05f), 2_000))
    }

    @Test fun `turning the head aside is not a push-up, and does not spoil the next one`() {
        val counter = PushUpCounter()
        counter.hold(face(0.10f), 0)
        // Turned 50 degrees: the eyes close up in the picture, the mouth
        // stays where it was below them. The face is no bigger.
        counter.hold(face(0.06f, eyesToMouth = 0.10f), 1_000)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        // Turned back: no closer than before, so nothing happens...
        assertEquals(0, counter.hold(face(0.10f), 2_000))
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        // ...and the next real rep counts against the same baseline.
        counter.hold(face(0.18f), 3_000)
        assertEquals(1, counter.hold(face(0.10f), 4_000))
    }

    @Test fun `a mouth flickering in and out of view is not a push-up`() {
        val counter = PushUpCounter()
        val turned = face(0.06f, eyesToMouth = 0.10f)
        val turnedNoMouth = turned.copy(
            mouthLeft = turned.mouthLeft.copy(visibility = 0.1f),
            mouthRight = turned.mouthRight.copy(visibility = 0.1f),
        )
        counter.hold(turned, 0)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        // The mouth drops out of the model's confidence and comes back,
        // twice: the face has not moved, and the count must not either.
        repeat(2) { round ->
            counter.hold(turnedNoMouth, 1_000L + round * 2_000)
            assertEquals(PushUpCounter.Phase.UP, counter.phase)
            assertEquals(0, counter.hold(turned, 2_000L + round * 2_000))
            assertEquals(PushUpCounter.Phase.UP, counter.phase)
        }
        assertEquals(0, counter.reps)
        // And a real rep afterwards still counts.
        counter.hold(face(0.11f, eyesToMouth = 0.18f), 6_000)
        assertEquals(PushUpCounter.Phase.DOWN, counter.phase)
        assertEquals(1, counter.hold(turned, 7_000))
    }

    @Test fun `a face with no mouth in view is measured across the eyes`() {
        val counter = PushUpCounter()
        val eyesOnly = { gap: Float ->
            face(gap).let {
                it.copy(mouthLeft = it.mouthLeft.copy(visibility = 0.1f), mouthRight = it.mouthRight.copy(visibility = 0.1f))
            }
        }
        counter.hold(eyesOnly(0.10f), 0)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        counter.hold(eyesOnly(0.18f), 1_000)
        assertEquals(1, counter.hold(eyesOnly(0.10f), 2_000))
    }

    @Test fun `a face on its side, the phone laid the other way, is still a face`() {
        val counter = PushUpCounter()
        fun sideways(gap: Float) = face(gap).let {
            it.copy(
                leftEye = Landmark(0.5f, 0.4f - gap / 2, 0.95f),
                rightEye = Landmark(0.5f, 0.4f + gap / 2, 0.95f),
                nose = Landmark(0.6f, 0.4f, 0.95f),
                mouthLeft = Landmark(0.5f + gap, 0.38f, 0.95f),
                mouthRight = Landmark(0.5f + gap, 0.42f, 0.95f),
            )
        }
        counter.hold(sideways(0.10f), 0)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        counter.hold(sideways(0.18f), 1_000)
        assertEquals(1, counter.hold(sideways(0.10f), 2_000))
    }

    @Test fun `a face is the floor view even if the model guesses an upright arm and hip`() {
        val counter = PushUpCounter()
        // From the floor the body is foreshortened into something the
        // model may still call an arm and a torso, standing upright.
        val guessed = face(0.10f).let {
            val point = Landmark(0.5f, 0.9f, 0.7f)
            it.copy(
                leftShoulder = point, leftElbow = point.copy(y = 1.0f), leftWrist = point.copy(y = 1.1f),
                leftHip = point.copy(y = 1.3f),
            )
        }
        counter.hold(guessed, 0)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
    }

    @Test fun `re-propping the phone between reps arms the new view`() {
        val counter = PushUpCounter()
        counter.hold(pose(175.0), 0)
        assertEquals(PushUpCounter.Phase.UP, counter.phase)
        // Same "up", other view: the first rep in the new view must count.
        counter.hold(face(0.10f), 1_000)
        assertEquals(PushUpCounter.View.FRONT, counter.view)
        counter.hold(face(0.18f), 2_000)
        assertEquals(1, counter.hold(face(0.10f), 3_000))
    }

    @Test fun `a face the model is unsure of is nobody`() {
        val counter = PushUpCounter()
        counter.hold(face(0.10f, eyeVisibility = 0.2f), 0)
        assertEquals(PushUpCounter.View.NONE, counter.view)
        assertEquals(PushUpCounter.Phase.NO_BODY, counter.phase)
    }

    @Test fun `normalised points are stretched back to the picture's aspect ratio`() {
        // A straight arm pointing down in a 4:3 frame: in normalised
        // space the vertical run is scaled by 3/4 relative to horizontal,
        // which an angle check must undo before it can call it straight.
        val points = MutableList(PushUpPose.LANDMARK_COUNT) { Landmark(0f, 0f, 0f) }
        points[PushUpPose.NOSE] = Landmark(0.30f, 0.32f, 1f)
        points[PushUpPose.LEFT_EYE] = Landmark(0.28f, 0.30f, 1f)
        points[PushUpPose.MOUTH_LEFT] = Landmark(0.29f, 0.36f, 1f)
        points[PushUpPose.MOUTH_RIGHT] = Landmark(0.31f, 0.36f, 1f)
        points[PushUpPose.RIGHT_EYE] = Landmark(0.32f, 0.30f, 1f)
        points[PushUpPose.LEFT_SHOULDER] = Landmark(0.30f, 0.40f, 1f)
        points[PushUpPose.LEFT_ELBOW] = Landmark(0.30f, 0.55f, 1f)
        points[PushUpPose.LEFT_WRIST] = Landmark(0.36f, 0.70f, 1f)
        points[PushUpPose.LEFT_HIP] = Landmark(0.60f, 0.40f, 1f)
        val pose = PushUpPose.fromNormalised(points, aspectRatio = 4f / 3f)!!
        assertEquals(0.40f, pose.leftShoulder.x, 1e-6f)
        assertEquals(0.40f, pose.leftShoulder.y, 1e-6f)
        assertEquals(0.80f, pose.leftHip.x, 1e-6f)
        assertEquals(0.28f * 4f / 3f, pose.leftEye.x, 1e-6f)
        assertNull(PushUpPose.fromNormalised(points.take(10), 1f))
    }
}
