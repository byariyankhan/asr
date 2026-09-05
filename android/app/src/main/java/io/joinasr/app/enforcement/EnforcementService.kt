package io.joinasr.app.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.joinasr.app.MainActivity
import io.joinasr.app.R
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.data.LocalSignOut
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.earn.EarnStore
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.sync.PendingEvent
import io.joinasr.app.sync.Sync
import io.joinasr.app.sync.Uuid7
import io.joinasr.app.usage.Day
import io.joinasr.app.usage.UsageReader
import io.joinasr.app.usage.UsageSnapshot
import io.joinasr.app.usage.usageReader
import io.joinasr.app.witness.WitnessStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The loop. Reads how long each app has been used, compares it with the
 * pact, and puts the block screen in front of anything that has run out.
 *
 * A foreground service because that is the only kind Android will keep
 * running, and because a person whose apps can be blocked should be able to
 * see at a glance that something is watching. The notification is not a
 * formality here; it is the honest disclosure that the app is running.
 *
 * It holds no state of its own beyond the pact it last read and which app it
 * is currently blocking. Everything that decides anything is in
 * [Enforcement], everything that measures anything is in [UsageReader], and
 * both are tested. What is left here is what only a device can do: staying
 * alive, launching the block screen, and noticing a permission has gone.
 */
