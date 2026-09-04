package io.joinasr.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.joinasr.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The registration token this phone is reachable at.
 *
 * Every notification in this product is a push. The server queues one row
 * per witness and the watchdog delivers it through FCM; email carries only
 * sign-up verification and password resets. So a phone with no token here is
 * a phone nobody can reach — the watchdog marks those notifications
 * `unregistered` and gives up on them, which is exactly what was happening
 * before this file existed.
 *
 * Everything is guarded rather than assumed. Without google-services.json,
 * FirebaseApp never initialises and every call here answers null instead of
 * throwing, because a person whose challenge is being enforced should not
 * lose the enforcement over a missing notification.
 */
object Push {

    /** Whether this build has Firebase config at all. */
    val configured: Boolean get() = BuildConfig.FIREBASE_CONFIGURED

    /**
     * True once Firebase has actually come up on this device.
     *
     * Separate from [configured]: the config can be present and
     * initialisation still fail — no Play services on the device, a
     * mismatched package name — and the difference matters when working out
     * why somebody is not being told anything.
     */
    fun available(context: Context): Boolean {
        if (!configured) return false
        return runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }

    /**
     * The current token, or null.
     *
     * Fetching involves a round trip to Google the first time, which is why
     * this is suspending and why the result is only ever used to fill in a
     * field on a request that was going to be made anyway.
     */
    suspend fun token(context: Context): String? {
        if (!available(context)) return null
        return runCatching { awaitToken() }.getOrNull()
    }

    private suspend fun awaitToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (continuation.isActive) {
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
            }
    }
}
