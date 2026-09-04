package io.joinasr.app.sync

import android.content.Context
import android.os.Build
import io.joinasr.app.BuildConfig
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.DeviceRegistration
import io.joinasr.app.data.EventCreate
import io.joinasr.app.data.PactCreate
import io.joinasr.app.data.PactSnapshot
import io.joinasr.app.data.SnapshotApp
import io.joinasr.app.data.SummaryApp
import io.joinasr.app.data.SummaryCreate
import io.joinasr.app.enforcement.Pact
import java.time.Instant
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
        )
        val result = Api.devices.register(token, registration)
        return when (result) {
            is ApiResult.Ok -> result.value.id.also { store.saveDeviceId(it) }
            else -> null
        }
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
            ),
        )
        val created = Api.pacts.create(token, body)
        val id = when {
            created is ApiResult.Ok -> created.value.id
            created is ApiResult.Failure && created.code == 409 && store.pending().isEmpty() ->
                (Api.pacts.current(token) as? ApiResult.Ok)?.value?.id
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
        Api.devices.heartbeat(token, device, protectionEnabled, BuildConfig.VERSION_NAME)
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

    /** Whether everything queued has gone through. */
    suspend fun isDrained(): Boolean = store.pending().isEmpty()

    private fun iso(millis: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
        )
}
