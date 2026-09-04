package io.joinasr.app.enforcement

import io.joinasr.app.usage.UsageSnapshot

/** What the loop should do about what it just saw. */
sealed interface Decision {
    /** Nothing in front of the person is over its limit. Take the block screen down. */
    data object Allow : Decision

    /** Show the block screen over [app], which has run out of time today. */
    data class Block(
        val app: PactApp,
        val usedMinutes: Int,
    ) : Decision
}

/**
 * The rule the whole app comes down to, kept as one pure function so it can
 * be argued with in tests rather than in production.
 *
 * It deliberately decides on nothing but what is in front of the person
 * right now. An app that is over its limit but not open is not blocked,
 * because there is nothing to block: covering the screen while somebody is
 * reading their messages would be the app punishing them for a limit they
 * are not currently exceeding.
 */
object Enforcement {

    fun decide(pact: Pact?, snapshot: UsageSnapshot): Decision {
        if (pact == null || !pact.isEnforceable) return Decision.Allow
        val foreground = snapshot.foregroundPackage ?: return Decision.Allow
        val app = pact.appFor(foreground) ?: return Decision.Allow
        val used = snapshot.minutesByPackage[foreground] ?: 0
        // At the limit, not past it: a twenty minute limit is spent when the
        // twentieth minute is complete. Waiting for twenty-one would give
        // everybody a free minute and make the number on the screen a lie.
        return if (used >= app.limitMinutes) Decision.Block(app, used) else Decision.Allow
    }

    /**
     * Whether the pact has been broken, and by what.
     *
     * The block screen goes up the moment a limit is reached, so simply
     * reaching one is not a breach — it is the app working. A breach is the
     * block *failing to hold*: the app kept being used for
     * [BREACH_GRACE_MINUTES] minutes beyond a limit that was supposed to
     * stop it, which happens when a permission was revoked, the launch was
     * dropped by the system, or the person found a way past the screen.
     * That is the honest thing to call a broken pact, and it is the only
     * definition this architecture can actually measure.
     *
     * Every controlled app is checked, not just the one in front: a limit
     * blown through an hour ago is still blown through now, and waiting for
     * the person to reopen that app to notice would be pretending.
     *
     * The grace is not generosity. It is the distance between "the loop was
     * a poll behind" and "nothing stopped this", and three minutes is wide
     * enough that no correctly working block can cross it -- the loop polls
     * every second inside the last two minutes of a limit -- and narrow
     * enough that somebody scrolling past a broken block reaches it almost
     * at once.
     */
    fun breach(pact: Pact?, snapshot: UsageSnapshot, nowMillis: Long, dayNumber: Int): Breach? {
        if (pact == null || !pact.isEnforceable) return null
        for (app in pact.apps) {
            val used = snapshot.minutesByPackage[app.packageName] ?: 0
            if (used >= app.limitMinutes + BREACH_GRACE_MINUTES) {
                return Breach(
                    packageName = app.packageName,
                    label = app.label,
                    limitMinutes = app.limitMinutes,
                    usedMinutes = used,
                    atMillis = nowMillis,
                    dayNumber = dayNumber,
                )
            }
        }
        return null
    }

    /**
     * How long to wait before looking again.
     *
     * The tension: the block screen should arrive the moment a limit is
     * reached, and polling costs battery all day whether or not anything is
     * happening. So the loop looks closely only when it is nearly time —
     * inside the last two minutes of a watched app that is currently open —
     * and idles otherwise. Somebody who is nowhere near a limit is not worth
     * a query a second.
     */
    fun pollDelayMillis(pact: Pact?, snapshot: UsageSnapshot): Long {
        if (pact == null || !pact.isEnforceable) return IDLE_MILLIS
        val foreground = snapshot.foregroundPackage ?: return IDLE_MILLIS
        val app = pact.appFor(foreground) ?: return IDLE_MILLIS
        val remaining = app.limitMinutes - (snapshot.minutesByPackage[foreground] ?: 0)
        return if (remaining <= CLOSE_MINUTES) CLOSE_MILLIS else WATCHING_MILLIS
    }

    /** Within this many minutes of a limit, the loop watches closely. */
    const val CLOSE_MINUTES = 2

    /** Minutes past a limit at which the block has demonstrably not held. */
    const val BREACH_GRACE_MINUTES = 3

    /** A watched app is open and nearly out of time. */
    const val CLOSE_MILLIS = 1_000L

    /** A watched app is open with time to spare. */
    const val WATCHING_MILLIS = 5_000L

    /** Nothing under a limit is in front of the person. */
    const val IDLE_MILLIS = 15_000L
}
