package io.joinasr.app.earn

import java.util.Locale

/**
 * What the camera screen needs to know to run one activity: the judge
 * that watches the poses, the words for its units, and where the phone
 * goes. One entry per camera activity; the screen itself has no idea
 * which one it is showing.
 */
class CameraSpec(
    /** The activity's type ([EarnRules.PUSHUPS] and friends). */
    val type: String,
    /** "push-ups", "seconds": what the count is of, on the screen. */
    val noun: String,
    /** The ask, at the top of the permission screen: "Count your push-ups." */
    val permissionTitle: String,
    /** The label beside the target number on the permission screen: "PUSH-UPS", "SECONDS". */
    val unitLabel: String,
    /** Where the phone goes, as one line, on the permission screen. */
    val placementLine: String,
    /** How the counting is done, in a sentence or two, on the permission screen. */
    val verification: String,
    /** True when the units are seconds held rather than reps. */
    val timed: Boolean,
    /**
     * True when the target has to be done in one go: a break, or leaving
     * the screen, starts the count over. False when a break pauses it and
     * the units done so far are kept.
     */
    val continuous: Boolean = false,
    /** Every how many units the phone ticks and pulses. */
    val tickEvery: Int,
    /** The numbered steps on the placement card. */
    val placement: List<String>,
    /** The line under the steps. */
    val placementNote: String,
    private val judge: () -> PoseJudge,
) {
    fun newJudge(): PoseJudge = judge()

    /** [units] as the screen says them: "6" push-ups, "32" seconds, "4:15" of a meditation. */
    fun format(units: Int): String =
        if (timed && EarnRules.targetFor(type) >= 60) String.format(Locale.US, "%d:%02d", units / 60, units % 60) else "$units"

    /** Under the live count: "of 7 push-ups", "of 45 seconds", "of 7 minutes, in one sitting". */
    val ofTarget: String
        get() {
            val target = EarnRules.targetFor(type)
            return if (timed && target >= 60 && target % 60 == 0) {
                "of ${target / 60} minutes" + if (continuous) ", in one $session" else ""
            } else {
                "of $target $noun"
            }
        }

    /** What is left, as the reward card's title: "3 to go", "12 seconds to go", "4:15 to go". */
    fun toGo(remaining: Int): String = when {
        timed && EarnRules.targetFor(type) >= 60 -> "${format(remaining)} to go"
        timed -> "$remaining seconds to go"
        else -> "$remaining to go"
    }

    /** What one go at it is called on the exits: a "set" of push-ups, a "sitting" of a meditation. */
    val session: String get() = if (continuous) "sitting" else "set"

    /** The target on the permission screen, with the label for it: "7" and "PUSH-UPS", "7" and "MINUTES". */
    val targetShown: Pair<String, String>
        get() {
            val target = EarnRules.targetFor(type)
            return if (timed && target >= 60 && target % 60 == 0) "${target / 60}" to "MINUTES" else "$target" to unitLabel
        }
}

