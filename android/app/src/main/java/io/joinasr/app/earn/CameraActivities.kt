package io.joinasr.app.earn

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
    /** Every how many units the phone ticks and pulses. */
    val tickEvery: Int,
    /** The numbered steps on the placement card. */
    val placement: List<String>,
    /** The line under the steps. */
    val placementNote: String,
    private val judge: () -> PoseJudge,
) {
    fun newJudge(): PoseJudge = judge()
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
        placementLine = "Phone propped on its side, a few steps away",
        verification = "Your phone finds your shoulders, hips and ankles in each frame and runs " +
            "the clock while they make a straight line. The frame is then dropped.",
        timed = true,
        tickEvery = 5,
        placement = listOf(
            "Prop the phone on its side a few steps away, low, with your whole body in the picture.",
            "Get into a plank, forearms or hands. The clock runs while you hold it and stops when you drop.",
        ),
        placementNote = "Rest if you need to. The seconds you have done are kept; the clock picks up when you are back in position.",
        judge = {
            HoldJudge(
                position = HoldPositions::plank,
                hasBody = HoldPositions::hasBody,
                coach = { phase, _ ->
                    when (phase) {
                        PoseJudge.Phase.NO_BODY ->
                            "Looking for you" to "Prop the phone on its side a few steps away, your whole body in the picture."
                        PoseJudge.Phase.NOT_IN_POSITION ->
                            "Get into a plank" to "Forearms or hands on the floor, body straight from shoulders to heels."
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
        placementLine = "Phone propped on its side, a few steps away",
        verification = "Your phone finds your shoulders, hips, knees and ankles in each frame and runs " +
            "the clock while your back is upright and your thighs are level. The frame is then dropped.",
        timed = true,
        tickEvery = 5,
        placement = listOf(
            "Prop the phone on its side a few steps away, with your whole body in the picture from the side.",
            "Back flat against a wall, slide down until your knees are at a right angle. The clock runs while you hold it.",
        ),
        placementNote = "Rest if you need to. The seconds you have done are kept; the clock picks up when you are back down.",
        judge = {
            HoldJudge(
                position = HoldPositions::wallSit,
                hasBody = HoldPositions::hasBody,
                coach = { phase, _ ->
                    when (phase) {
                        PoseJudge.Phase.NO_BODY ->
                            "Looking for you" to "Prop the phone on its side a few steps away, your whole body in the picture."
                        PoseJudge.Phase.NOT_IN_POSITION ->
                            "Slide down the wall" to "Back against the wall, thighs level with the floor, knees at a right angle."
                        else ->
                            "Hold it" to "Thighs level, back on the wall. The clock is running."
                    }
                },
            )
        },
    )
    else -> null
}
