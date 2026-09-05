package io.joinasr.app.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * The three permissions the enforcement loop needs, and how to ask for them.
 *
 * None of them is an ordinary runtime permission that a dialog can grant.
 * Usage access and overlay are "special access" settings, granted in a
 * Settings screen the app can only open; notifications is a normal runtime
 * permission, but only from Android 13. So this file is checks and intents,
 * and every screen that shows a state has to re-read it when the person
 * comes back from Settings -- the system tells us nothing.
 */
object Permissions {

    /**
     * Whether PACKAGE_USAGE_STATS has actually been granted.
     *
     * checkSelfPermission is useless here: the permission is declared in the
     * manifest and is always "granted" in that sense, while the thing that
     * decides is an app-op the person toggles in Settings. AppOps is the only
     * honest answer.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        // MODE_DEFAULT means "no explicit answer", which for this op means the
        // person has not granted it. Treating it as granted is a common bug
        // and produces a service that reads zero minutes forever.
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Settings has no per-app usage-access page, so this lands on the list of
     * apps and the person finds ours. Some manufacturers ship a device with
     * the screen missing entirely, which is why callers must handle the
     * intent not resolving rather than crashing on it.
     */
    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Both grants the loop needs, as one answer. This is what a heartbeat
     * reports as `protection_enabled`, because it is what a witness would be
     * trusting: usage access is what makes anything measurable, the overlay
     * grant is what makes anything blockable, and either one missing is a
     * challenge nothing enforces.
     */
    fun protectionOn(context: Context): Boolean = hasUsageAccess(context) && canDrawOverlays(context)

    /**
     * Whether Android has agreed to leave this app alone when the phone
     * dozes. Not required for the loop -- it only runs while the screen is
     * on -- but a service on a phone that restricts the app is the one that
     * gets killed at the first opportunity and not brought back.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return true
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
    }

    /**
     * The list every phone has, rather than the per-app request dialog. The
     * dialog needs a permission Play grants by exception and refuses for
     * most reasons; the list needs nothing, and the person finds Asr on it.
     */
    fun batteryOptimizationIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** The app's own Settings page, where Battery lives under some name on every phone. */
    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

    fun overlayIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    )

    /**
     * Below Android 13 notifications need no permission, but the person can
     * still have switched them off for the app -- which for this app means
     * they never learn a pact broke. Both cases answer the same question, so
     * both are checked.
     */
    /**
     * Whether the app may read the step counter.
     *
     * A runtime permission from API 29, and granted at install below that.
     * Only asked for when somebody chooses a walking activity, which is why
     * it is not part of [PermissionState]: a challenge runs perfectly
     * without it, and a permission sheet at launch for a feature nobody has
     * opened is how apps get denied everything.
     */
    fun hasActivityRecognition(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        val manager = context.getSystemService<NotificationManager>() ?: return false
        return manager.areNotificationsEnabled()
    }

    /** True where a runtime dialog exists; below 13 the only route is Settings. */
    val notificationsAreRequestable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** For "switched off in Settings" on any version: the app's own page. */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}

/** What the three rows on the protection screen show. */
data class PermissionState(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    /** Whether Android has agreed not to restrict the app. Recommended, like notifications. */
    val batteryUnrestricted: Boolean = true,
) {
    /** Notifications are recommended, not required: losing them costs the
     *  witness updates, not the limits themselves. */
    val requiredGranted: Boolean get() = usageAccess && overlay

    companion object {
        fun read(context: Context) = PermissionState(
            usageAccess = Permissions.hasUsageAccess(context),
            overlay = Permissions.canDrawOverlays(context),
            notifications = Permissions.hasNotifications(context),
            batteryUnrestricted = Permissions.isIgnoringBatteryOptimizations(context),
        )
    }
}