class EnforcementService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var reader: UsageReader
    private lateinit var store: PactStore
    private lateinit var carried: CarriedUsage
    private lateinit var status: ProtectionStatusStore
    private lateinit var outcomes: OutcomeStore
    private lateinit var witnesses: WitnessStore
    private lateinit var earn: EarnStore
    private lateinit var sync: Sync

    /** When the outbox was last emptied, so it is drained on a timer rather
     *  than on every pass of a loop that can run once a second. */
    @Volatile
    private var lastFlushMillis = 0L

    /** Today's minutes from the phone this challenge came from, and when
     *  the server was last asked for them. */
    private var carriedDay: String? = null
    private var carriedMinutes: Map<String, Int> = emptyMap()
    private var lastCarryCheckMillis = 0L

    /** The last figures the server was given, and when. Only sent when they
     *  have changed: an unchanged day is not news. */
    private var lastSummaryMillis = 0L
    private var lastSummarySent: Map<String, Int> = emptyMap()

    /** True while [end] is running, so a slow write cannot end a pact twice. */
    @Volatile
    private var ending = false

    /** What the foreground notification currently says, so it is not re-posted to say it again. */
    private var showingApps = -1
    private var foregrounded = false

    /**
     * Spent limits already reported, by event id. Cleared with the process;
     * the ids are derived from the day, so a restart re-reports at most one
     * event per app that the server already has.
     */
    private val limitsReported = mutableSetOf<String>()

    @Volatile
    private var pact: Pact? = null

    @Volatile
    private var hadUsageAccess = false

    /**
     * The app the block screen is currently up for, so it is launched once
     * per block rather than once per poll. Cleared the moment anything else
     * comes to the front — including the block screen itself, which makes
     * this app the foreground app and so is naturally "allowed".
     */
    @Volatile
    private var blocking: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        reader = usageReader(this)
        store = PactStore(this)
        carried = CarriedUsage(this)
        status = ProtectionStatusStore(this)
        outcomes = OutcomeStore(this)
        witnesses = WitnessStore(this)
        earn = EarnStore(this)
        sync = Sync(this)

        createChannel()
        startInForeground(apps = 0)

        store.pact.onEach { current ->
            pact = current
            if (current == null) {
                // Nothing to enforce. Not an error, and not something to sit
                // in the notification shade over: the challenge is finished
                // or was never started.
                stopSelf()
            } else {
                startInForeground(apps = current.apps.size)
            }
        }.launchIn(scope)

        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restarted by the system after being killed: come back, because a
        // limit that stops being enforced when memory gets tight is not a
        // limit. The pact is re-read from storage in onCreate.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            // Nothing in here may throw. This loop is the entire product: if
            // it dies, every limit silently stops being enforced and the app
            // looks fine while doing nothing, which is what happened the
            // first time this shipped.
            val delayMillis = runCatching { tick() }.getOrElse { Enforcement.IDLE_MILLIS }
            delay(delayMillis)
        }
    }

    /** One pass. Returns how long to wait before the next one. */
    private suspend fun tick(): Long {
        val current = pact
        val hasAccess = Permissions.hasUsageAccess(this)

        // Usage access can be revoked in Settings at any moment, and without
        // it the system reports an empty event stream rather than an error --
        // so everything measured after that point would read as zero and
        // every limit would silently stop working. Noticing the permission
        // come back and starting the day's count again is the only honest
        // thing to do with it.
        if (hasAccess && !hadUsageAccess) reader.reset()
        hadUsageAccess = hasAccess

        status.record(System.currentTimeMillis(), enforcing = current != null && hasAccess)

        if (current == null || !hasAccess) {
            blocking = null
            return Enforcement.IDLE_MILLIS
        }

        val measured = reader.poll()
        val now = System.currentTimeMillis()
        // What this phone can see, plus what the day already held when the
        // challenge arrived here. Everything below decides against the whole
        // day; nothing below needs to know the difference.
        val snapshot = measured.plus(carriedToday(now, measured.minutesByPackage))
        val progress = ChallengeProgress.of(current.startedAtMillis, current.durationDays, now)
        // Bonus minutes raise today's allowance. Read on every pass rather
        // than cached, because a walk finishing while an app is open should
        // take the block screen down within the second -- which is the whole
        // promise of earning time.
        val earned = earn.earnedToday().minutesByPackage

        cancelFocusIfBroken(snapshot.foregroundPackage, current)

        // Checked before blocking: a pact that has run its course should not
        // be putting a block screen in front of anybody.
        //
        // Nothing else ends it from here any more. Going past a limit is
        // reported and blocked, not punished -- see `Enforcement.overLimit`
        // for why a challenge that could be failed by scrolling was failing
        // people for this app's own missed polls, and for time spent before
        // the challenge existed.
        if (progress.isComplete) {
            end(current)
            return Enforcement.IDLE_MILLIS
        }

        reportLimitsReached(current, snapshot, earned, now)

        sendSummaryIfDue(current, now, snapshot.minutesByPackage)
        flushIfDue(current, now)

        when (val decision = Enforcement.decide(current, snapshot, earned)) {
            Decision.Allow -> blocking = null

            is Decision.Block -> {
                if (blocking != decision.app.packageName) {
                    blocking = decision.app.packageName
                    showBlockScreen(decision)
                }
            }
        }
        return Enforcement.pollDelayMillis(current, snapshot, earned)
    }

    /**
     * Says that a limit was spent, once per app per day.
     *
     * The witnesses' progress screen already reads these -- "Reached a limit
     * on Instagram" -- and nothing on any phone had ever sent one, because
     * the only thing this loop did about a limit was fail the challenge over
     * it. Reaching a limit is not a failure and is worth saying out loud; it
     * is the pact working, in front of the people who asked to watch.
     *
     * The id is derived from the pact, the app and the day rather than from
     * the clock, so the loop noticing the same spent limit forty times, and
     * the service being restarted in the middle of it, all post the same
     * event -- which the server already recognises as one it has.
     */
    private suspend fun reportLimitsReached(
        pact: Pact,
        snapshot: UsageSnapshot,
        earned: Map<String, Int>,
        now: Long,
    ) {
        val over = Enforcement.overLimit(pact, snapshot, earned)
        if (over.isEmpty()) return
        val dayStart = Day.startOfDay(now)
        for (app in over) {
            val id = Uuid7.forDay("${pact.startedAtMillis}|${app.packageName}", dayStart)
            // In memory as well, so a spent limit somebody is sitting on does
            // not put a write through the outbox every second.
            if (!limitsReported.add(id)) continue
            runCatching {
                sync.report(
                    pact,
                    PendingEvent(
                        id = id,
                        type = "limit_hit",
                        appPackage = app.packageName,
                        occurredAtMillis = now,
                    ),
                )
            }
        }
    }

    /**
     * Ends the challenge, and tells whoever is meant to be told.
     *
     * The order matters. The outcome is written first, then the event is
     * queued, then the pact is cleared -- so a phone that dies halfway
     * through comes back with a finished challenge and an event still to
     * send, rather than with a pact it has already stopped enforcing or a
     * failure nothing recorded.
     *
     * The pact is cleared last for the same reason: clearing it is what
     * stops this service, and a service that stopped before writing the
     * outcome would leave the person looking at a dashboard for a challenge
     * that quietly no longer exists.
     */
    private suspend fun end(pact: Pact) {
        if (ending) return
        ending = true
        val now = System.currentTimeMillis()
        val watching = runCatching { witnesses.current().size }.getOrDefault(0)
        // Built in the one place every way of ending is built, so what this
        // writes down and what the witnesses are told cannot drift apart.
        val ending = Endings.completed(pact, watching, Uuid7.next(now), now)
        outcomes.save(ending.outcome)

        runCatching {
            sync.report(pact, ending.event)
            if (sync.isDrained()) outcomes.markReported()
        }

        blocking = null
        store.clear()
    }

    /**
     * Ends a focus session the moment a controlled app is opened.
     *
     * This is the whole rule of a focus session: twenty minutes off the apps
     * being limited. The loop already knows what is in front of the person
     * every second, so it is the only thing on the phone that can enforce it
     * -- and a timer that could be beaten by opening the app it is about
     * would be a reward for nothing.
     */
    private suspend fun cancelFocusIfBroken(foreground: String?, pact: Pact) {
        if (foreground == null) return
        if (pact.appFor(foreground) == null) return
        val running = earn.currentActive() ?: return
        if (running.type != EarnRules.FOCUS) return
        earn.clearActive()
        runCatching { sync.cancelActivity(running) }
    }

    /**
     * Empties the outbox now and then while a challenge is running, so an
     * event queued during a tunnel is not still sitting there a day later.
     */
    private suspend fun flushIfDue(pact: Pact, nowMillis: Long) {
        if (nowMillis - lastFlushMillis < FLUSH_EVERY_MILLIS) return
        lastFlushMillis = nowMillis
        runCatching {
            sync.drain(pact)
            // Measured, not assumed. A heartbeat that always says true is
            // worse than none: it is what a witness would be trusting.
            sync.heartbeat(protectionEnabled = Permissions.canDrawOverlays(this))
            signOutIfEvicted()
        }
    }

    /**
     * Today's figures, often enough to be worth having.
     *
     * Two things read them. A witness watching a challenge being kept, for
     * whom half an hour late is fine -- and the next phone, for whom it is
     * not: whatever has not been sent when somebody signs in elsewhere is a
     * gap in the day that the new phone will hand back as free minutes. Five
     * minutes is the width of that gap now.
     *
     * Only when they have changed, and only marked as sent when they landed:
     * an unchanged day is not news, and a failed send that counted as one
     * would be a hole nobody could see.
     */
    private suspend fun sendSummaryIfDue(pact: Pact, nowMillis: Long, minutesByPackage: Map<String, Int>) {
        if (minutesByPackage == lastSummarySent) return
        if (nowMillis - lastSummaryMillis < SUMMARY_EVERY_MILLIS) return
        lastSummaryMillis = nowMillis
        val sent = runCatching { sync.sendSummary(pact, minutesByPackage) }.getOrDefault(false)
        if (sent) lastSummarySent = minutesByPackage
    }

    /**
     * The minutes today already held when this challenge arrived here.
     *
     * Asked for once, as soon as there is a reading to subtract this phone's
     * own share from -- which is why it is here and not at sign-in: on a new
     * install usage access is granted after the challenge has arrived, and
     * until it is, this phone reads zero for everything.
     *
     * Retried on a minute, because the answer must not be guessed. A failed
     * request is not an empty day.
     */
    private suspend fun carriedToday(nowMillis: Long, ownSoFar: Map<String, Int>): Map<String, Int> {
        val day = CarriedUsage.today(nowMillis)
        if (carriedDay != day) {
            carriedDay = day
            carriedMinutes = carried.forDay(day)
            lastCarryCheckMillis = 0L
        }
        if (nowMillis - lastCarryCheckMillis >= CARRY_RETRY_MILLIS) {
            lastCarryCheckMillis = nowMillis
            if (carried.pendingFor(day)) {
                val totals = runCatching { sync.usedToday(day) }.getOrNull()
                if (totals != null) {
                    carried.resolve(day, totals, ownSoFar)
                    carriedMinutes = carried.forDay(day)
                }
            }
        }
        return carriedMinutes
    }

    /**
     * Lets go of everything when this phone is no longer the one signed in.
     *
     * One account runs on one phone. Somebody signing in on another handset
     * ends this session, moves the challenge across and tells the witnesses
     * -- and this phone is supposed to hear that as a push and stop. Pushes
     * get missed, so the loop asks as well: a 401 is the same answer by a
     * slower road.
     *
     * Not an ending. No outcome is written and nobody is told, because
     * nothing ended: the challenge is being kept somewhere else now.
     */
    private suspend fun signOutIfEvicted() {
        if (sync.evicted()) LocalSignOut.run(this)
    }

    /**
     * Puts the block screen in front of the person.
     *
     * This is a background activity launch, which Android forbids from
     * Android 10 unless the app holds SYSTEM_ALERT_WINDOW — the "display
     * over other apps" permission the setup flow asks for. Without that
     * grant the system drops the launch without a word, so the failure is
     * recorded rather than swallowed: the dashboard reads it and says
     * protection is not working, instead of the person finding out by
     * scrolling uninterrupted past their limit.
     */
    private suspend fun showBlockScreen(decision: Decision.Block) {
        val intent = BlockActivity.intent(
            context = this,
            app = decision.app,
            usedMinutes = decision.usedMinutes,
            limitMinutes = decision.limitMinutes,
            availableAgain = nextResetText(),
        )
        val launched = runCatching { startActivity(intent) }.isSuccess
        if (!launched) {
            blocking = null
            status.recordBlockFailed(System.currentTimeMillis())
        }
    }

    /**
     * When the limits come back, in the phone's own clock format. Read from
     * the device rather than hard-coded to 12 hours, because "Tomorrow at
     * 12:00 AM" is meaningless to most of the world.
     */
    private fun nextResetText(): String {
        val now = System.currentTimeMillis()
        val tomorrow = Day.startOfDay(now) + DAY_MILLIS
        val time = DateFormat.getTimeFormat(this).format(Date(tomorrow))
        return getString(R.string.block_available_again, time)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // A new id, because Android will not let an existing channel's
        // importance be lowered -- only the person can, in settings. On an
        // install that already has the noisier one, creating a quieter
        // channel beside it is the only way the change reaches them; the old
        // one goes so it does not sit in their settings meaning nothing.
        getSystemService<NotificationManager>()?.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.protection_channel_name),
            // The quietest a foreground service is allowed to be.
            //
            // Android will not run one without a notification, and without a
            // foreground service the limits stop being enforced the moment
            // the phone decides to sleep. So it has to exist. What it does
            // not have to do is behave like news: MIN keeps it out of the
            // status bar entirely and at the bottom of the shade, under
            // "Silent", where somebody who wants to check finds it and
            // nobody else is interrupted by it.
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.protection_channel_description)
            setShowBadge(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun startInForeground(apps: Int) {
        // Only when the sentence changes.
        //
        // The pact store re-emits on every write to it, and each emission
        // re-posted this -- which resets the notification's timestamp, so
        // the shade showed "Asr · 2m" as though something had just happened.
        // Nothing had. It has been on all day.
        if (foregrounded && apps == showingApps) return
        showingApps = apps
        foregrounded = true
        val notification = buildNotification(apps)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(apps: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = resources.getQuantityString(R.plurals.protection_apps_limited, apps, apps)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle(getString(R.string.protection_running))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            // No timestamp. There is no moment for it to be relative to --
            // protection has been on since the challenge started -- and the
            // one it showed made a permanent thing read as a fresh one.
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection-quiet"

        /** The IMPORTANCE_LOW channel this replaced. See [createChannel]. */
        private const val LEGACY_CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** How often the loop tries to empty the outbox. */
        private const val FLUSH_EVERY_MILLIS = 30L * 60 * 1000

        /** How often today's figures go up, when they have moved. */
        private const val SUMMARY_EVERY_MILLIS = 5L * 60 * 1000

        /** How often to ask what the day already held, until there is an answer. */
        private const val CARRY_RETRY_MILLIS = 60L * 1000

        /**
         * Starts the loop, if there is anything for it to do.
         *
         * Safe to call as often as anything likes: starting a service that is
         * already running only delivers another onStartCommand, and the
         * service stops itself when there is no pact.
         */
        fun start(context: Context) {
            if (!Permissions.hasUsageAccess(context)) return
            val intent = Intent(context, EnforcementService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, EnforcementService::class.java)) }
        }
    }
}
