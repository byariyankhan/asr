package io.joinasr.app.earn

import kotlinx.serialization.Serializable

/**
 * An attempt to earn more time, in progress.
 *
 * One at a time, deliberately. Two running at once would mean somebody
 * walking while a focus timer runs, collecting twice for one twenty minutes,
 * and the point of the mechanism is that the time was paid for.
 *
 * [id] is made on the phone and is the server's id too, so starting the same
 * activity twice on a bad connection produces one row rather than two.
 */
@Serializable
data class EarnActivity(
    val id: String,
    /** [EarnRules.WALK] or [EarnRules.FOCUS]. */
    val type: String,
    val packageName: String,
    val appLabel: String,
    /** Steps for a walk, minutes for a focus session. */
    val target: Int,
    val rewardMinutes: Int,
    val startedAtMillis: Long,
    val deadlineAtMillis: Long,
    /**
     * The step counter's reading when this started. The sensor counts from
     * the last reboot and is shared by every app on the phone, so the only
     * usable figure is the difference from a baseline taken at the start.
     * -1 until the first sample arrives, which can take a moment.
     */
    val baselineSteps: Int = -1,
    /** Steps taken, or whole minutes focused. Never above [target] on screen. */
    val progress: Int = 0,
) {
    val isWalk: Boolean get() = type == EarnRules.WALK

    val fraction: Float
        get() = if (target <= 0) 1f else (progress.toFloat() / target).coerceIn(0f, 1f)

    val remaining: Int get() = (target - progress).coerceAtLeast(0)

    val isComplete: Boolean get() = progress >= target

    fun expired(nowMillis: Long): Boolean = nowMillis >= deadlineAtMillis
}

/** What has been earned today, and for which day. */
@Serializable
data class EarnedToday(
    /** Local date as YYYY-MM-DD. Anything else means the figures are stale. */
    val day: String,
    val minutesByPackage: Map<String, Int> = emptyMap(),
) {
    fun forPackage(packageName: String): Int = minutesByPackage[packageName] ?: 0
}
