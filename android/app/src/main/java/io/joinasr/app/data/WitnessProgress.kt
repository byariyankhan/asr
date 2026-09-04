package io.joinasr.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One app under a limit, as a witness is allowed to see it.
 *
 * [minutesUsed] is null when the phone has not sent today's summary yet, and
 * that is a different fact from zero: zero means they have not opened the
 * app, null means we have not heard from them today. Showing "0 / 20 min"
 * for the second would be inventing good news.
 */
@Serializable
data class ProgressApp(
    val label: String,
    @SerialName("package") val packageName: String,
    @SerialName("limit_min") val limitMinutes: Int,
    @SerialName("minutes_used") val minutesUsed: Int? = null,
    @SerialName("earned_min") val earnedMinutes: Int = 0,
) {
    /** Where the bar sits, 0f..1f. Null when there is nothing to draw. */
    val fraction: Float?
        get() {
            val used = minutesUsed ?: return null
            val limit = limitMinutes + earnedMinutes
            if (limit <= 0) return 1f
            return (used.toFloat() / limit).coerceIn(0f, 1f)
        }

    val atLimit: Boolean
        get() = minutesUsed != null && minutesUsed >= limitMinutes + earnedMinutes
}

@Serializable
data class WithinLimits(val within: Int, val total: Int)

@Serializable
data class CurrentPactView(
    @SerialName("pact_id") val pactId: String,
    val day: Int,
    val of: Int,
    val status: String,
    val apps: List<ProgressApp> = emptyList(),
    @SerialName("apps_within_limits_today") val withinLimits: WithinLimits? = null,
)

/** One thing that happened, from the ledger. */
@Serializable
data class RemotePactEvent(
    val id: String,
    @SerialName("pact_id") val pactId: String,
    val type: String,
    val reason: String? = null,
    @SerialName("app_package") val appPackage: String? = null,
    val minutes: Int? = null,
    @SerialName("received_at") val receivedAt: String? = null,
)

/**
 * GET /v1/witnesses/{id}/progress — Figma 17, and the same shape as
 * /v1/me/progress, because a witness sees exactly what the person sees about
 * themselves and nothing more.
 */
@Serializable
data class WitnessProgress(
    val user: RemoteUser,
    val current: CurrentPactView? = null,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("longest_streak_days") val longestStreakDays: Int = 0,
    val completed: Int = 0,
    val broken: Int = 0,
    @SerialName("recent_events") val recentEvents: List<RemotePactEvent> = emptyList(),
)
