package io.joinasr.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.joinasr.app.MainActivity
import io.joinasr.app.R
import io.joinasr.app.sync.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where every notification in this product arrives.
 *
 * The server writes one notification row per witness who asked for that
 * kind, and the watchdog delivers it here through FCM. Email carries only
 * sign-up verification and password resets; everything a witness is told —
 * a pact started, a pact broken, protection gone dark, a reaction — comes
 * through this class.
 *
 * The words are the server's. Title and body arrive written and are posted
 * as they came, because they are the same sentence the inbox shows on Figma
 * 19 and the only version anybody will ever quote back.
 */
class AsrMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A new token, which happens on install, on restore to a new phone, and
     * whenever Firebase decides to rotate one.
     *
     * Sent immediately rather than waiting for the next app start: between a
     * rotation and the next launch is exactly the window in which somebody's
     * pact might break, and a witness told about it a day late has been told
     * about a different thing.
     */
    override fun onNewToken(token: String) {
        scope.launch { runCatching { Sync(applicationContext).registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title ?: message.data["title"] ?: return
        val body = notification?.body ?: message.data["body"].orEmpty()
        show(this, title = title, body = body, deepLink = message.data["deep_link"])
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "alerts"

        /**
         * The channel these arrive on.
         *
         * Its own, separate from the permanent protection notification, so
         * somebody who silences the one that never goes away has not also
         * silenced the one telling them their brother broke his pact.
         */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alerts_channel_description)
            }
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        /**
         * Posts one.
         *
         * The id is derived from the deep link so a second notification
         * about the same person replaces the first rather than stacking; a
         * witness with a chatty friend should not come back to fourteen rows
         * saying the same thing.
         */
        fun show(context: Context, title: String, body: String, deepLink: String?) {
            createChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                (deepLink ?: title).hashCode(),
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val built = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_protection)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .build()
            // Posting without the permission throws on Android 13; there is
            // nothing useful to do about it here, and the app already asks
            // for it during setup.
            runCatching {
                NotificationManagerCompat.from(context).notify((deepLink ?: title).hashCode(), built)
            }
        }
    }
}
