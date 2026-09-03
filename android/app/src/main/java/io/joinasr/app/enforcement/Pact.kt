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
)

/**
 * The commitment itself: which apps, how long each gets per day, and when
 * the person started.
 *
 * The whole point of the product is that this is hard to walk away from, so
 * it is one immutable value written once. Nothing edits a field of it. A
 * change means a new pact, deliberately made, which is what "limits lock
 * when your challenge starts" has to mean if it is to mean anything.
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
