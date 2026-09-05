package io.joinasr.app.diagnostics

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.joinasr.app.push.Push

/**
 * Where a failure the app survived is written down.
 *
 * Crashes report themselves once the Crashlytics SDK is in the build. The
 * failures that matter most here do not crash anything: the enforcement
 * loop catches everything, because a loop that dies takes every limit with
 * it -- and until now what it caught went nowhere. A block screen that would
 * not appear on some phones was a rumour for weeks because no phone could
 * say so.
 *
 * Guarded the way Push is: without google-services.json Firebase never
 * initialises, and asking Crashlytics for an instance then throws. A phone
 * whose challenge is being enforced must not lose the enforcement over a
 * missing crash reporter.
 */
object Crash {

    /** A failure that was caught and survived, with the place it happened. */
    fun report(context: Context, error: Throwable, where: String) {
        if (!Push.available(context)) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("where", where)
            crashlytics.recordException(error)
        }
    }
}
