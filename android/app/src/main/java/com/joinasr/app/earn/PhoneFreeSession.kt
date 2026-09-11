package com.joinasr.app.earn

/**
 * One uninterrupted lock-screen interval, measured with elapsedRealtime
 * (which includes sleep and cannot be advanced in Settings).
 *
 * Deliberately not restored: after a service/process restart we cannot prove
 * there were no unlocks while the receiver was absent. Start a fresh interval.
 * Display state, foreground apps and call state have no part in this rule.
 */
class PhoneFreeSession(private val durationMillis: Long) {
    var lockedSinceElapsed: Long? = null
        private set
    var complete: Boolean = false
        private set

    val deadlineElapsed: Long? get() = lockedSinceElapsed?.plus(durationMillis)

    /** userPresent is authoritative even if a keyguard query briefly lags it. */
    fun observe(nowElapsed: Long, keyguardLocked: Boolean, userPresent: Boolean = false) {
        if (complete) return
        val since = lockedSinceElapsed
        if (since != null && nowElapsed < since) {
            lockedSinceElapsed = null
        } else if (since != null && nowElapsed - since >= durationMillis) {
            // An unlock at/after the deadline must not take back earned time,
            // including when Doze deferred the completion alarm.
            complete = true
            return
        }
        if (userPresent || !keyguardLocked) {
            lockedSinceElapsed = null
        } else if (lockedSinceElapsed == null) {
            lockedSinceElapsed = nowElapsed
        }
    }
}
