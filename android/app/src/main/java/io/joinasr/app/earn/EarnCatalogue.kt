package io.joinasr.app.earn

import java.util.Locale

/**
 * Which drawing stands for an activity on the chooser. One entry per
 * activity, drawn in ui/components/AsrIcons.kt; an enum rather than a
 * composable here so this file stays plain Kotlin.
 */
enum class EarnIcon { WALK, FOCUS, PUSH_UPS, PLANK, WALL_SIT, RUN, STAIRS, MEDITATION }

/**
 * One way to earn time, as the chooser describes it.
 *
 * Everything the tile and its detail sheet say about an activity is here,
 * so adding a fourth kind is one entry in [earnOptions] plus its icon and
 * its rule; the screen does not change. The type string is the server's
 * name ([EarnRules.WALK] and friends), which is also what the view model
 * starts.
 */
data class EarnOption(
    val type: String,
    /** On the tile: one or two words. */
    val name: String,
    /** On the sheet: the whole ask. */
    val title: String,
    /** On the sheet, under the title: the number the ask comes to. */
    val target: String,
    val icon: EarnIcon,
    /** What to do, in a sentence or two. */
    val explanation: String,
    /** How the phone knows it happened. */
    val verification: String,
    /** What is and is not collected, when a sensor is involved. */
    val privacy: String?,
    val recommended: Boolean,
    /** Why this phone cannot offer it, or null when it can. */
    val unavailableReason: String?,
    /** The first line of the receipt: "Your walk is complete." */
    val done: String,
) {
    val available: Boolean get() = unavailableReason == null
}

/** The catalogue entry for a type, however the phone is equipped, or null for a type the chooser does not list. */
fun earnOptionFor(type: String): EarnOption? =
    earnOptions(stepsAvailable = true, cameraAvailable = true, barometerAvailable = true)
        .firstOrNull { it.type == type }

/**
 * The activities this build can actually run, in the order the chooser
 * shows them. Only these exist; nothing is listed that is not built.
 *
 * Reward and target come from [EarnRules], the same constants the pact
 * locks and the view model starts with, so the sheet cannot promise a
 * price the activity does not pay.
 */
