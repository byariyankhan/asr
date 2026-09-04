package io.joinasr.app.sync

import android.content.Context
import android.os.Build
import io.joinasr.app.BuildConfig
import io.joinasr.app.data.Api
import io.joinasr.app.data.ActivityCreate
import io.joinasr.app.data.ActivityRule
import io.joinasr.app.data.ActivityRules
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.DeviceRegistration
import io.joinasr.app.data.EventCreate
import io.joinasr.app.data.PactCreate
import io.joinasr.app.data.PactSnapshot
import io.joinasr.app.data.RemotePact
import io.joinasr.app.data.SnapshotApp
import io.joinasr.app.data.SummaryApp
import io.joinasr.app.data.SummaryCreate
import io.joinasr.app.earn.EarnActivity
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.push.Push
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Telling the server what happened here.
 *
 * The rule this is built on: the phone is the truth and the network is an
 * afterthought. Enforcement never waits for a request, a challenge starts
 * whether or not anything reaches the server, and a breach is recorded
 * locally the moment it is detected. This class exists so that the people
 * the person invited eventually find out — which is the whole product, and
 * still must not be on the path of a limit being applied.
 *
 * Everything here is therefore best-effort and idempotent. Events carry an
 * id made when they happened, so sending one twice is the same as sending it
 * once, and a phone that was in flight mode for a week reports a week-old
 * breach with the time it actually occurred.
 */
class Sync(context: Context) {

    private val app = context.applicationContext
    private val store = SyncStore(app)
    private val tokens = Api.tokens(app)

