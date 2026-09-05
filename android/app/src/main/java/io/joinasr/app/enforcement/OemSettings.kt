package io.joinasr.app.enforcement

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * The manufacturer's own "let this app keep running" screen, where there is
 * one.
 *
 * Android's battery optimisation is one switch. Several manufacturers add a
 * second layer of their own -- autostart on Xiaomi, app launch management on
 * Huawei, sleeping apps on Samsung -- and it is that layer, not Android's,
 * that stops a foreground service the moment the screen goes off. A limit
 * enforced by a service that is not running is not a limit, and a phone
 * that has stopped heartbeating tells the witnesses a day later that the
 * person went dark, about somebody who changed nothing.
 *
 * None of these screens has a public intent. The component names below are
 * the ones the community has kept track of for years (dontkillmyapp.com);
 * they are tried in order and the first one this phone actually has is
 * opened. When none resolves, the caller falls back to the app's own
 * details page, where the same setting lives under another name.
 */
object OemSettings {

    class Guide(
        /** The manufacturer, as the screen should name it. */
        val brand: String,
        /** What to switch on once there, in the manufacturer's own words. */
        val steps: String,
        internal val intents: List<Intent>,
    )

    fun guideFor(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Guide? {
        val key = "$manufacturer $brand".lowercase()
        return when {
            "xiaomi" in key || "redmi" in key || "poco" in key -> Guide(
                brand = "Xiaomi",
                steps = "Autostart: on. Battery saver: No restrictions. Under Other permissions, " +
                    "allow \"Display pop-up windows while running in the background\".",
                intents = listOf(
                    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                ),
            )

            "huawei" in key || "honor" in key -> Guide(
                brand = "Huawei",
                steps = "App launch: Manage manually, with Auto-launch, Secondary launch and Run in " +
                    "background all on.",
                intents = listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
            )

            "oppo" in key || "realme" in key || "oneplus" in key -> Guide(
                brand = "Oppo / Realme / OnePlus",
                steps = "Allow auto-launch, and under Battery choose Allow background activity.",
                intents = listOf(
                    component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ),
            )

            "vivo" in key || "iqoo" in key -> Guide(
                brand = "Vivo",
                steps = "Autostart: on. Under Battery, allow high background power consumption.",
                intents = listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ),
            )

            "samsung" in key -> Guide(
                brand = "Samsung",
                steps = "Battery: Unrestricted. In Device care, take Asr out of Sleeping apps and " +
                    "Deep sleeping apps, and add it to Never sleeping apps.",
                intents = listOf(
                    component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ),
            )

            "asus" in key -> Guide(
                brand = "Asus",
                steps = "Auto-start manager: allow Asr. PowerMaster: do not clean it up.",
                intents = listOf(
                    component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
                    component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                ),
            )

            else -> null
        }
    }

    /**
     * Opens the first of the guide's screens this phone has. False when it
     * has none of them, so the caller can offer the app's own page instead.
     */
    @Suppress("DEPRECATION")
    fun open(context: Context, guide: Guide): Boolean {
        for (intent in guide.intents) {
            val here = runCatching { context.packageManager.resolveActivity(intent, 0) != null }
                .getOrDefault(false)
            if (!here) continue
            val opened = runCatching {
                context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return true
        }
        return false
    }

    private fun component(packageName: String, className: String): Intent =
        Intent().setComponent(ComponentName(packageName, className))
}
