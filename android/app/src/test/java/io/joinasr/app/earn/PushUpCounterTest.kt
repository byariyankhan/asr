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
        return PushUpPose(
            leftShoulder = shoulder, leftElbow = elbow, leftWrist = wrist, leftHip = hip,
            rightShoulder = ghost, rightElbow = ghost, rightWrist = ghost, rightHip = ghost,
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

    @Test fun `normalised points are stretched back to the picture's aspect ratio`() {
        // A straight arm pointing down in a 4:3 frame: in normalised
        // space the vertical run is scaled by 3/4 relative to horizontal,
        // which an angle check must undo before it can call it straight.
        val points = MutableList(PushUpPose.LANDMARK_COUNT) { Landmark(0f, 0f, 0f) }
        points[PushUpPose.LEFT_SHOULDER] = Landmark(0.30f, 0.40f, 1f)
        points[PushUpPose.LEFT_ELBOW] = Landmark(0.30f, 0.55f, 1f)
        points[PushUpPose.LEFT_WRIST] = Landmark(0.36f, 0.70f, 1f)
        points[PushUpPose.LEFT_HIP] = Landmark(0.60f, 0.40f, 1f)
        val pose = PushUpPose.fromNormalised(points, aspectRatio = 4f / 3f)!!
        assertEquals(0.40f, pose.leftShoulder.x, 1e-6f)
        assertEquals(0.40f, pose.leftShoulder.y, 1e-6f)
        assertEquals(0.80f, pose.leftHip.x, 1e-6f)
        assertNull(PushUpPose.fromNormalised(points.take(10), 1f))
    }
}