fun earnOptions(stepsAvailable: Boolean, cameraAvailable: Boolean, barometerAvailable: Boolean): List<EarnOption> = listOf(
    EarnOption(
        type = EarnRules.WALK,
        name = "Walk",
        title = "Walk ${"%.1f".format(Locale.US, EarnRules.kilometresFor(EarnRules.WALK_STEPS))} km",
        target = "≈ ${String.format(Locale.US, "%,d", EarnRules.WALK_STEPS)} steps",
        icon = EarnIcon.WALK,
        explanation = "Go for a walk. You can lock your phone or leave the app; the steps keep " +
            "counting until the goal is reached.",
        verification = "Your phone's own step counter, read from the moment you start. " +
            "Distance is estimated from steps.",
        privacy = "No location or GPS. No movement history: only the difference in the step " +
            "count since you started.",
        recommended = stepsAvailable,
        unavailableReason = if (stepsAvailable) null else {
            "This phone has no step counter, so a walk cannot be measured."
        },
        done = "Your walk is complete.",
    ),
    EarnOption(
        type = EarnRules.FOCUS,
        name = "Phone down",
        title = "Put your phone down for ${EarnRules.FOCUS_MINUTES} minutes",
        target = "${EarnRules.FOCUS_MINUTES} minutes locked, without a break",
        icon = EarnIcon.FOCUS,
        explanation = "Lock your phone and spend some time in the real world. Unlocking it " +
            "before the time is up starts the ${EarnRules.FOCUS_MINUTES} minutes over.",
        verification = "The lock screen. Calls and notifications that arrive over it do not " +
            "count against you; unlocking does.",
        privacy = "Only whether the phone is locked, and that stays on the phone. Nothing " +
            "about what is on it. Your witnesses and Asr's server learn that the session " +
            "was completed, not when you unlocked.",
        recommended = false,
        unavailableReason = null,
        done = "Your phone-free session is complete.",
    ),
    EarnOption(
        type = EarnRules.PUSHUPS,
        name = "Push-ups",
        title = "Do ${EarnRules.PUSHUP_REPS} push-ups",
        target = "${EarnRules.PUSHUP_REPS} push-ups, counted on camera",
        icon = EarnIcon.PUSH_UPS,
        explanation = "Put your phone on the floor, screen up, just ahead of your hands, and " +
            "do them. You will hear a tick for each one that counts.",
        verification = "The front camera and an on-device pose model: each time your face " +
            "comes down to the phone and back up is one.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
        recommended = false,
        unavailableReason = if (cameraAvailable) null else {
            "This phone has no camera, so push-ups cannot be counted."
        },
        done = "Your push-ups are done.",
    ),
    EarnOption(
        type = EarnRules.PLANK,
        name = "Plank",
        title = "Hold a plank for ${EarnRules.PLANK_SECONDS} seconds",
        target = "${EarnRules.PLANK_SECONDS} seconds, timed on camera",
        icon = EarnIcon.PLANK,
        explanation = "Prop your phone on its side a few steps away and get into a plank, on your " +
            "forearms or your hands. The clock runs while you hold it. Rest if you need to: " +
            "the seconds you have done are kept.",
        verification = "The front camera and an on-device pose model: the clock runs while your " +
            "shoulders, hips and ankles make a straight line.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
        recommended = false,
        unavailableReason = if (cameraAvailable) null else {
            "This phone has no camera, so a plank cannot be timed."
        },
        done = "Your plank is done.",
    ),
    EarnOption(
        type = EarnRules.WALL_SIT,
        name = "Wall sit",
        title = "Hold a wall sit for ${EarnRules.WALL_SIT_SECONDS} seconds",
        target = "${EarnRules.WALL_SIT_SECONDS} seconds, timed on camera",
        icon = EarnIcon.WALL_SIT,
        explanation = "Back against a wall, slide down until your knees are at a right angle, and " +
            "hold it. Prop your phone on its side a few steps away, looking at you from the side. " +
            "Rest if you need to: the seconds you have done are kept.",
        verification = "The front camera and an on-device pose model: the clock runs while your " +
            "back is upright and your thighs are level with the floor.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
        recommended = false,
        unavailableReason = if (cameraAvailable) null else {
            "This phone has no camera, so a wall sit cannot be timed."
        },
        done = "Your wall sit is done.",
    ),
    EarnOption(
        type = EarnRules.RUN,
        name = "Run",
        title = "Run ${String.format(Locale.US, "%,d", EarnRules.RUN_STEPS)} steps",
        target = "${String.format(Locale.US, "%,d", EarnRules.RUN_STEPS)} steps at a running pace, about six minutes",
        icon = EarnIcon.RUN,
        explanation = "Go for a run with the phone on you. Only steps at a running pace count, so a " +
            "walk to the park is not part of it. You can lock the phone and put it away.",
        verification = "Your phone's own step counter, read in twenty-second stretches: a stretch at " +
            "140 steps a minute or more is running, and its steps count. No GPS.",
        privacy = "No location or GPS. No route: only the pace of your steps, on the phone, and " +
            "the count that came of it.",
        recommended = false,
        unavailableReason = if (stepsAvailable) null else {
            "This phone has no step counter, so a run cannot be measured."
        },
        done = "Your run is done.",
    ),
    EarnOption(
        type = EarnRules.STAIRS,
        name = "Stairs",
        title = "Climb ${EarnRules.STAIR_FLOORS} floors",
        target = "${EarnRules.STAIR_FLOORS} floors, on foot",
        icon = EarnIcon.STAIRS,
        explanation = "Take the stairs, with the phone on you. Floors add up across the day until " +
            "you have ${EarnRules.STAIR_FLOORS}; going down and taking the lift count for nothing.",
        verification = "Your phone's barometer, which feels the air thin as you climb, read together " +
            "with the step counter: a rise made while stepping is a climb, a rise without steps is a lift.",
        privacy = "No location or GPS. Only air pressure and steps, on the phone, and the floors " +
            "that came of them.",
        recommended = false,
        unavailableReason = when {
            !stepsAvailable -> "This phone has no step counter, so a climb cannot be measured."
            !barometerAvailable -> "This phone has no barometer, so a climb cannot be measured."
            else -> null
        },
        done = "Your climb is done.",
    ),
    EarnOption(
        type = EarnRules.MEDITATION,
        name = "Meditate",
        title = "Breathe for ${EarnRules.MEDITATION_SECONDS / 60} minutes",
        target = "${EarnRules.MEDITATION_SECONDS / 60} minutes with the phone lying still",
        icon = EarnIcon.MEDITATION,
        explanation = "Put the phone down where you can see it, sit, and follow the breathing " +
            "guide: in for four, out for six. A small tap marks each turn, so your eyes can " +
            "close. Pick the phone up and the clock pauses; put it back and it goes on.",
        verification = "The motion sensor: the clock runs while the phone lies still with the " +
            "guide on the screen. It cannot know whether you meditated; it knows the phone " +
            "was put down and left.",
        privacy = "Only whether the phone moved, on the phone. Nothing is recorded and nothing " +
            "about the session leaves the phone but that it was completed.",
        recommended = false,
        unavailableReason = null,
        done = "Your ten minutes are done.",
    ),
)
