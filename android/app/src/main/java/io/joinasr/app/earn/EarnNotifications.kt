package io.joinasr.app.earn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.joinasr.app.MainActivity
import io.joinasr.app.R

/**
 * The one notification an activity measured in the background sends: it
 * is done, and the minutes exist. The person is not looking at the app
 * when a phone-free session or a run completes, which is the point of
 * both, so this is how they hear.
 */
object EarnNotifications {

    fun completed(
        context: Context,
        activity: EarnActivity,
        channelId: String,
        channelName: String,
        title: String,
        requestCode: Int,
    ) {
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "You earned +${activity.rewardMinutes} minutes for ${activity.appLabel} today."
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // The existing notification grant applies. Denial never loses a reward;
        // the stored completion is also shown when the person returns to Asr.
        runCatching { NotificationManagerCompat.from(context).notify(channelId, requestCode, notification) }
    }
}