    /**
     * Registers this install if it has not been, and returns the server's
     * device id. Null when there is no token or the request failed; both are
     * ordinary conditions and neither is worth an error on screen.
     */
    suspend fun deviceId(): String? {
        store.deviceId()?.let { return it }
        val token = tokens.current() ?: return null
        val registration = DeviceRegistration(
            installId = store.installId(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            osVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
            // Sent with the very first registration rather than in a second
            // request afterwards. Every notification in this product is a
            // push, so a device row without a token is a person nobody can
            // reach -- the watchdog marks their notifications unregistered
            // and drops them.
            fcmToken = Push.token(app),
        )
        val result = Api.devices.register(token, registration)
        return when (result) {
            is ApiResult.Ok -> result.value.id.also {
                store.saveDeviceId(it)
                store.savePushToken(registration.fcmToken)
            }
            else -> null
        }
    }

    /**
     * Sends a push token the moment Firebase issues a new one.
     *
     * Not left until the next app start. Between a token rotating and the
     * next launch is exactly the window in which a pact might break, and a
     * witness told about it a day later has been told about a different
     * thing.
     */
    suspend fun registerPushToken(pushToken: String) {
        if (store.pushToken() == pushToken) return
        val token = tokens.current() ?: return
        val device = deviceId() ?: return
        val sent = Api.devices.heartbeat(
            token = token,
            deviceId = device,
            protectionEnabled = Permissions.canDrawOverlays(app),
            appVersion = BuildConfig.VERSION_NAME,
            fcmToken = pushToken,
        )
        if (sent is ApiResult.Ok) store.savePushToken(pushToken)
    }

    /**
     * Whether the server's active pact is this phone's, by when it started.
     *
     * A pact with no `starts_at` is one from a build that did not send the
     * field; it is not identifiable, and refusing to adopt it is the safe
     * direction -- the worst case is a challenge whose witnesses hear
     * nothing, rather than one whose witnesses hear about somebody else.
     */
    private fun mine(remote: RemotePact, pact: Pact): Boolean {
        val started = parseInstantMillis(remote.startsAt) ?: return false
        return kotlin.math.abs(started - pact.startedAtMillis) <= ADOPTION_WINDOW_MS
    }

    /**
     * The challenge this account is running, rebuilt on a phone that does
     * not have it.
     *
     * The pact lived only in this app's own storage, written in one place
     * and read from nowhere else, so it belonged to an install rather than
     * to a person. Delete the app and reinstall it, replace the handset,
     * sign in on a second phone -- in every one of those the challenge was
     * simply gone, while the server still had it and the witnesses were
     * still watching something nothing was enforcing.
     *
     * A commitment cannot be a property of a handset. This is where it comes
     * back from.
     *
     * Null when there is nothing to restore, when there is no signal, or
     * when the server's copy is missing the snapshot -- which is every pact
     * created before the phone started sending one. Those are unrecoverable
     * and saying so by returning null is better than rebuilding a challenge
     * with no apps in it.
     */
    suspend fun restorePact(): Pact? {
        val token = tokens.current() ?: return null
        val remote = (Api.pacts.current(token) as? ApiResult.Ok)?.value ?: return null
        if (remote.status != null && remote.status != "active") return null
        val snapshot = remote.snapshot ?: return null
        val startedAt = parseInstantMillis(remote.startsAt) ?: return null
        val apps = snapshot.apps.map {
            PactApp(
                packageName = it.packageName,
                label = it.label,
                limitMinutes = it.dailyLimitMinutes,
            )
        }
        if (apps.isEmpty()) return null

        val pact = Pact(
            apps = apps,
            startedAtMillis = startedAt,
            durationDays = remote.durationDays ?: ChallengeDuration.DEFAULT_DAYS,
        )
        // The id first, keyed to the start time this pact will be stored
        // with, so events reported the moment enforcement begins already
        // know where to go.
        store.saveRemotePact(remote.id, pact.startedAtMillis)
        // And say out loud that this phone is the one enforcing it now.
        // Best effort: a challenge that is running here matters more than
        // the server's record of which handset it is running on, and the
        // next heartbeat is another chance.
        deviceId()?.let { device ->
            runCatching { Api.pacts.claim(token, remote.id, device) }
        }
        return pact
    }

    /**
     * The server's id for [pact], creating it if this phone has not managed
     * to yet.
     *
     * A 409 means a pact already exists on the account, and there are two
     * ways that happens. Either this phone's own create landed and only the
     * answer was lost -- in which case adopting the active pact is exactly
     * right, and is how a challenge started on a flaky connection still ends
     * up with witnesses. Or a previous challenge ended offline and the event
     * that would have closed it is still in the outbox -- in which case the
     * active pact is the *old* one, and adopting it would file this
     * challenge's breaches against the last challenge.
     *
     * An empty outbox is what tells the two apart, so it is the condition
     * for adopting. When there is something queued, this gives up and
     * returns null; the drain closes the old pact, and the next attempt
     * creates this one properly.
     *
     * And a third way, which used to be swallowed by the first: the active
     * pact belongs to *another phone* signed into the same account. Adopting
     * it there is silently reporting this phone's breaches against a
     * challenge somebody set up on a different handset, with different apps
     * and different limits, and never sending this one's snapshot at all.
     *
     * So the start times have to agree. This phone's own create, answered or
     * not, produces a server pact whose `starts_at` is when the request
     * landed -- the same moment, give or take the round trip. Another
     * phone's is any time at all. [ADOPTION_WINDOW_MS] is wide enough for a
     * slow request and far narrower than the gap between two people
     * committing separately.
     */
    suspend fun remotePactId(pact: Pact): String? {
        store.remotePactId(pact.startedAtMillis)?.let { return it }
        val token = tokens.current() ?: return null
        val device = deviceId() ?: return null

        val body = PactCreate(
            deviceId = device,
            durationDays = pact.durationDays,
            timezone = ZoneId.systemDefault().id,
            snapshot = PactSnapshot(
                apps = pact.apps.map {
                    SnapshotApp(
                        packageName = it.packageName,
                        label = it.label,
                        dailyLimitMinutes = it.limitMinutes,
                    )
                },
                // Locked in with the challenge. The server reads the target
                // and the reward from here rather than from the request that
                // starts a walk, so the price of earning time cannot be
                // renegotiated by the phone halfway through.
                activities = ActivityRules(
                    walkSteps = ActivityRule(
                        rewardMinutes = EarnRules.REWARD_MINUTES,
                        dailyCapMinutes = EarnRules.DAILY_CAP_MINUTES,
                        target = EarnRules.WALK_STEPS,
                    ),
                    focusSession = ActivityRule(
                        rewardMinutes = EarnRules.REWARD_MINUTES,
                        dailyCapMinutes = EarnRules.DAILY_CAP_MINUTES,
                        targetMinutes = EarnRules.FOCUS_MINUTES,
                    ),
                ),
            ),
        )
        val created = Api.pacts.create(token, body)
        val id = when {
            created is ApiResult.Ok -> created.value.id
            created is ApiResult.Failure && created.code == 409 && store.pending().isEmpty() ->
                (Api.pacts.current(token) as? ApiResult.Ok)?.value?.takeIf { mine(it, pact) }?.id
            else -> null
        } ?: return null
        store.saveRemotePact(id, pact.startedAtMillis)
        return id
    }

    /** Queues an event and tries to send it now. Safe to call from the loop. */
    suspend fun report(pact: Pact, event: PendingEvent) {
        store.enqueue(event)
        drain(pact)
    }

    /**
     * Sends everything queued, oldest first, and stops at the first one that
     * does not go through — so a phone that comes back on a train does not
     * report a breach before the challenge it belongs to.
     *
     * A refusal from the server drops the event rather than retrying it. A
     * 409 on a closed pact and a 400 on a body this build sends wrongly are
     * both permanent, and an outbox that keeps a doomed event forever stops
     * being an outbox and becomes a stuck queue.
     */
    suspend fun drain(pact: Pact?) {
        val queued = store.pending()
        if (queued.isEmpty()) return
        val token = tokens.current() ?: return
        val pactId = pact?.let { remotePactId(it) } ?: return

        for (event in queued.sortedBy { it.occurredAtMillis }) {
            val result = Api.pacts.postEvent(
                token = token,
                pactId = pactId,
                event = EventCreate(
                    id = event.id,
                    type = event.type,
                    reason = event.reason,
                    appPackage = event.appPackage,
                    minutes = event.minutes,
                    occurredAt = iso(event.occurredAtMillis),
                ),
            )
            when (result) {
                is ApiResult.Ok -> store.drop(event.id)
                is ApiResult.Failure -> {
                    // 401 and 429 will work later; everything else the server
                    // refuses, it will refuse again.
                    if (result.code == 401 || result.code == 429) return
                    store.drop(event.id)
                }
                is ApiResult.Offline -> return
            }
        }
    }

    /**
     * Tells the server whether protection is actually working here. Called
     * where the answer is already known rather than guessed, because a
     * heartbeat that always says true is worse than none: it is what a
     * witness would be trusting.
     */
    suspend fun heartbeat(protectionEnabled: Boolean) {
        val token = tokens.current() ?: return
        val device = deviceId() ?: return
        // The token rides along on every heartbeat. onNewToken is the fast
        // path and this is the safety net: a rotation that happened while
        // the app was uninstalled from Play services' point of view, or a
        // registration whose answer was lost, is repaired within six hours
        // instead of never.
        val pushToken = Push.token(app)
        val sent = Api.devices.heartbeat(
            token = token,
            deviceId = device,
            protectionEnabled = protectionEnabled,
            appVersion = BuildConfig.VERSION_NAME,
            fcmToken = pushToken,
        )
        if (sent is ApiResult.Ok && pushToken != null) store.savePushToken(pushToken)
    }

    /**
     * Sends today's usage figures for [pact].
     *
     * This is what makes a witness's screen show numbers rather than only
     * the moment something broke. It carries only the apps the person chose
     * to limit, and only their totals -- never what else is on the phone,
     * and never anything under an app they did not put in the challenge.
     */
    suspend fun sendSummary(pact: Pact, minutesByPackage: Map<String, Int>) {
        val token = tokens.current() ?: return
        val pactId = remotePactId(pact) ?: return
        val apps = pact.apps.map {
            SummaryApp(
                packageName = it.packageName,
                minutesUsed = (minutesByPackage[it.packageName] ?: 0).coerceIn(0, 1440),
                limitMinutes = it.limitMinutes,
            )
        }
        if (apps.isEmpty()) return
        Api.pacts.postSummary(
            token = token,
            pactId = pactId,
            body = SummaryCreate(day = LocalDate.now(ZoneId.systemDefault()).toString(), apps = apps),
        )
    }

    /**
     * Tells the server an activity has begun. Best-effort, like everything
     * else here: the walk counts on the phone whether or not this lands.
     */
    suspend fun startActivity(pact: Pact, activity: EarnActivity): Boolean {
        val token = tokens.current() ?: return false
        val pactId = remotePactId(pact) ?: return false
        val result = Api.activities.start(
            token = token,
            pactId = pactId,
            body = ActivityCreate(
                id = activity.id,
                type = activity.type,
                startedAt = iso(activity.startedAtMillis),
                deadlineAt = iso(activity.deadlineAtMillis),
                appPackage = activity.packageName,
            ),
        )
        return result is ApiResult.Ok
    }

    /**
     * Reports a finished activity, with the reward it carried.
     *
     * The event id is made here and kept nowhere, because the server treats
     * the completion itself as idempotent on the activity: a retry of an
     * already-completed activity answers 409, which is a settled state and
     * not a thing to keep trying.
     */
    suspend fun completeActivity(activity: EarnActivity, atMillis: Long): Boolean {
        val token = tokens.current() ?: return false
        val result = Api.activities.complete(
            token = token,
            activityId = activity.id,
            eventId = Uuid7.next(atMillis),
            occurredAt = iso(atMillis),
        )
        return result is ApiResult.Ok
    }

    suspend fun cancelActivity(activity: EarnActivity) {
        val token = tokens.current() ?: return
        Api.activities.cancel(token, activity.id)
    }

    /**
     * Tells the server to forget this phone's push token, and forgets it
     * here.
     *
     * Called on sign-out. The device row stays -- pacts reference it -- but
     * without a token nothing can be delivered to a phone whose owner has
     * left it.
     */
    suspend fun forgetDevice() {
        val token = tokens.current() ?: return
        val device = store.deviceId() ?: return
        Api.devices.forget(token, device)
        store.clearDevice()
    }

    /** Whether everything queued has gone through. */
    suspend fun isDrained(): Boolean = store.pending().isEmpty()

    /**
     * An ISO timestamp from the server, in milliseconds. Null when it is
     * missing or unparseable, which is treated as "cannot restore" rather
     * than as "started at the epoch".
     */
    private fun parseInstantMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun iso(millis: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
        )

    companion object {
        /**
         * How far apart this phone's idea of when a challenge started and
         * the server's may be, and still be the same challenge.
         *
         * The gap is one request: the phone stamps the pact when Start is
         * pressed, the server stamps it when the create lands. Five minutes
         * covers a request that crawled and is nowhere near the distance
         * between two people setting up separately on two handsets.
         */
        private const val ADOPTION_WINDOW_MS = 5L * 60 * 1000
    }
}
