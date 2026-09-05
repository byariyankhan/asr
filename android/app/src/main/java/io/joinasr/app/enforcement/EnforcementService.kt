package io.joinasr.app.enforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.joinasr.app.DeepLink
import io.joinasr.app.MainActivity
import io.joinasr.app.R
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.data.LocalSignOut
import io.joinasr.app.diagnostics.Crash
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Whether a limited app was in front on the last pass, so that putting
     *  it down can be noticed. */
    private var wasUsingLimitedApp = false

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
     * Whether the screen is on.
     *
     * Nothing can be used while it is off, so nothing needs measuring: the
     * loop stops entirely and the phone is left alone for the half of every
     * day it spends in a pocket. Android reports the screen going off as an
     * event that closes whatever app was open, at the moment it happened, so
     * the time is not lost by not looking -- it is read back with the right
     * timestamps on the first poll after the screen comes on.
     */
    @Volatile
    private var screenOn = true

    /** Wakes the loop the instant the screen comes back, rather than
     *  leaving somebody a minute of unwatched scrolling. */
    private val screenOnSignal = Channel<Unit>(Channel.CONFLATED)

    private val screenWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    screenOnSignal.trySend(Unit)
                }

                Intent.ACTION_SCREEN_OFF -> screenOn = false

                // Somebody set the clock, or crossed a border, or the date
                // rolled. Today's figures were counted from a midnight that
                // is no longer where it was; the loop starts the day again
                // from what the system reports, and if the challenge now
                // reads as over, the server is asked before it is believed.
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                -> {
                    clockChanged = true
                    screenOnSignal.trySend(Unit)
                }
            }
        }
    }

    /**
     * What has been done about the app being blocked, and whether it took.
     * The activity is launched once per block rather than once per poll;
     * [BlockWatch] is what notices when Android dropped that launch without
     * a word and hands the job to [overlay] instead.
     */
    private val blockWatch = BlockWatch()
    private lateinit var overlay: BlockOverlay

    /**
     * Set by the receiver when the clock, the zone or the date changes under
     * the loop. Everything counted from midnight is counted from a midnight
     * that has moved; the next pass throws it away and reads again.
     */
    @Volatile
    private var clockChanged = false

    /**
     * When the server was last asked whether the challenge may end, on the
     * clock that cannot be set by hand. Asked at most every
     * [COMPLETION_RETRY_MILLIS], because a calendar that disagrees with the
     * server keeps disagreeing until somebody fixes it.
     */
    private var completionAskedAtElapsed = 0L

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
        overlay = BlockOverlay(this)

        // Registered in code and not in the manifest: Android has refused
        // to deliver these two to a manifest receiver since Oreo, and a
        // filter that silently never fires is worse than none.
        screenOn = getSystemService<PowerManager>()?.isInteractive ?: true
        ContextCompat.registerReceiver(
            this,
            screenWatcher,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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
        runCatching { unregisterReceiver(screenWatcher) }
        // A window this service put up outlives nothing. onDestroy is on
        // the main thread, which is the thread the window belongs to.
        if (::overlay.isInitialized) overlay.hideNow()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            // Nothing in here may throw. This loop is the entire product: if
            // it dies, every limit silently stops being enforced and the app
            // looks fine while doing nothing, which is what happened the
            // first time this shipped.
            if (!screenOn) {
                runCatching { rest() }.onFailure { Crash.report(this, it, "rest") }
                continue
            }
            // Caught and reported, never rethrown. A failure here used to
            // vanish; now it is the one thing a phone in the field can say
            // about why a limit was not enforced.
            val delayMillis = runCatching { tick() }.getOrElse {
                Crash.report(this, it, "tick")
                Enforcement.IDLE_MILLIS
            }
            delay(delayMillis)
        }
    }

    /**
     * What the loop does while the screen is off, which is as close to
     * nothing as it can be.
     *
     * No app can be in front of somebody who is not looking at the phone, so
     * there is nothing to measure and nothing to block -- and this is half
     * of every day. The one thing that still has to happen is the
     * half-hourly errand: an event queued in a tunnel, and the heartbeat,
     * without which a phone asleep for a day would be reported to its
     * witnesses as a phone that stopped protecting anything.
     *
     * Waits for the screen rather than for a timer, so somebody who picks
     * the phone up is watched from the first second rather than from the
     * next tick.
     */
    private suspend fun rest() {
        blockWatch.clear()
        overlay.hide()
        val current = pact
        val now = System.currentTimeMillis()
        status.record(now, enforcing = current != null && Permissions.hasUsageAccess(this))
        if (current != null) flushIfDue(current, now)
        withTimeoutOrNull(DARK_MILLIS) { screenOnSignal.receive() }
    }

    /** One pass. Returns how long to wait before the next one. */
    private suspend fun tick(): Long {
        val current = pact
        val hasAccess = Permissions.hasUsageAccess(this)
        val now = System.currentTimeMillis()

        // Usage access can be revoked in Settings at any moment, and without
        // it the system reports an empty event stream rather than an error --
        // so everything measured after that point would read as zero and
        // every limit would silently stop working. Noticing the permission
        // come back and starting the day's count again is the only honest
        // thing to do with it.
        if (hasAccess && !hadUsageAccess) reader.reset()
        hadUsageAccess = hasAccess

        if (clockChanged) {
            clockChanged = false
            reader.reset()
            carriedDay = null
            blockWatch.clear()
            overlay.hide()
        }

        status.record(now, enforcing = current != null && hasAccess)

        // The half-hourly errand goes out whether or not usage access is on.
        // It used to be skipped without it, so a permission revoked in the
        // middle of a challenge produced silence rather than a heartbeat
        // saying protection was off -- and silence takes the server a day to
        // notice, where a heartbeat saying so takes it two hours.
        if (current != null) flushIfDue(current, now)

        if (current == null || !hasAccess) {
            blockWatch.clear()
            overlay.hide()
            return Enforcement.IDLE_MILLIS
        }

        val measured = reader.poll()
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
        // If it did not end, the calendar here and the server's disagree,
        // or the server could not be asked and this phone's clock is not one
        // to be trusted alone. Either way the challenge carries on being
        // enforced, and the question is asked again later.
        if (progress.isComplete && end(current)) return Enforcement.IDLE_MILLIS

        reportLimitsReached(current, snapshot, earned, now)

        sendSummaryIfDue(current, now, snapshot.minutesByPackage, snapshot.foregroundPackage)

        val blocked = Enforcement.decide(current, snapshot, earned) as? Decision.Block
        when (blockWatch.next(blocked?.app?.packageName, now)) {
            BlockWatch.Step.Nothing -> if (blocked == null) overlay.hide()

            BlockWatch.Step.LaunchActivity -> {
                overlay.hide()
                val launched = blocked != null && launchBlockActivity(blocked)
                if (launched) blockWatch.shown(BlockWatch.Via.Activity, now) else blockWatch.failed(now)
            }

            // The activity was launched and the blocked app is still what is
            // in front: Android dropped the launch without a word. Written
            // down, so the dashboard can say so, and drawn as a window
            // instead, which needs no exemption from anybody.
            BlockWatch.Step.ShowOverlay -> {
                status.recordBlockFailed(now)
                val drawn = blocked != null && showOverlay(blocked)
                if (drawn) blockWatch.shown(BlockWatch.Via.Overlay, now) else blockWatch.failed(now)
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
    private suspend fun end(pact: Pact): Boolean {
        if (ending) return false
        val sinceAsked = SystemClock.elapsedRealtime() - completionAskedAtElapsed
        if (completionAskedAtElapsed != 0L && sinceAsked < COMPLETION_RETRY_MILLIS) return false
        ending = true
        try {
            completionAskedAtElapsed = SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            val watching = runCatching { witnesses.current().size }.getOrDefault(0)
            // Built in the one place every way of ending is built, so what
            // this writes down and what the witnesses are told cannot drift
            // apart.
            val completed = Endings.completed(pact, watching, Uuid7.next(now), now)

            // Asked, not announced. Completion is the one ending that is a
            // date arriving rather than a thing the person did, and the date
            // is the easiest thing on a phone to change: with nothing here, a
            // month moved forward in Settings finished a challenge on day
            // three and the witnesses were congratulated. The server keeps
            // its own calendar and refuses a completion before it; when it
            // cannot be asked, a phone that takes its time from the network
            // is trusted, and one whose time was set by hand waits.
            val confirmation = runCatching { sync.confirmCompletion(pact, completed.event) }
                .getOrDefault(Sync.Confirmation.Unreachable)
            when (confirmation) {
                Sync.Confirmation.TooEarly -> {
                    reader.reset()
                    return false
                }

                Sync.Confirmation.Unreachable -> if (!DeviceClock.isAutomatic(this)) return false

                Sync.Confirmation.Confirmed -> Unit
            }

            outcomes.save(completed.outcome)
            if (confirmation == Sync.Confirmation.Confirmed) {
                outcomes.markReported()
            } else {
                runCatching {
                    sync.report(pact, completed.event)
                    if (sync.isDrained()) outcomes.markReported()
                }
            }

            blockWatch.clear()
            overlay.hide()
            store.clear()
            return true
        } finally {
            ending = false
        }
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
            // worse than none: it is what a witness would be trusting. Both
            // grants, because either one missing is a challenge nothing
            // enforces, and the server starts a two-hour clock on a false.
            sync.heartbeat(protectionEnabled = Permissions.protectionOn(this))
            signOutIfEvicted()
        }
    }

    /**
     * Today's figures, sent when something happened rather than on a clock.
     *
     * Two things read them. A witness watching a challenge being kept, for
     * whom half an hour late is fine -- and the next phone to hold this
     * challenge, for whom it is not: whatever has not been sent when
     * somebody signs in elsewhere is a piece of the day the new phone hands
     * back as free minutes.
     *
     * A timer would spend a phone's night sending a number that has not
     * moved. These figures only change while a limited app is actually in
     * front of somebody, so that is when this sends: once when they put it
     * down, which is the moment the number is final, and every few minutes
     * while they are still holding it, so that a long sitting cannot be
     * carried off by signing in elsewhere mid-scroll. An idle phone sends
     * nothing at all -- the first line returns, because nothing has changed.
     *
     * The half-hourly floor stays for what moves without anybody opening
     * anything: minutes earned by walking, and the day rolling over.
     *
     * Only marked as sent when it landed. A failed send counted as a success
     * would be a hole nobody could see.
     */
    private suspend fun sendSummaryIfDue(
        pact: Pact,
        nowMillis: Long,
        minutesByPackage: Map<String, Int>,
        foreground: String?,
    ) {
        val using = foreground != null && pact.appFor(foreground) != null
        val justPutItDown = wasUsingLimitedApp && !using
        wasUsingLimitedApp = using

        if (minutesByPackage == lastSummarySent) return
        val due = justPutItDown ||
            (using && nowMillis - lastSummaryMillis >= SUMMARY_WHILE_USING_MILLIS) ||
            nowMillis - lastSummaryMillis >= SUMMARY_FLOOR_MILLIS
        if (!due) return

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
     * Puts the block screen in front of the person, as an activity.
     *
     * This is a background activity launch, which Android forbids from
     * Android 10 unless the app holds SYSTEM_ALERT_WINDOW — the "display
     * over other apps" permission the setup flow asks for. A refused launch
     * does not throw: `startActivity` returns and nothing appears. So true
     * here only means the request was made; whether it took is what
     * [BlockWatch] reads off the next poll, when the blocked app is either
     * gone from the foreground or still there.
     */
    private fun launchBlockActivity(decision: Decision.Block): Boolean {
        val intent = BlockActivity.intent(
            context = this,
            app = decision.app,
            usedMinutes = decision.usedMinutes,
            limitMinutes = decision.limitMinutes,
            availableAgain = nextResetText(),
        )
        return runCatching { startActivity(intent) }.isSuccess
    }

    /**
     * The same screen, drawn as a window over the app, for a phone that
     * dropped the activity. Needs only the overlay grant, which the setup
     * flow asked for and the heartbeat reports on.
     */
    private suspend fun showOverlay(decision: Decision.Block): Boolean = overlay.show(
        BlockOverlay.Shown(
            app = decision.app,
            usedMinutes = decision.usedMinutes,
            limitMinutes = decision.limitMinutes,
            availableAgain = nextResetText(),
            // Home, the way the activity's button does it. The window comes
            // down on the next pass, once the launcher is what is in front;
            // if the launch is refused too, it stays up over the app, which
            // is the point of it.
            onLeave = {
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onEarnTime = {
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(DeepLink.EXTRA_EARN_FOR, decision.app.packageName),
                    )
                }
            },
        ),
    )

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

        /** How long to sleep between errands while the screen is off. The
         *  screen coming back interrupts this; nothing else needs to. */
        private const val DARK_MILLIS = 15L * 60 * 1000

        /** How often the loop tries to empty the outbox. */
        private const val FLUSH_EVERY_MILLIS = 30L * 60 * 1000

        /** While a limited app is in front: often enough that a long
         *  sitting cannot be carried off by signing in elsewhere. */
        private const val SUMMARY_WHILE_USING_MILLIS = 5L * 60 * 1000

        /** And a floor, for what moves without anybody opening anything. */
        private const val SUMMARY_FLOOR_MILLIS = 30L * 60 * 1000

        /** How often to ask what the day already held, until there is an answer. */
        private const val CARRY_RETRY_MILLIS = 60L * 1000

        /** How often to ask the server again whether a challenge that reads as over is over. */
        private const val COMPLETION_RETRY_MILLIS = 15L * 60 * 1000

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
