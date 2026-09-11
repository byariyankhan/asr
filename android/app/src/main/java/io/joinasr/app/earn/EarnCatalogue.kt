package io.joinasr.app.earn

import java.util.Locale

/**
 * Which drawing stands for an activity on the chooser. One entry per
 * activity, drawn in ui/components/AsrIcons.kt; an enum rather than a
 * composable here so this file stays plain Kotlin.
 */
enum class EarnIcon { WALK, FOCUS, PUSH_UPS, PLANK, WALL_SIT, RUN, STAIRS, MEDITATION, RIDE }

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
    /** Why this phone cannot offer it, or null when it can. */
    val unavailableReason: String?,
    /** The first line of the receipt: "Your walk is complete." */
    val done: String,
) {
    val available: Boolean get() = unavailableReason == null
}

/** The catalogue entry for a type, however the phone is equipped, or null for a type the chooser does not list. */
fun earnOptionFor(type: String): EarnOption? =
    earnOptions(
        stepsAvailable = true,
        cameraAvailable = true,
        barometerAvailable = true,
        accelerometerAvailable = true,
        gpsAvailable = true,
    )
        .firstOrNull { it.type == type }

/**
 * The activities this build can actually run, in the order the chooser
 * shows them. Only these exist; nothing is listed that is not built.
 *
 * Reward and target come from [EarnRules], the same constants the pact
 * locks and the view model starts with, so the sheet cannot promise a
 * price the activity does not pay.
 */
fun earnOptions(
    stepsAvailable: Boolean,
    cameraAvailable: Boolean,
    barometerAvailable: Boolean,
    accelerometerAvailable: Boolean,
    gpsAvailable: Boolean,
): List<EarnOption> = listOf(
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
        explanation = "Stand your phone upright on the floor just ahead of your hands, " +
            "facing you, and get into a plank, on your forearms or your hands. The clock runs " +
            "while you hold it. Rest if you need to: the seconds you have done are kept.",
        verification = "The front camera and an on-device pose model: the clock runs while you " +
            "are up on your hands or forearms facing the phone with your hips behind you, not " +
            "lying down, standing, kneeling up or sitting.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
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
        explanation = "Stand your phone upright on the floor a couple of steps in front of the wall, " +
            "facing you. Back against the wall, slide down until your knees are at a right angle, and " +
            "hold it. Rest if you need to: the seconds you have done are kept.",
        verification = "The front camera and an on-device pose model: the clock runs while it sees you " +
            "from shoulders to feet with your back upright, your thighs level and your shins straight " +
            "down to your feet; standing or a half squat does not count.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
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
        unavailableReason = when {
            !stepsAvailable -> "This phone has no step counter, so a climb cannot be measured."
            !barometerAvailable -> "This phone has no barometer, so a climb cannot be measured."
            else -> null
        },
        done = "Your climb is done.",
    ),
    EarnOption(
        type = EarnRules.RIDE,
        name = "Cycle",
        title = "Cycle ${"%.0f".format(Locale.US, EarnRules.RIDE_METRES / 1000.0)} km",
        target = "${"%.0f".format(Locale.US, EarnRules.RIDE_METRES / 1000.0)} km at a cycling speed, by GPS",
        icon = EarnIcon.RIDE,
        explanation = "Go for a ride with the phone on you. Only distance at a cycling speed counts, " +
            "between 8 and 45 km/h, so walking the bike and a car do not. You can lock the phone.",
        verification = "GPS, while the ride is on, judged half a minute at a time: a bicycle's pace, " +
            "the shake of a bicycle on the motion sensor (a phone resting in a car earns nothing), no " +
            "car-like braking, no running on the step counter, no mock location. Each fix is then dropped.",
        privacy = "Location is read on the phone to measure the ride and never sent. No route or " +
            "place is saved; the server learns that the ride was completed, nothing about where.",
        unavailableReason = when {
            !gpsAvailable -> "This phone has no GPS, so a ride cannot be measured."
            !stepsAvailable -> "This phone has no step counter, which a ride needs to tell it from a run."
            !accelerometerAvailable -> "This phone has no motion sensor, which a ride needs to tell it from a car."
            else -> null
        },
        done = "Your ride is done.",
    ),
    EarnOption(
        type = EarnRules.MEDITATION,
        name = "Meditate",
        title = "Meditate for ${EarnRules.MEDITATION_SECONDS / 60} minutes",
        target = "${EarnRules.MEDITATION_SECONDS / 60} minutes sitting still, in one go, on camera",
        icon = EarnIcon.MEDITATION,
        explanation = "Stand the phone upright in front of you, sit cross-legged or on a chair, and be " +
            "still: eyes closed or open, as you like. The clock runs while the camera sees you sitting " +
            "upright and still, and it has to be ${EarnRules.MEDITATION_SECONDS / 60} minutes in one " +
            "sitting. A scratch or a cough is fine; getting up, or moving about, starts it over.",
        verification = "The front camera and an on-device pose model: the clock runs while it sees you " +
            "from head to knees, sitting, back upright, facing the phone and still. Standing does not " +
            "count. It cannot know whether you meditated; it knows you sat there.",
        privacy = "No photo or video is ever saved. Every frame is dropped after it is " +
            "judged, and nothing from the camera leaves the phone.",
        unavailableReason = if (cameraAvailable) null else {
            "This phone has no camera, so a meditation cannot be timed."
        },
        done = "Your meditation is done.",
    ),
)
