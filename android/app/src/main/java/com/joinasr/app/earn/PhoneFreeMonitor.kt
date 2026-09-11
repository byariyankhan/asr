package com.joinasr.app.earn

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.joinasr.app.analytics.Analytics
import com.joinasr.app.diagnostics.Crash
import com.joinasr.app.sync.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owned by the existing foreground service, independent of its screen-off usage loop. */
class PhoneFreeMonitor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = EarnStore(context)
    private val keyguard = context.getSystemService<KeyguardManager>()
    private val alarms = context.getSystemService<AlarmManager>()
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var running: EarnActivity? = null
    private var session: PhoneFreeSession? = null
    private var alarmAt: Long? = null
    private var registered = false

    private sealed interface Event {
        data class Activity(val value: EarnActivity?) : Event
        data class State(
            val elapsed: Long,
            val locked: Boolean,
            val unlocked: Boolean = false,
            val pending: BroadcastReceiver.PendingResult? = null,
        ) : Event
    }

    private val deadlineIntent = PendingIntent.getBroadcast(
        context, 20, Intent(ACTION_DEADLINE).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    private val deadlineListener = AlarmManager.OnAlarmListener { sample() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action !in setOf(
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_SCREEN_OFF, ACTION_DEADLINE,
                )) return
            val pending = goAsync()
            // Capture now, before any asynchronous disk work. USER_PRESENT
            // means keyguard dismissal, not a notification lighting the display.
            val event = Event.State(
                SystemClock.elapsedRealtime(), isLocked(),
                action == Intent.ACTION_USER_PRESENT, pending,
            )
            if (!events.trySend(event).isSuccess) pending.finish()
        }
    }

    fun start() {
        // In particular, clear a deadline left by a killed service. Its old
        // interval is unverified and will not be restored from EarnActivity.
        cancelAlarm()
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                // These only prompt a KeyguardManager query. Neither screen
                // event by itself starts, resets, or completes the timer.
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(ACTION_DEADLINE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        scope.launch {
            for (event in events) {
                try {
                    when (event) {
                        is Event.Activity -> activityChanged(event.value)
                        is Event.State -> observe(event)
                    }
                } catch (error: Exception) {
                    // Never continue an interval after a monitoring failure.
                    session = running?.let { PhoneFreeSession(it.target * 60_000L) }
                    cancelAlarm()
                    Crash.report(context, error, "phone_free")
                } finally {
                    (event as? Event.State)?.pending?.finish()
                }
            }
        }
        scope.launch { store.active.collect { events.send(Event.Activity(it)) } }
        scope.launch {
            while (isActive) {
                delay(1_000)
                // Catches delayed keyguard engagement and OEM transitions.
                // No wake lock: elapsedRealtime accounts for CPU sleep.
                if (running != null) sample()
            }
        }
    }

    private fun isLocked(): Boolean = keyguard?.isKeyguardLocked == true

    private fun sample() {
        events.trySend(Event.State(SystemClock.elapsedRealtime(), isLocked()))
    }

    private suspend fun activityChanged(activity: EarnActivity?) {
        val focus = activity?.takeIf { it.type == EarnRules.FOCUS }
        if (focus?.id == running?.id) return
        cancelAlarm()
        running = focus
        session = focus?.let { PhoneFreeSession(it.target * 60_000L) }
        if (focus != null) {
            // Reset old progress, including attempts created before this upgrade.
            store.update(focus.copy(progress = 0, focusLockedSinceElapsed = null))
            observe(Event.State(SystemClock.elapsedRealtime(), isLocked()))
        }
    }

    private suspend fun observe(event: Event.State) {
        val activity = running ?: return
        val timer = session ?: return
        timer.observe(event.elapsed, event.locked, event.unlocked)
        if (timer.complete) {
            cancelAlarm()
            val completed = activity.copy(progress = activity.target, focusLockedSinceElapsed = null)
            if (store.complete(completed)) {
                showCompleted(completed)
                Analytics.log(Analytics.extraTimeEarned(completed.type))
                // Network work must not hold up the unlock receiver or local reward.
                scope.launch(Dispatchers.IO) {
                    runCatching { Sync(context).completeActivity(completed, System.currentTimeMillis()) }
                }
            }
            running = null
            session = null
            return
        }
        val updated = activity.copy(progress = 0, focusLockedSinceElapsed = timer.lockedSinceElapsed)
        if (updated != activity) {
            store.update(updated)
            running = updated
        }
        scheduleAlarm(timer.deadlineElapsed)
    }

    private fun scheduleAlarm(deadline: Long?) {
        if (deadline == alarmAt) return
        cancelAlarm()
        if (deadline == null) return
        // Listener-based exact alarms require no special access and improve
        // timing outside Doze. The inexact wakeup is the permission-free
        // fallback in idle; Android may defer it, but never awards early.
        alarms?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, deadlineIntent)
        runCatching {
            alarms?.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, "asr-phone-free",
                deadlineListener, Handler(Looper.getMainLooper()),
            )
        }
        alarmAt = deadline
    }

    private fun cancelAlarm() {
        alarms?.cancel(deadlineIntent)
        alarms?.cancel(deadlineListener)
        alarmAt = null
    }

    private fun showCompleted(activity: EarnActivity) {
        EarnNotifications.completed(
            context, activity,
            channelId = CHANNEL_ID, channelName = "Phone-free sessions",
            title = "Your ${activity.target} phone-free minutes are complete.",
            requestCode = 20,
        )
    }

    fun stop() {
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        cancelAlarm()
        events.close()
        scope.cancel()
        while (true) {
            val event = events.tryReceive().getOrNull() ?: break
            (event as? Event.State)?.pending?.finish()
        }
    }

    companion object {
        const val CHANNEL_ID = "phone-free"
        private const val ACTION_DEADLINE = "com.joinasr.app.PHONE_FREE_DEADLINE"
    }
}
