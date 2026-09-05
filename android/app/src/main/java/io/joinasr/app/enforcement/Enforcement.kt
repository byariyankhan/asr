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
        /** The limit as it stands today, bonus minutes included. */
        val limitMinutes: Int,
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

    fun decide(
        pact: Pact?,
        snapshot: UsageSnapshot,
        /**
         * Minutes earned today, per package. Bonus time raises today's
         * allowance and never the pact: the limit somebody committed to is
         * still the limit tomorrow, which is the difference between earning
         * time and editing a promise.
         */
        earnedMinutes: Map<String, Int> = emptyMap(),
    ): Decision {
        if (pact == null || !pact.isEnforceable) return Decision.Allow
        val foreground = snapshot.foregroundPackage ?: return Decision.Allow
        val app = pact.appFor(foreground) ?: return Decision.Allow
        val used = snapshot.minutesByPackage[foreground] ?: 0
        val allowed = app.limitMinutes + (earnedMinutes[foreground] ?: 0)
        // At the limit, not past it: a twenty minute limit is spent when the
        // twentieth minute is complete. Waiting for twenty-one would give
        // everybody a free minute and make the number on the screen a lie.
        return if (used >= allowed) Decision.Block(app, used, allowed) else Decision.Allow
    }

    /**
     * Which controlled apps are past their allowance today.
     *
     * Reported, not punished. Going over a limit does not fail a challenge
     * and never did anything a person should lose a month of work over --
     * the app in front of them is blocked, and that is the whole remedy the
     * product has to offer.
     *
     * This used to be `breach`, and it ended the pact. The definition was
     * defensible on paper -- three minutes past a limit means the block did
     * not hold, which is somebody getting around it or the app failing --
     * and wrong in practice for two reasons.
     *
     * The first is that it cannot tell those two apart. A dropped launch, a
     * revoked permission, a poll that arrived late on a phone under load:
     * every one of them reads as three minutes past, and failing somebody's
     * thirty-day challenge for a bug in this app is not accountability.
     *
     * The second is worse and is what somebody actually hit. Usage is
     * counted from local midnight, so a challenge started at eleven at night
     * inherits the whole day -- forty minutes of Instagram against a limit
     * that was thirty minutes old. "Challenge failed, Day 1", for time spent
     * before the promise existed. A pact cannot be broken before it is made.
     *
     * So a challenge now ends only by something the person did on purpose:
     * finishing it, giving it up, removing the app, or turning protection
     * off. Every one of those is a decision. Scrolling is not.
     *
     * Every controlled app is checked, not only the one in front: a limit
     * spent an hour ago is still spent, and the witnesses' progress screen
     * reads these.
     */
    fun overLimit(
        pact: Pact?,
        snapshot: UsageSnapshot,
        earnedMinutes: Map<String, Int> = emptyMap(),
    ): List<PactApp> {
        if (pact == null || !pact.isEnforceable) return emptyList()
        return pact.apps.filter { app ->
            val used = snapshot.minutesByPackage[app.packageName] ?: 0
            used >= app.limitMinutes + (earnedMinutes[app.packageName] ?: 0)
        }
    }

    /**
     * How long to wait before looking again.
     *
     * The tension: the block screen should arrive the moment a limit is
     * reached, and polling costs battery all day whether or not anything is
     * happening. So the loop looks closely when it is nearly time — inside
     * the last two minutes of a watched app that is open — and idles
     * otherwise. Somebody who is nowhere near a limit is not worth a query a
     * second.
     *
     * With one exception, which used to be a hole big enough to walk
     * through. This answered from the app in front *right now*, so somebody
     * on their home screen got the idle delay -- and opening an app whose
     * day was already spent bought them however much of those fifteen
     * seconds was left before the loop next looked. Every time. A limit that
     * can be had fifteen seconds at a time is not a limit, it is a toll.
     *
     * So a spent app is watched as closely as an open one. It is one tap
     * away, and the tap is the only thing that has not happened yet.
     */
    fun pollDelayMillis(
        pact: Pact?,
        snapshot: UsageSnapshot,
        earnedMinutes: Map<String, Int> = emptyMap(),
    ): Long {
        if (pact == null || !pact.isEnforceable) return IDLE_MILLIS
        val foreground = snapshot.foregroundPackage
        val open = foreground?.let { pact.appFor(it) }
        if (open != null) {
            val allowed = open.limitMinutes + (earnedMinutes[open.packageName] ?: 0)
            val remaining = allowed - (snapshot.minutesByPackage[open.packageName] ?: 0)
            return if (remaining <= CLOSE_MINUTES) CLOSE_MILLIS else WATCHING_MILLIS
        }
        // Nothing limited is in front. Whether that is worth idling through
        // depends on what is waiting: an app with nothing left today has to
        // be blocked on the tap, not a quarter of a minute after it.
        return if (overLimit(pact, snapshot, earnedMinutes).isEmpty()) IDLE_MILLIS else CLOSE_MILLIS
    }

    /** Within this many minutes of a limit -- or with a limit already spent
     *  and the app one tap away -- the loop watches closely. */
    const val CLOSE_MINUTES = 2

    /** A watched app is open and nearly out of time. */
    const val CLOSE_MILLIS = 1_000L

    /** A watched app is open with time to spare. */
    const val WATCHING_MILLIS = 5_000L

    /** Nothing under a limit is in front of the person. */
    const val IDLE_MILLIS = 15_000L
}
