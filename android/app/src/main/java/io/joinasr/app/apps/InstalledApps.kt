package io.joinasr.app.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about this phone's apps that only the phone can answer.
 *
 * The visibility rules matter here. From Android 11 an app sees almost
 * nothing of what else is installed unless it says what it is looking for,
 * and the blunt instrument for that — QUERY_ALL_PACKAGES — is a restricted
 * permission Play grants by exception and rejects for most reasons. The
 * `<queries>` block in the manifest asks for exactly one thing instead:
 * activities that answer MAIN/LAUNCHER, which is the same set a launcher
 * shows, and is precisely what a person means by "my apps".
 */
object InstalledApps {

    /** Icons are rasterised once at this size rather than per frame. */
    const val ICON_PX = 128

    /**
     * Every app with a launcher entry, minus the ones that must never be
     * blockable, in the order screen 06 shows them.
     *
     * Labels come from the app's own resources, so this reads a few hundred
     * of them; it belongs off the main thread and the caller gets a suspend
     * function rather than a promise to be careful.
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = queryActivities(pm, launcherIntent)
        val entries = launchable
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString().trim()) }
            // One package can publish several launcher activities. The person
            // chooses an app, not an activity, so the extras are duplicates.
            .distinctBy { it.packageName }
            .filter { it.label.isNotEmpty() }
        AppCatalog.offerable(entries, undeniable(context))
    }

    /**
     * The apps this device would be dangerous or impossible to use without,
     * asked of the device rather than guessed from package names: a phone
     * can ship any dialer, any launcher, and manufacturers rename Settings.
     *
     * Everything here is best effort. A phone with no telephony has no
     * dialer to protect, and that is not an error.
     */
    fun undeniable(context: Context): Set<String> {
        val pm = context.packageManager
        val names = mutableSetOf<String>()

        names += context.packageName

        // The launcher. Blocking it means a person cannot reach any app at
        // all, including this one.
        queryActivities(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .mapTo(names) { it.activityInfo.packageName }

        // Settings, under whatever name this manufacturer ships it. This is
        // where every permission Asr holds is revoked; block it and the app
        // becomes something a person cannot switch off.
        resolvePackage(pm, Intent(Settings.ACTION_SETTINGS))?.let(names::add)
        resolvePackage(pm, Intent(Settings.ACTION_APPLICATION_SETTINGS))?.let(names::add)

        // Calls and messages. A blocked dialer is a phone that cannot call
        // for help, which is not a trade-off worth any amount of focus.
        context.getSystemService<TelecomManager>()?.defaultDialerPackage?.let(names::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(names::add)

        return names
    }

    /**
     * One app's icon, rasterised. Null when the app has none or the drawable
     * cannot be turned into a bitmap; the row draws a lettered tile then,
     * which is what the design shows anyway.
     */
    suspend fun icon(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_PX, ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())

    private fun resolvePackage(pm: PackageManager, intent: Intent): String? =
        queryActivities(pm, intent).firstOrNull()?.activityInfo?.packageName
}
