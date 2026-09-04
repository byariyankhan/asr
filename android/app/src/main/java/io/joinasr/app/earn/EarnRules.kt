package io.joinasr.app.earn

/**
 * The two ways to earn more time, and what each costs.
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

    /** Figma 21: "Focus for 20 min". */
    const val FOCUS_MINUTES = 20

    /** What either one is worth. Figma 21 and 24: "+10 minutes". */
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
}
