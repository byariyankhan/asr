package com.joinasr.app.earn

/**
 * The ways to earn more time, and what each costs.
 *
 * These numbers are written into the pact when it starts and never read from
 * a request afterwards — the server takes the target and the reward from the
 * snapshot it locked, precisely so a phone cannot ask for a cheaper walk.
 * That is why they live here as constants rather than as anything editable:
 * the whole value of earned time is that it was earned at the price agreed
 * on the day the challenge began.
 */
object EarnRules {

    /** Figma 21: "Walk 2 km · ≈ 2,500 steps". */
    const val WALK_STEPS = 2_500

    /** Phone-free session: twenty uninterrupted minutes with the keyguard showing. */
    const val FOCUS_MINUTES = 20

    /**
     * Push-ups, counted by the camera from a side view. Seven is the
     * founder's price: roughly the effort of the walk for somebody who would
     * rather not go outside, and few enough that the camera session is over
     * in under a minute.
     */
    const val PUSHUP_REPS = 7

    /**
     * A plank and a wall sit, timed by the camera. Forty-five seconds each:
     * the easy end of the list, on purpose, for the person who would give
     * the challenge up if every way to earn were a walk.
     */
    const val PLANK_SECONDS = 45
    const val WALL_SIT_SECONDS = 45

    /**
     * A run: steps taken at a running cadence, a thousand of them, which is
     * six or seven minutes of jogging. Cadence is the whole of the proof;
     * there is no GPS in this app.
     */
    const val RUN_STEPS = 1_000

    /** A climb: floors, from the barometer while the step counter moves. */
    const val STAIR_FLOORS = 10

    /**
     * A meditation: seven minutes sitting still in front of the camera, in
     * one go. Kept in seconds on the phone, like the holds, so the count on
     * the screen moves; the server's rule is in minutes.
     */
    const val MEDITATION_SECONDS = 7 * 60

    /** A ride: metres at a cycling speed, from GPS. Three kilometres is ten minutes or so on a bicycle. */
    const val RIDE_METRES = 3_000

    /** What any one of them is worth. Figma 21 and 24: "+10 minutes". */
    const val REWARD_MINUTES = 10

    /** The most that can be earned for one app in a day. */
    const val DAILY_CAP_MINUTES = 30

    /**
     * How far a step is taken to be, in metres.
     *
     * Figma's own numbers: 2,500 steps is "≈ 2 km" and 1,240 is "~1.0 km".
     * Both come out at roughly 0.8, which is a reasonable average stride and
     * is why every distance in this app is prefixed with a tilde. Nothing
     * here measures distance; it estimates it from steps, and says so.
     */
    const val METRES_PER_STEP = 0.8

    /** An activity has to be finished the same day it was started. */
    const val DEADLINE_HOURS = 12L

    fun kilometresFor(steps: Int): Double = steps * METRES_PER_STEP / 1000.0

    /** What the server calls them. */
    const val WALK = "walk_steps"
    const val FOCUS = "focus_session"
    const val PUSHUPS = "push_ups"
    const val PLANK = "plank"
    const val WALL_SIT = "wall_sit"

    const val RUN = "run_steps"
    const val STAIRS = "stairs"
    const val MEDITATION = "meditation"
    const val RIDE = "cycling"

    /** The activities the camera counts or times, on the camera screen. */
    val CAMERA_TYPES: Set<String> = setOf(PUSHUPS, PLANK, WALL_SIT, MEDITATION)

    /** The activities the foreground service measures from the motion sensors, screen on or off. */
    val MOTION_TYPES: Set<String> = setOf(RUN, STAIRS)

    /** Everything that reads the step counter, and so needs ACTIVITY_RECOGNITION on API 29+. */
    val STEP_TYPES: Set<String> = setOf(WALK) + MOTION_TYPES

    fun targetFor(type: String): Int = when (type) {
        WALK -> WALK_STEPS
        PUSHUPS -> PUSHUP_REPS
        PLANK -> PLANK_SECONDS
        WALL_SIT -> WALL_SIT_SECONDS
        RUN -> RUN_STEPS
        STAIRS -> STAIR_FLOORS
        MEDITATION -> MEDITATION_SECONDS
        RIDE -> RIDE_METRES
        else -> FOCUS_MINUTES
    }
}
