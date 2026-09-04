package io.joinasr.app.enforcement

import io.joinasr.app.sync.PendingEvent

/**
 * What ending a challenge produces: something to keep and something to send.
 *
 * A challenge can end three ways and the two halves have to agree in all
 * three. The outcome is what the person is shown and what their history is
 * built from; the event is what their witnesses are told. Building them in
 * one place is what stops a screen saying "you gave up" while the message
 * that went out said the limit broke.
 *
 * The reason on a failure is not decoration. The server requires one on a
 * `broken` event, and it is what picks the sentence each witness reads --
 * `limit_exceeded` and `user_gave_up` are different things to be told, in
 * the same way that being caught and turning yourself in are.
 */
data class Ending(val outcome: PactOutcome, val event: PendingEvent)

object Endings {

    /** The limit did not hold. */
    fun broken(
        pact: Pact,
        breach: Breach,
        witnesses: Int,
        eventId: String,
        nowMillis: Long,
    ) = Ending(
        outcome = outcome(pact, PactResult.Failed, breach, witnesses, nowMillis),
        event = PendingEvent(
            id = eventId,
            type = "broken",
            reason = "limit_exceeded",
            appPackage = breach.packageName,
            occurredAtMillis = nowMillis,
        ),
    )

    /**
     * They stopped it themselves.
     *
     * Still a failure, and still reported. The alternative to having this at
     * all is not a challenge nobody quits -- it is a challenge people quit by
     * uninstalling, which loses their history, tells their witnesses the
     * harshest thing there is to be told, and takes the person with it. A
     * door somebody can walk out of is what makes the room a choice.
     *
     * No breach, because nothing was breached: no limit was exceeded and no
     * app is to blame. The ending screen reads the absence and says the
     * challenge ended early rather than inventing a number.
     */
    fun gaveUp(
        pact: Pact,
        witnesses: Int,
        eventId: String,
        nowMillis: Long,
    ) = Ending(
        outcome = outcome(pact, PactResult.Failed, breach = null, witnesses, nowMillis),
        event = PendingEvent(
            id = eventId,
            type = "broken",
            reason = "user_gave_up",
            appPackage = null,
            occurredAtMillis = nowMillis,
        ),
    )

    /** They reached the end of it. */
    fun completed(
        pact: Pact,
        witnesses: Int,
        eventId: String,
        nowMillis: Long,
    ) = Ending(
        outcome = outcome(pact, PactResult.Completed, breach = null, witnesses, nowMillis),
        event = PendingEvent(
            id = eventId,
            type = "completed",
            reason = null,
            appPackage = null,
            occurredAtMillis = nowMillis,
        ),
    )

    private fun outcome(
        pact: Pact,
        result: PactResult,
        breach: Breach?,
        witnesses: Int,
        nowMillis: Long,
    ) = PactOutcome(
        result = result,
        startedAtMillis = pact.startedAtMillis,
        endedAtMillis = nowMillis,
        durationDays = pact.durationDays,
        apps = pact.apps,
        breach = breach,
        witnesses = witnesses,
        reported = false,
    )
}
