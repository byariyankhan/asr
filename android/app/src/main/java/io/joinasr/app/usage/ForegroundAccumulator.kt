package io.joinasr.app.usage

/**
 * How long each app has been in front of the person today.
 *
 * This is the measurement the whole product rests on, so it is worth saying
 * why it is done the hard way. Android will answer "how long was this app in
 * the foreground" directly, through `UsageStats.totalTimeInForeground`, and
 * that answer is bucketed into intervals the system chooses. The buckets do
 * not begin at the person's midnight, they are rewritten as the day goes on,
 * and on several manufacturers they are simply wrong. A limit enforced from
 * them is a limit that fires at the wrong minute, and the first time that
 * happens to somebody mid-sentence they stop trusting the app.
 *
 * So the events are walked instead. One app is in the foreground at a time,
 * so the rule is simply: something resuming closes whatever was open, and
 * the time between opening and closing belongs to the app that was open.
 *
 * The accumulator is fed forward — each poll hands it only what has happened
 * since the last one — because re-reading a whole day of events every few
 * seconds is work the phone can feel. It keeps the running total, and the
 * app that is still open, across those calls.
 *
 * Everything is clamped into the day. An app left open across midnight is
 * the case that catches naive implementations: its resume happened
 * yesterday, so the caller reads a little way back past midnight, and
 * clamping turns "resumed at 23:50, paused at 00:05" into the five minutes
 * that actually belong to today.
 */
class ForegroundAccumulator(private val dayStartMillis: Long) {

    private val totals = mutableMapOf<String, Long>()
    private var openPackage: String? = null
    private var openedAt: Long = dayStartMillis

    /** How far the events fed in so far reach. */
    var cursorMillis: Long = dayStartMillis
        private set

    /**
     * Fold in everything that happened up to [upTo]. Events are sorted here
     * rather than trusted to arrive in order: the system's cursor is
     * ordered, but a caller merging two queries is not, and one event out of
     * place would silently mis-time a whole session.
     */
    fun add(events: List<UsageEvent>, upTo: Long) {
        for (event in events.sortedBy { it.timestampMillis }) {
            if (event.timestampMillis > upTo) continue
            val at = event.timestampMillis.coerceAtLeast(dayStartMillis)
            when (event.kind) {
                UsageEvent.Kind.Resumed -> {
                    close(at)
                    openPackage = event.packageName
                    openedAt = at
                }

                UsageEvent.Kind.Paused ->
                    if (openPackage == event.packageName) close(at)

                UsageEvent.Kind.Interrupted -> close(at)
            }
        }
        cursorMillis = maxOf(cursorMillis, upTo)
    }

    /**
     * Milliseconds each app has been in front, as of [nowMillis], including
     * the app that is still open right now. Apps with no time are absent
     * rather than present with a zero.
     */
    fun millisByPackage(nowMillis: Long): Map<String, Long> {
        val result = totals.toMutableMap()
        val open = openPackage
        if (open != null) {
            val elapsed = (nowMillis - openedAt).coerceAtLeast(0)
            if (elapsed > 0) result[open] = (result[open] ?: 0) + elapsed
        }
        return result
    }

    /**
     * The same thing in whole minutes, rounded down.
     *
     * Down, not to nearest: a limit of fifteen minutes should be reached
     * when fifteen have passed, not when fourteen and a half have. Being
     * blocked half a minute early is the version of this bug a person
     * notices and calls broken.
     */
    fun minutesByPackage(nowMillis: Long): Map<String, Int> =
        millisByPackage(nowMillis).mapValues { (_, millis) -> (millis / 60_000L).toInt() }

    /** Whatever is in front right now, or null if nothing is. */
    fun foregroundPackage(): String? = openPackage

    private fun close(at: Long) {
        val open = openPackage ?: return
        val elapsed = at - openedAt
        if (elapsed > 0) totals[open] = (totals[open] ?: 0) + elapsed
        openPackage = null
    }
}
