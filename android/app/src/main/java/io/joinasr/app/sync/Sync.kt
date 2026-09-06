package io.joinasr.app.sync

import android.content.Context
import android.os.Build
import io.joinasr.app.BuildConfig
import io.joinasr.app.analytics.Analytics
import io.joinasr.app.data.Api
import io.joinasr.app.data.ActivityCreate
import io.joinasr.app.data.ActivityRule
import io.joinasr.app.data.ActivityRules
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.DeviceRegistration
import io.joinasr.app.data.EventCreate
import io.joinasr.app.data.PactAppAdd
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
            protectionEnabled = Permissions.protectionOn(app),
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
     * back from -- but it comes back as a question, not as a fact. Nothing
     * here claims the challenge or starts enforcing it: see [takeOver].
     *
     * Null when there is nothing to restore, when there is no signal, or
     * when the server's copy is missing the snapshot -- which is every pact
     * created before the phone started sending one. Those are unrecoverable
     * and saying so by returning null is better than rebuilding a challenge
     * with no apps in it.
     */
    suspend fun remoteChallenge(): RemoteChallenge? {
        val token = tokens.current() ?: return null
        val remote = (Api.pacts.current(token) as? ApiResult.Ok)?.value ?: return null
        if (remote.status != null && remote.status != "active") return null
        val snapshot = remote.snapshot ?: return null
        val startedAt = parseInstantMillis(remote.startsAt) ?: return null
        val apps = snapshot.apps.map(::localApp)
        if (apps.isEmpty()) return null

        // Registering is what moves the challenge onto this phone, signs the
        // last one out and tells the witnesses -- all of it server-side, in
        // this one request. It is not here for the id it returns.
        deviceId()
        return RemoteChallenge(
            pact = Pact(
                apps = apps,
                startedAtMillis = startedAt,
                durationDays = remote.durationDays ?: ChallengeDuration.DEFAULT_DAYS,
            ),
            remoteId = remote.id,
        )
    }

    /**
     * Writes down the server's id for a challenge this phone has just been
     * handed, before it starts enforcing it.
     *
     * Nothing claims anything here. One account runs on one phone, so
     * registering this install *is* taking the challenge over -- the server
     * moves it, signs the last phone out and tells the witnesses, all inside
     * the request [deviceId] already made to get here. By the time this runs
     * the challenge is already this phone's.
     *
     * What is left is local: the id, keyed to this pact's start time, so an
     * event detected in the next second is filed against the challenge that
     * exists rather than against a second one this phone would otherwise try
     * to create.
     */
    suspend fun adopt(challenge: RemoteChallenge) {
        store.saveRemotePact(challenge.remoteId, challenge.pact.startedAtMillis)
    }

    /** What went out or came back on the wire, as the enforcement loop reads it. */
    private fun localApp(remote: SnapshotApp) = PactApp(
        packageName = remote.packageName,
        label = remote.label,
        limitMinutes = remote.dailyLimitMinutes,
        addedOn = remote.addedOn,
    )

    /** Why an app could not be added, in words for the screen. */
    sealed interface AddAppResult {
        /** The server's copy of the challenge, with the app in it. */
        data class Added(val pact: Pact) : AddAppResult
        data class Refused(val message: String) : AddAppResult
    }

    /**
     * Brings one more app under a limit on the running challenge.
     *
     * The one thing in this class that is not best-effort. Everything else
     * here is the phone telling the server what already happened; this is
     * the phone asking, and waiting for the answer, because the answer is
     * the new challenge. The witnesses read the server's copy, and a phone
     * that added an app locally and told the server later would spend that
     * gap enforcing a promise nobody else could see. So no connection means
     * no change, and the screen says so.
     *
     * The pact that comes back replaces the local one whole -- apps, the
     * day each came in, everything. The start time and the duration are the
     * phone's own, which the server's copy agrees with by construction.
     *
     * An app the server already has (a retry after a lost answer) is not a
     * failure: the current copy is fetched and adopted the same way.
     */
    suspend fun addApp(pact: Pact, packageName: String, label: String, limitMinutes: Int): AddAppResult {
        val token = tokens.current()
            ?: return AddAppResult.Refused("Sign in again to change your challenge.")
        val id = remotePactId(pact)
            ?: return AddAppResult.Refused(NO_CONNECTION)
        val body = PactAppAdd(packageName = packageName, label = label, dailyLimitMinutes = limitMinutes)
        val remote = when (val answer = Api.pacts.addApp(token, id, body)) {
            is ApiResult.Ok -> answer.value
            is ApiResult.Offline -> return AddAppResult.Refused(NO_CONNECTION)
            is ApiResult.Failure -> when {
                answer.code == 409 && answer.error == "app_already_in_pact" ->
                    (Api.pacts.current(token) as? ApiResult.Ok)?.value
                        ?: return AddAppResult.Refused(NO_CONNECTION)
                answer.code == 409 ->
                    return AddAppResult.Refused("Your challenge has ended, so nothing can be added to it.")
                else -> return AddAppResult.Refused(answer.message)
            }
        }
        val apps = remote.snapshot?.apps?.map(::localApp).orEmpty()
        // The server never hands back fewer apps than it was given; an
        // answer with none is an answer that could not be read, and
        // replacing a running challenge with an empty one is not a way to
        // handle that.
        if (apps.none { it.packageName == packageName }) return AddAppResult.Refused(NO_CONNECTION)
        return AddAppResult.Added(pact.copy(apps = apps))
    }

    /**
     * What the challenge's apps have already had today, wherever it was
     * spent.
     *
     * Null when there is no answer -- no signal, no session, or a figure
     * stamped with a different day than the one being asked about. Null is
     * "ask again", never "nothing was used": treating a failed request as a
     * clean slate is exactly the fresh allowance this exists to remove.
     */
    suspend fun usedToday(day: String): Map<String, Int>? {
        val token = tokens.current() ?: return null
        val remote = (Api.pacts.current(token) as? ApiResult.Ok)?.value ?: return null
        val today = remote.today ?: return null
        if (today.day != day) return null
        return today.apps.associate { it.packageName to it.minutesUsed }
    }

    /**
     * Whether this phone's session is gone -- which, for this account, means
     * somebody signed in on another one.
     *
     * Asked by the enforcement loop now and then, because the push that says
     * so can be missed: no Play services, notifications switched off, a
     * phone that was in flight mode when it was sent. Without a second way
     * of finding out, this phone would go on blocking apps for a challenge
     * it no longer holds until somebody opened the app.
     *
     * Only a 401 counts. Offline is not evicted, a 500 is not evicted, and a
     * phone that stops enforcing because a server had a bad minute would be
     * a worse bug than the one this closes.
     */
    suspend fun evicted(): Boolean {
        val token = tokens.current() ?: return false
        val result = Api.me.get(token)
        return result is ApiResult.Failure && result.code == 401
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
        if (created is ApiResult.Ok) Analytics.log(Analytics.pactStarted(pact.durationDays))
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

    /** What the server said when asked whether a challenge may end as completed. */
    enum class Confirmation {
        /** The server agrees, and has recorded it. */
        Confirmed,

        /** The server's calendar says the challenge is still running. */
        TooEarly,

        /** No answer: no session, no signal, or a server having a bad minute. */
        Unreachable,
    }

    /**
     * Asks the server to record a completion, now, and says what it thought.
     *
     * Completion is the one ending the phone is not trusted about on its
     * own. Every other ending is something the person did; this one is a
     * date arriving, and the date is the easiest thing on a phone to change.
     * So it is posted directly rather than queued, and the answer decides
     * whether the challenge ends here at all -- a refusal of `pact_not_elapsed`
     * is the server saying the calendar disagrees.
     *
     * Any other 409 is a challenge the server has already closed, by its
     * own clock or another ending, and that stands: there is nothing left to
     * enforce, whichever ending the server wrote.
     */
    suspend fun confirmCompletion(pact: Pact, event: PendingEvent): Confirmation {
        val token = tokens.current() ?: return Confirmation.Unreachable
        val pactId = remotePactId(pact) ?: return Confirmation.Unreachable
        return when (val result = Api.pacts.postEvent(token, pactId, wire(event))) {
            is ApiResult.Ok -> Confirmation.Confirmed
            is ApiResult.Failure -> when {
                result.error == NOT_ELAPSED -> Confirmation.TooEarly
                result.code == 409 -> Confirmation.Confirmed
                else -> Confirmation.Unreachable
            }
            is ApiResult.Offline -> Confirmation.Unreachable
        }
    }

    /**
     * Sends everything queued, oldest first, and stops at the first one that
     * does not go through — so a phone that comes back on a train does not
     * report a breach before the challenge it belongs to.
     *
     * What happens to an event the server answers with an error is
     * [OutboxPolicy]'s decision: kept when the server may yet take it, dropped
     * when it has refused the event itself. Both matter. An event kept forever
     * is a stuck queue with everything behind it; an event dropped on a 502
     * is a witness never told, and every deploy serves a minute of 502s.
     */
    suspend fun drain(pact: Pact?) {
        val queued = store.pending()
        if (queued.isEmpty()) return
        val token = tokens.current() ?: return
        val pactId = pact?.let { remotePactId(it) } ?: return

        for (event in queued.sortedBy { it.occurredAtMillis }) {
            when (val result = Api.pacts.postEvent(token = token, pactId = pactId, event = wire(event))) {
                is ApiResult.Ok -> store.drop(event.id)
                is ApiResult.Failure -> {
                    if (OutboxPolicy.keepAfter(result.code)) return
                    store.drop(event.id)
                }
                is ApiResult.Offline -> return
            }
        }
    }

    private fun wire(event: PendingEvent) = EventCreate(
        id = event.id,
        type = event.type,
        reason = event.reason,
        appPackage = event.appPackage,
        minutes = event.minutes,
        occurredAt = iso(event.occurredAtMillis),
    )

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
    suspend fun sendSummary(pact: Pact, minutesByPackage: Map<String, Int>): Boolean {
        val token = tokens.current() ?: return false
        val pactId = remotePactId(pact) ?: return false
        val apps = pact.apps.map {
            SummaryApp(
                packageName = it.packageName,
                minutesUsed = (minutesByPackage[it.packageName] ?: 0).coerceIn(0, 1440),
                limitMinutes = it.limitMinutes,
            )
        }
        if (apps.isEmpty()) return false
        // The answer matters here, unlike everywhere else in this class. A
        // figure the server did not receive is a gap in the day, and the
        // next phone to hold this challenge would hand that gap back as free
        // minutes.
        return Api.pacts.postSummary(
            token = token,
            pactId = pactId,
            body = SummaryCreate(day = LocalDate.now(ZoneId.systemDefault()).toString(), apps = apps),
        ) is ApiResult.Ok
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
        private const val NO_CONNECTION =
            "Adding an app needs a connection, so your witnesses' copy of the challenge matches yours. Try again in a moment."
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

        /** The server's word for "not by my calendar", on a `completed` event. */
        private const val NOT_ELAPSED = "pact_not_elapsed"
    }
}
