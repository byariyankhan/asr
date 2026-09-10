package io.joinasr.app.earn

import java.util.Locale

/**
 * Which drawing stands for an activity on the chooser. One entry per
 * activity, drawn in ui/components/AsrIcons.kt; an enum rather than a
 * composable here so this file stays plain Kotlin.
 */
enum class EarnIcon { WALK, FOCUS, PUSH_UPS }

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
) {
    val available: Boolean get() = unavailableReason == null
}

/**
 * The activities this build can actually run, in the order the chooser
 * shows them. Only these three exist; nothing is listed that is not built.
 *
 * Reward and target come from [EarnRules], the same constants the pact
 * locks and the view model starts with, so the sheet cannot promise a
 * price the activity does not pay.
 */
fun earnOptions(stepsAvailable: Boolean, cameraAvailable: Boolean): List<EarnOption> = listOf(
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
    ),
)
