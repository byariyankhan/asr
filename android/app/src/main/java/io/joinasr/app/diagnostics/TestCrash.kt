package io.joinasr.app.diagnostics

import android.content.Context

/**
 * TEMPORARY. Remove once a report from a real phone has been seen in the
 * Firebase console; nothing else depends on this file.
 *
 * A hidden way to send Crashlytics a test report from a phone: seven taps on
 * the version line at the bottom of Help & Support ask first, then [crash]
 * throws on the main thread and the process dies. Crashlytics writes the
 * report to disk as that happens and uploads it on the NEXT launch, which is
 * why the check ends with opening the app again. Opening the question also
 * records a non-fatal through [Crash.report], the path the enforcement loop
 * uses, so one visit exercises both pipelines.
 */
object TestCrash {
    /** Taps on the version line before the question is asked. */
    const val TAPS = 7

    /** A caught failure, reported the way the enforcement loop reports its own. */
    fun nonFatal(context: Context) {
        Crash.report(context, IllegalStateException("Asr test non-fatal (Crashlytics check)"), "test_crash")
    }

    /** Does not return: an uncaught exception on the main thread. */
    fun crash(): Nothing {
        throw RuntimeException("Asr test crash (Crashlytics check)")
    }
}
