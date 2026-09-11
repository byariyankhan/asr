package com.joinasr.app.earn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.joinasr.app.MainActivity
import com.joinasr.app.R
import com.joinasr.app.analytics.Analytics
import com.joinasr.app.diagnostics.Crash
import com.joinasr.app.permissions.Permissions
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
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * A ride being measured: GPS on, with the phone in a pocket and the
 * screen off, for as long as the ride is the active activity.
 *
 * Its own foreground service rather than a monitor inside the
 * enforcement one, because Android 14 wants a service that reads
 * location to say so in its type, and the enforcement service is a
 * special-use one for a different reason and runs all day; location must
 * run only for a ride. This one starts when a ride starts (with the
 * location permission already granted, which is what lets a location
 * service start at all), and stops itself when the ride completes, is
 * given up, runs out of time, or has credited nothing for half an hour,
 * which is a phone forgotten with GPS on.
 *
 * Every fix goes to [RideCounter], with the step counter and the
 * accelerometer beside it (a run is told by the feet, a car seat by the
 * lack of shake), and is then forgotten: no route is kept, nothing about
 * where the phone was is written or sent. Progress is metres on the
 * activity; the reward is applied here, locally and first, and reported
 * after, like every other activity's.
 */
class RideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store by lazy { EarnStore(this) }
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val counter = RideCounter()
    private var running: EarnActivity? = null
    private var lastCreditAt: Long = 0L
    private var listening = false

    private sealed interface Event {
        data class Activity(val value: EarnActivity?) : Event
        data class Fix(
            val latitude: Double,
            val longitude: Double,
            val accuracy: Float,
            val speed: Float?,
            val mock: Boolean,
            val atMillis: Long,
        ) : Event
        data class Steps(val total: Int, val atMillis: Long) : Event
        data class Motion(val x: Float, val y: Float, val z: Float, val atMillis: Long) : Event
        /** The watchdog: is there still a ride worth listening for? */
        data object Tick : Event
    }

    private val locations = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            @Suppress("DEPRECATION")
            val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider
            events.trySend(
                Event.Fix(
                    location.latitude, location.longitude,
                    if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                    if (location.hasSpeed()) location.speed else null,
                    mock,
                    location.elapsedRealtimeNanos / 1_000_000L,
                ),
            )
        }

        @Deprecated("Older Android calls it; nothing to do.")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val at = event.timestamp / 1_000_000L
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> event.values.firstOrNull()?.let { events.trySend(Event.Steps(it.toInt(), at)) }
                Sensor.TYPE_ACCELEROMETER ->
                    events.trySend(Event.Motion(event.values[0], event.values[1], event.values[2], at))
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification(null))
            }
        }
        if (started.isFailure) {
            // Without the permission a location service may not start on
            // Android 14; the UI asks before starting this, so this is the
            // permission having gone in the meantime. Nothing to do here.
            stopSelf()
            return
        }
        lastCreditAt = System.currentTimeMillis()
        // A watchdog of its own, because the idle stop and the deadline
        // must not wait for a GPS fix that a switched-off GPS never sends.
        scope.launch {
            while (isActive) {
                delay(WATCHDOG_MILLIS)
                events.trySend(Event.Tick)
            }
        }
        scope.launch {
            for (event in events) {
                try {
                    when (event) {
                        is Event.Activity -> activityChanged(event.value)
                        is Event.Fix -> onFix(event)
                        is Event.Steps -> counter.observeSteps(event.total, event.atMillis)
                        is Event.Motion -> counter.observeMotion(event.x, event.y, event.z, event.atMillis)
                        Event.Tick -> if (overdueOrIdle()) stopSelf()
                    }
                } catch (error: Exception) {
                    Crash.report(this@RideService, error, "ride")
                }
            }
        }
        scope.launch { store.active.collect { events.send(Event.Activity(it)) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun activityChanged(activity: EarnActivity?) {
        val ride = activity?.takeIf { it.isRide }
        if (ride == null || (running != null && ride.id != running?.id)) {
            // Over, given up, or replaced: the lens on the world goes off.
            stopSelf()
            return
        }
        running = ride
        if (!listening) startListening()
    }

    private fun startListening() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val locationManager = getSystemService<LocationManager>()
        // Both permissions, or nothing: without the step counter a runner
        // at a bicycle's speed would be a cyclist, which the sheet says
        // is refused. The screens ask for both before starting this.
        if (!fine || locationManager == null || !Permissions.hasActivityRecognition(this)) {
            stopSelf()
            return
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, FIX_INTERVAL_MILLIS, 0f, locations, Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            stopSelf()
            return
        }
        // The step counter tells a run from a ride and the accelerometer a
        // bicycle from a car seat: a ride without either cannot be judged,
        // and is not started. The chooser dims the tile on such a phone.
        val sensors = getSystemService<SensorManager>()
        val steps = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val motion = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensed = sensors != null && steps != null && motion != null &&
            sensors.registerListener(sensorListener, steps, SensorManager.SENSOR_DELAY_NORMAL, SENSOR_LATENCY_MICROS) &&
            sensors.registerListener(sensorListener, motion, SensorManager.SENSOR_DELAY_NORMAL, SENSOR_LATENCY_MICROS)
        if (!sensed) {
            runCatching { locationManager.removeUpdates(locations) }
            runCatching { sensors?.unregisterListener(sensorListener) }
            stopSelf()
            return
        }
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        runCatching { getSystemService<LocationManager>()?.removeUpdates(locations) }
        runCatching { getSystemService<SensorManager>()?.unregisterListener(sensorListener) }
        listening = false
    }

    /**
     * True when there is no ride left to measure: its deadline has passed
     * (it is stood down, as the view model stands down an overdue walk) or
     * nothing has been credited for half an hour, which is a phone
     * forgotten with GPS on. Checked on every fix and on the watchdog.
     */
    private suspend fun overdueOrIdle(): Boolean {
        val activity = running ?: return true
        val now = System.currentTimeMillis()
        if (activity.expired(now)) {
            store.clearActive(activity.id)
            return true
        }
        return now - lastCreditAt > IDLE_STOP_MILLIS
    }

    private suspend fun onFix(fix: Event.Fix) {
        val activity = running ?: return
        if (overdueOrIdle()) {
            stopSelf()
            return
        }
        val earned = counter.observeFix(fix.latitude, fix.longitude, fix.accuracy, fix.speed, fix.mock, fix.atMillis)
        if (earned <= 0) return
        lastCreditAt = System.currentTimeMillis()
        val updated = activity.copy(progress = (activity.progress + earned).coerceAtMost(activity.target))
        if (!updated.isComplete) {
            store.update(updated)
            running = updated
            getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification(updated))
            return
        }
        stopListening()
        running = null
        if (store.complete(updated)) {
            EarnNotifications.completed(
                this, updated,
                channelId = MotionMonitor.CHANNEL_ID, channelName = "Runs and climbs",
                title = "Your ride is done.",
                requestCode = 22,
            )
            Analytics.log(Analytics.extraTimeEarned(updated.type))
            // Reported before the service goes: stopping first would cancel
            // this scope with the report still in it, and the ledger would
            // keep a pending ride that the phone had already paid out.
            withContext(Dispatchers.IO) {
                runCatching { Sync(this@RideService).completeActivity(updated, System.currentTimeMillis()) }
            }
        }
        stopSelf()
    }

    private fun createChannel() {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rides", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    /** The ongoing notice a foreground service must show: what is being measured, and how far it has got. */
    private fun notification(activity: EarnActivity?): Notification {
        val open = PendingIntent.getActivity(
            this, 22,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = activity?.let {
            String.format(Locale.US, "%.1f of %.1f km. GPS is on for the ride only.", it.progress / 1000.0, it.target / 1000.0)
        } ?: "GPS is on for the ride only."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle("Measuring your ride")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stopListening()
        events.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ride"
        private const val NOTIFICATION_ID = 22
        private const val FIX_INTERVAL_MILLIS = 1_000L
        private const val SENSOR_LATENCY_MICROS = 5_000_000

        /** Half an hour of no ride is a phone forgotten with GPS on. */
        private const val IDLE_STOP_MILLIS = 30L * 60 * 1000
        private const val WATCHDOG_MILLIS = 60L * 1000

        /**
         * Starts measuring the active ride; safe to call whenever the ride
         * is on screen, since a service already running only gets another
         * onStartCommand. The screens have checked both permissions.
         */
        fun start(context: Context) {
            val intent = Intent(context, RideService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
