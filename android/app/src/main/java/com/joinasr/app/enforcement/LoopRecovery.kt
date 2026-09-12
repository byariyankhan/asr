package com.joinasr.app.enforcement

/**
 * What the enforcement loop does after a pass that threw.
 *
 * The loop used to answer a failed pass with `continue` and no pause at all
 * on the screen-off path, which is only safe while failures are one-offs. A
 * failure that keeps happening -- a store that cannot be written, a system
 * service that keeps refusing -- turned that path into a bare `while (true)`
 * with a crash report inside it: one core pinned for as long as the phone
 * was on, memory climbing, and Crashlytics handed the same exception
 * thousands of times an hour. The loop is the whole product and it runs for
 * days, so the cost of getting this wrong is paid in somebody's battery and
 * eventually in the process being killed.
 *
 * Two decisions, both pure, both here so they can be argued with in a test
 * rather than inferred from the service.
 */
object LoopRecovery {

    /**
     * How long to wait after the [failures]th failure in a row.
     *
     * The first wait is the loop's own idle interval, so a single unlucky
     * pass costs exactly what it used to. After that it doubles, because a
     * second failure means the first was not bad luck, and the more certain
     * it is that nothing will work the less there is to gain from asking
     * again soon. Capped, because the loop must come back on its own when
     * whatever it was clears.
     */
    fun backoffMillis(failures: Int): Long {
        if (failures <= 1) return FIRST_BACKOFF_MILLIS
        val doublings = (failures - 1).coerceAtMost(MAX_DOUBLINGS)
        return (FIRST_BACKOFF_MILLIS shl doublings).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    /**
     * Whether the [failures]th failure in a row is one to report.
     *
     * The 1st, 2nd, 4th, 8th, 16th and so on: enough to see in Crashlytics
     * that something failed, and then that it is still failing, without the
     * report itself becoming the load. A power of two is the cheapest way to
     * say "ever more rarely" and needs no state of its own.
     */
    fun shouldReport(failures: Int): Boolean =
        failures > 0 && failures and (failures - 1) == 0

    /** The idle interval, so one bad pass waits what a quiet one waits. */
    const val FIRST_BACKOFF_MILLIS = Enforcement.IDLE_MILLIS

    /** Five minutes. Long enough to cost nothing, short enough to recover. */
    const val MAX_BACKOFF_MILLIS = 5L * 60 * 1000

    private const val MAX_DOUBLINGS = 16
}
