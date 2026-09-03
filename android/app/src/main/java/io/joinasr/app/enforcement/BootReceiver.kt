package io.joinasr.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the loop again after the phone restarts.
 *
 * Without this, every reboot would quietly end somebody's limits until they
 * next opened Asr, and a person who wanted out would learn that restarting
 * the phone is the way. A commitment that survives only until the next
 * restart is not one.
 *
 * It starts the service unconditionally rather than reading the pact here
 * first. Reading it is asynchronous, a receiver has about ten seconds, and
 * the service already stops itself when there is nothing to enforce -- so
 * the simple path is also the reliable one.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Not LOCKED_BOOT_COMPLETED: the pact lives in credential-encrypted
        // storage, which is unreadable until the phone is first unlocked, so
        // starting there would only produce a service with nothing to read.
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            // Sent to an app after it is updated or reinstalled. The service
            // is killed by both, and nothing else would bring it back.
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (relevant) EnforcementService.start(context)
    }
}
