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
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.usage.Day
import io.joinasr.app.usage.UsageReader
import io.joinasr.app.usage.usageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * The loop. Reads how long each app has been used, compares it with the
 * pact, and puts the block screen up or takes it down.
 *
 * A foreground service because that is the only kind Android will keep
 * running, and because a person whose apps can be blocked should be able to
 * see at a glance that something is watching. The notification is not a
 * formality here; it is the honest disclosure that the app is running.
 *
 * It holds no state of its own beyond the pact it last read. Everything that
 * decides anything is in [Enforcement], and everything that measures
 * anything is in [UsageReader], both of which are tested. What is left here
 * is the parts only a device can do: staying alive, drawing a window, and
 * knowing when a permission has gone away.
 */
class EnforcementService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var reader: UsageReader
    private lateinit var overlay: BlockOverlay
    private lateinit var store: PactStore

    @Volatile
    private var pact: Pact? = null

    @Volatile
    private var hadUsageAccess = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        reader = usageReader(this)
        overlay = BlockOverlay(this)
        store = PactStore(this)

        createChannel()
        startInForeground(apps = 0)

        store.pact.onEach { current ->
            pact = current
            if (current == null) {
                // Nothing to enforce. Not an error, and not something to sit
                // in the notification shade over: the challenge is finished
                // or was never started.
                withContext(Dispatchers.Main) { overlay.hide() }
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
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val current = pact
            val hasAccess = Permissions.hasUsageAccess(this)

            // Usage access can be revoked in Settings at any moment, and
            // without it the system reports an empty event stream rather
            // than an error -- so everything measured after that point would
            // read as zero and every limit would silently stop working.
            // Detecting the return of the permission and starting the day's
            // count again is the only honest thing to do with it.
            if (hasAccess && !hadUsageAccess) reader.reset()
            hadUsageAccess = hasAccess

            if (current == null || !hasAccess) {
                withContext(Dispatchers.Main) { overlay.hide() }
                delay(Enforcement.IDLE_MILLIS)
                continue
            }

            val snapshot = reader.poll()
            when (val decision = Enforcement.decide(current, snapshot)) {
                Decision.Allow -> withContext(Dispatchers.Main) { overlay.hide() }

                is Decision.Block -> {
                    val icon = InstalledApps.icon(this, decision.app.packageName)
                    val blocked = BlockedState(
                        app = decision.app,
                        usedMinutes = decision.usedMinutes,
                        icon = icon,
                        availableAgain = nextResetText(),
                    )
                    withContext(Dispatchers.Main) { overlay.show(blocked) }
                }
            }

            delay(Enforcement.pollDelayMillis(current, snapshot))
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.protection_channel_name),
            // Low: it must be visible and permanent, and it must never make
            // a sound. A commitment app that pings all day gets muted, and a
            // muted foreground notification is one nobody reads.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.protection_channel_description)
            setShowBadge(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun startInForeground(apps: Int) {
        val notification = buildNotification(apps)
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Starts the loop, if there is anything for it to do.
         *
         * Safe to call as often as anything likes: starting a service that
         * is already running only delivers another onStartCommand, and the
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
