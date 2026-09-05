package io.joinasr.app.analytics

import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import io.joinasr.app.push.Push

/**
 * The product events, and the one place they are sent from.
 *
 * Ten events, enough to see whether people sign up, finish onboarding,
 * start challenges, invite witnesses, earn time, and finish or break what
 * they started. Each carries at most a couple of product facts -- how long
 * a challenge is, what kind of activity earned time, why a challenge broke
 * -- and never the apps somebody limits, their minutes, their name, their
 * email address, their witnesses or anything they typed. [AnalyticsTest]
 * holds every event to that: a parameter outside [ALLOWED_PARAMS] fails the
 * build.
 *
 * No user id is set, and the manifest removes the advertising id, so what
 * Firebase receives is an event with a random installation id, the app
 * version, the phone model, country and language -- which is what the
 * privacy policy says it receives. Guarded like Crash.kt: without
 * google-services.json Firebase never initialises and every call here is a
 * no-op.
 */
object Analytics {

    /** Every parameter an event may carry. The test refuses anything else. */
    internal val ALLOWED_PARAMS = setOf("method", "duration_days", "activity_type", "reason")

    data class Event(val name: String, val params: Map<String, Any> = emptyMap())

    fun signUp() = Event("sign_up", mapOf("method" to "email"))
    fun login() = Event("login", mapOf("method" to "email"))
    fun onboardingComplete() = Event("onboarding_complete")
    fun pactCreated(durationDays: Int) = Event("pact_created", mapOf("duration_days" to durationDays))
    fun pactStarted(durationDays: Int) = Event("pact_started", mapOf("duration_days" to durationDays))
    fun witnessInviteSent() = Event("witness_invite_sent")
    fun witnessInviteAccepted() = Event("witness_invite_accepted")
    fun extraTimeEarned(activityType: String) = Event("extra_time_earned", mapOf("activity_type" to activityType))
    fun challengeCompleted(durationDays: Int) = Event("challenge_completed", mapOf("duration_days" to durationDays))
    fun challengeBroken(reason: String, durationDays: Int) =
        Event("challenge_broken", mapOf("reason" to reason, "duration_days" to durationDays))

    /** The whole catalogue, with example values, so the test can hold every event to the rule. */
    internal fun catalogue(): List<Event> = listOf(
        signUp(),
        login(),
        onboardingComplete(),
        pactCreated(7),
        pactStarted(7),
        witnessInviteSent(),
        witnessInviteAccepted(),
        extraTimeEarned("walk_steps"),
        challengeCompleted(7),
        challengeBroken("user_gave_up", 7),
    )

    /** Sends one event. Never throws, never blocks, does nothing without Firebase. */
    fun log(event: Event) {
        if (!Push.configured) return
        runCatching {
            val context = FirebaseApp.getInstance().applicationContext
            val bundle = Bundle()
            for ((key, value) in event.params) {
                when (value) {
                    is Int -> bundle.putLong(key, value.toLong())
                    is Long -> bundle.putLong(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
            FirebaseAnalytics.getInstance(context).logEvent(event.name, bundle)
        }
    }
}
