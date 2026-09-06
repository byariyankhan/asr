package io.joinasr.app.enforcement

import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.limits.DailyLimit
import kotlinx.serialization.Serializable

/** One app under a limit, with the name to show when it is blocked. */
@Serializable
data class PactApp(
    val packageName: String,
    val label: String,
    val limitMinutes: Int,
    /**
     * The local day (YYYY-MM-DD) this app was added to a running challenge,
     * or null for one the challenge started with. Enforcement ignores it:
     * an added app counts against the whole of today from the moment it is
     * added. The progress screen reads it, so the days before the app was
     * under a limit are not judged by that limit.
     */
    val addedOn: String? = null,
)

/**
 * The commitment itself: which apps, how long each gets per day, and when
 * the person started.
 *
 * The whole point of the product is that this is hard to walk away from, so
 * it is one immutable value. Nothing edits a field of it; the one change it
 * takes is being replaced whole by the server's copy after an app is added
 * ([PactViewModel.addApp]), and that change only ever tightens it -- an app
 * joins, no app leaves, no limit moves. Anything else means a new pact,
 * deliberately made, which is what "limits lock when your challenge starts"
 * has to mean if it is to mean anything.
 *
 * [version] is here from the first release rather than added when it is
 * first needed, because the day it is needed is the day somebody's live
 * challenge would otherwise fail to load.
 */
@Serializable
data class Pact(
    val apps: List<PactApp>,
    val startedAtMillis: Long,
    /**
     * How many days it runs for. Defaulted only so a pact written before
     * this field existed still loads rather than being thrown away; every
     * pact the app writes now carries what the person actually chose on
     * Figma 04.
     */
    val durationDays: Int = ChallengeDuration.DEFAULT_DAYS,
    val version: Int = CURRENT_VERSION,
) {
    val limitsByPackage: Map<String, Int>
        get() = apps.associate { it.packageName to it.limitMinutes }

    fun appFor(packageName: String): PactApp? = apps.firstOrNull { it.packageName == packageName }

    /**
     * For each app added after the start, the first instant of the local
     * day it came in on -- what [io.joinasr.app.challenge.WeeklyProgress]
     * needs to leave the days before it unjudged. Apps the challenge
     * started with are absent. A day that cannot be read is treated as
     * "from the start", which judges more rather than less.
     */
    fun judgedFrom(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Map<String, Long> =
        apps.mapNotNull { app ->
            val day = app.addedOn ?: return@mapNotNull null
            val start = runCatching {
                java.time.LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: return@mapNotNull null
            app.packageName to start
        }.toMap()

    /**
     * Whether this is something the enforcement loop can act on. An empty
     * pact is not an error to shout about, it is simply nothing to enforce,
     * and a limit outside the ladder means something wrote a value no screen
     * in the app can produce.
     */
    val isEnforceable: Boolean
        get() = apps.isNotEmpty() &&
            durationDays >= ChallengeDuration.MINIMUM_DAYS &&
            durationDays <= ChallengeDuration.MAXIMUM_DAYS &&
            apps.all {
                it.packageName.isNotBlank() &&
                    it.limitMinutes >= DailyLimit.MINIMUM_MINUTES &&
                    it.limitMinutes <= DailyLimit.MAXIMUM_MINUTES
            }

    companion object {
        const val CURRENT_VERSION = 1

        /** From what the setup flow collected, at the moment it is committed. */
        fun from(
            apps: List<io.joinasr.app.apps.AppEntry>,
            limits: Map<String, Int>,
            durationDays: Int,
            startedAtMillis: Long,
        ) = Pact(
            apps = apps.map {
                PactApp(
                    packageName = it.packageName,
                    label = it.label,
                    limitMinutes = limits[it.packageName] ?: DailyLimit.DEFAULT_MINUTES,
                )
            },
            startedAtMillis = startedAtMillis,
            durationDays = ChallengeDuration.clamp(durationDays),
        )
    }
}