/** The spec for a camera activity, or null for one the camera has no part in. */
fun cameraSpec(type: String): CameraSpec? = when (type) {
    EarnRules.PUSHUPS -> CameraSpec(
        type = type,
        noun = "push-ups",
        permissionTitle = "Count your push-ups.",
        unitLabel = "PUSH-UPS",
        placementLine = "Phone on the floor, camera looking up at you",
        verification = "Your phone finds your face and shoulders in each frame and counts " +
            "each time you come down to it and back up. The frame is then dropped.",
        timed = false,
        tickEvery = 1,
        placement = listOf(
            "On the floor, screen up, a hand's width in front of your hands. The camera looks up at you.",
            "Keep your face in the picture. You will hear a tick for every push-up that counts.",
        ),
        placementNote = "Prefer a side view? Prop it on its side a few steps away with your whole body in frame.",
        judge = { PushUpJudge() },
    )
    EarnRules.PLANK -> CameraSpec(
        type = type,
        noun = "seconds",
        permissionTitle = "Time your plank.",
        unitLabel = "SECONDS",
        placementLine = "Phone upright on the floor, a step ahead of your hands",
        verification = "Your phone finds your face, shoulders and hips in each frame and runs the " +
            "clock while your head is down in line with your shoulders and your hips are up behind " +
            "them. The frame is then dropped.",
        timed = true,
        tickEvery = 5,
        placement = listOf(
            "Stand the phone upright on the floor about a step ahead of where your hands go, screen facing you. Lean it on a wall, a book, a water bottle.",
            "Get into a plank facing it, forearms or hands, head down in line with your back. The clock runs while you hold it and stops when you drop.",
        ),
        placementNote = "Rest if you need to. The seconds you have done are kept; the clock picks up when you are back in position. " +
            "A phone propped on its side across the room, seeing your whole body, works too.",
        judge = {
            HoldJudge(
                position = HoldPositions::plank,
                hasBody = HoldPositions::hasBody,
                coach = { phase, _ ->
                    when (phase) {
                        PoseJudge.Phase.NO_BODY ->
                            "Looking for you" to "Stand the phone upright on the floor a step ahead of your hands, facing you."
                        PoseJudge.Phase.NOT_IN_POSITION ->
                            "Get into a plank" to "Forearms or hands on the floor, head down in line with your back, hips up behind you."
                        else ->
                            "Hold it" to "Hips level, body straight. The clock is running."
                    }
                },
            )
        },
    )
    EarnRules.WALL_SIT -> CameraSpec(
        type = type,
        noun = "seconds",
        permissionTitle = "Time your wall sit.",
        unitLabel = "SECONDS",
        placementLine = "Phone upright on the floor, a couple of steps in front of you",
        verification = "Your phone finds your shoulders, hips, knees and ankles in each frame and runs " +
            "the clock while your back is upright, your thighs are level and your shins drop straight " +
            "to your feet. The frame is then dropped.",
        timed = true,
        tickEvery = 5,
        placement = listOf(
            "Stand the phone upright on the floor a couple of steps in front of the wall, screen facing you, with you in the picture from shoulders to feet.",
            "Back flat against the wall, slide down until your thighs are level and your knees are over your feet. The clock runs while you hold it.",
        ),
        placementNote = "Rest if you need to. The seconds you have done are kept; the clock picks up when you are back down. " +
            "A phone propped on its side to see you from the side works too.",
        judge = {
            HoldJudge(
                position = HoldPositions::wallSit,
                hasBody = HoldPositions::hasBody,
                coach = { phase, _ ->
                    when (phase) {
                        PoseJudge.Phase.NO_BODY ->
                            "Looking for you" to "Stand the phone upright on the floor in front of you, shoulders to feet in the picture."
                        PoseJudge.Phase.NOT_IN_POSITION ->
                            "Slide down the wall" to "Back on the wall, thighs level, knees over your feet, and all of you from shoulders to feet in the picture."
                        else ->
                            "Hold it" to "Thighs level, back on the wall. The clock is running."
                    }
                },
            )
        },
    )
    EarnRules.MEDITATION -> CameraSpec(
        type = type,
        noun = "seconds",
        permissionTitle = "Time your meditation.",
        unitLabel = "SECONDS",
        placementLine = "Phone upright in front of you, a couple of steps away",
        verification = "Your phone finds your head, shoulders, hips and knees in each frame and runs the " +
            "clock while you sit upright, facing it, and keep still. Standing does not count. The frame is then dropped.",
        timed = true,
        continuous = true,
        tickEvery = 60,
        placement = listOf(
            "Stand the phone upright in front of you, a couple of steps away and no higher than your chest, with you in the picture from head to knees.",
            "Sit cross-legged on the floor or on a chair, facing it, and be still. The clock runs while you are; eyes can close, and a tick marks each minute.",
        ),
        placementNote = "It has to be ${EarnRules.MEDITATION_SECONDS / 60} minutes in one sitting. A scratch or a cough is fine; " +
            "getting up, or leaving this screen, starts the clock over.",
        judge = { MeditationJudge() },
    )
    else -> null
}
