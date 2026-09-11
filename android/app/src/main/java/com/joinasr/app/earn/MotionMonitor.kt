package com.joinasr.app.earn

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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

/**
 * A run or a climb, measured while the app is not open.
 *
 * Owned by the foreground service beside [PhoneFreeMonitor], for the same
 * reason: a walk can be read from the step counter's total whenever the
 * app next looks, but a run is steps at a pace and a climb is a rise
 * while stepping, and both need the readings as they happen. The sensors
 * are listened to only while one of these activities is running, and
 * with a report latency of ten seconds, so the sensor hub batches them
 * and the phone in a pocket stays asleep between batches; every reading
 * carries its own timestamp, which is what the counters go by.
 *
 * The step counter's wake-up variant is asked for first, so a batch that
 * fills while the phone sleeps is delivered rather than dropped. The
 * barometer is read with the same latency, and the stairs counter pairs
 * the two by their timestamps; since the two sensors' batches arrive
 * separately, readings are held for [SETTLE_MILLIS] and judged in
 * timestamp order, so a rise is never judged before the steps taken
 * during it have been seen. Progress therefore runs that far behind the
 * feet, and never loses anything.
 *
 * Progress is written to the activity as it is earned; the reward is
 * applied here, locally and first, and reported after, as everywhere
 * else in this feature. Nothing about the readings leaves the phone.
 */
class MotionMonitor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = EarnStore(context)
    private val sensors = context.getSystemService<SensorManager>()
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var running: EarnActivity? = null
    private var run: RunCounter? = null
    private var stairs: StairsCounter? = null
    private var listening = false

    private sealed interface Event {
        data class Activity(val value: EarnActivity?) : Event
        /** A reading, stamped with the sensor's own clock (elapsed realtime, in millis). */
        sealed interface Reading : Event {
            val atMillis: Long
        }
        data class Steps(val total: Int, override val atMillis: Long) : Reading
        data class Pressure(val hectopascals: Float, override val atMillis: Long) : Reading
        /** Time to judge the readings old enough to be in order. */
        data object Drain : Event
    }

    /** Readings waiting to be old enough to judge in order. */
    private val pending = ArrayList<Event.Reading>()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val at = event.timestamp / 1_000_000L
            val value = event.values.firstOrNull() ?: return
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> events.trySend(Event.Steps(value.toInt(), at))
                Sensor.TYPE_PRESSURE -> events.trySend(Event.Pressure(value, at))
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        scope.launch {
            for (event in events) {
                try {
                    when (event) {
                        is Event.Activity -> activityChanged(event.value)
                        is Event.Reading -> pending.add(event)
                        Event.Drain -> drain()
                    }
                } catch (error: Exception) {
                    Crash.report(context, error, "motion")
                }
            }
        }
        scope.launch { store.active.collect { events.send(Event.Activity(it)) } }
        scope.launch {
            while (isActive) {
                delay(DRAIN_EVERY_MILLIS)
                if (listening) events.trySend(Event.Drain)
            }
        }
    }

    /** Judges every reading older than [SETTLE_MILLIS], oldest first; first, whether there is still time. */
    private suspend fun drain() {
        if (expireIfOverdue()) return
        if (pending.isEmpty()) return
        val cutoff = SystemClock.elapsedRealtime() - SETTLE_MILLIS
        val ready = pending.filter { it.atMillis <= cutoff }.sortedBy { it.atMillis }
        if (ready.isEmpty()) return
        pending.removeAll(ready.toSet())
        for (reading in ready) {
            if (running == null) break
            when (reading) {
                is Event.Steps -> onSteps(reading)
                is Event.Pressure -> onPressure(reading)
            }
        }
    }

    private fun activityChanged(activity: EarnActivity?) {
        val motion = activity?.takeIf { it.isMotion }
        if (motion?.id == running?.id) {
            running = motion
            return
        }
        stopListening()
        pending.clear()
        running = motion
        run = null
        stairs = null
        if (motion == null) return
        when (motion.type) {
            EarnRules.RUN -> run = RunCounter()
            EarnRules.STAIRS -> stairs = StairsCounter()
        }
        startListening(pressure = motion.type == EarnRules.STAIRS)
    }

    private fun startListening(pressure: Boolean) {
        val manager = sensors ?: return
        val steps = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return
        manager.registerListener(listener, steps, SensorManager.SENSOR_DELAY_NORMAL, REPORT_LATENCY_MICROS)
        if (pressure) {
            manager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL, REPORT_LATENCY_MICROS)
            }
        }
        listening = true
    }

    private fun stopListening() {
        if (listening) sensors?.unregisterListener(listener)
        listening = false
    }

    private suspend fun onSteps(event: Event.Steps) {
        val activity = running ?: return
        stairs?.observeSteps(event.total, event.atMillis)
        val earned = run?.observe(event.total, event.atMillis) ?: 0
        credit(activity, earned)
    }

    private suspend fun onPressure(event: Event.Pressure) {
        val activity = running ?: return
        val earned = stairs?.observePressure(event.hectopascals, event.atMillis) ?: 0
        credit(activity, earned)
    }

    /**
     * The server fails an activity whose deadline passed and would refuse
     * its completion, so one that ran out of time is stood down here rather
     * than awarded and refused after. Checked on every tick, not only when
     * something is earned: a run that never reached a running pace would
     * otherwise sit active, sensors on, until somebody gave it up by hand.
     * True if the activity was stood down.
     */
    private suspend fun expireIfOverdue(): Boolean {
        val activity = running ?: return false
        if (!activity.expired(System.currentTimeMillis())) return false
        stopListening()
        pending.clear()
        running = null
        store.clearActive(activity.id)
        return true
    }

    /** Units earned, onto the activity. */
    private suspend fun credit(activity: EarnActivity, earned: Int) {
        if (earned <= 0) return
        val updated = activity.copy(progress = (activity.progress + earned).coerceAtMost(activity.target))
        if (!updated.isComplete) {
            store.update(updated)
            running = updated
            return
        }
        stopListening()
        running = null
        if (store.complete(updated)) {
            showCompleted(updated)
            Analytics.log(Analytics.extraTimeEarned(updated.type))
            scope.launch(Dispatchers.IO) {
                runCatching { Sync(context).completeActivity(updated, System.currentTimeMillis()) }
            }
        }
    }

    private fun showCompleted(activity: EarnActivity) {
        EarnNotifications.completed(
            context, activity,
            channelId = CHANNEL_ID, channelName = "Runs and climbs",
            title = when (activity.type) {
                EarnRules.RUN -> "Your run is done."
                else -> "Your ${activity.target} floors are climbed."
            },
            requestCode = 21,
        )
    }

    fun stop() {
        stopListening()
        pending.clear()
        events.close()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "motion"

        /** How long the sensor hub may hold readings before delivering a batch. */
        private const val REPORT_LATENCY_MICROS = 10_000_000

        /** A reading is judged once it is this old: every batch that could precede it has arrived. */
        private const val SETTLE_MILLIS = 12_000L
        private const val DRAIN_EVERY_MILLIS = 2_000L
    }
}
