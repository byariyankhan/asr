package io.joinasr.app.enforcement

import io.joinasr.app.sync.PendingEvent
import io.joinasr.app.witness.Witness

/**
 * What ending a challenge produces: something to keep and something to send.
 *
 * The outcome is what the person is shown and what their history is built
 * from; the event is what their witnesses are told. Building them in one
 * place is what stops a screen saying "you gave up" while the message that
 * went out said something else.
 *
 * There are two ways a challenge ends on this phone, and both are
 * deliberate: the person reached the end of it, or the person stopped it.
 * There used to be a third -- a limit the block failed to hold, reported as
 * `limit_exceeded` -- and it was removed with the rule behind it. Going over
 * a limit is blocked and reported, not punished; `Enforcement.overLimit`
 * has the argument. The other two failures, removing the app and turning
 * protection off, are the server's to notice, because a phone that has done
 * either is not going to report it.
 */
data class Ending(val outcome: PactOutcome, val event: PendingEvent)

object Endings {

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
        /** The people who will hear about it: those who accepted, nobody else. */
        witnesses: List<Witness>,
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
            pactStartedAtMillis = pact.startedAtMillis,
        ),
    )

    /** They reached the end of it. */
    fun completed(
        pact: Pact,
        witnesses: List<Witness>,
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
            pactStartedAtMillis = pact.startedAtMillis,
        ),
    )

    private fun outcome(
        pact: Pact,
        result: PactResult,
        breach: Breach?,
        witnesses: List<Witness>,
        nowMillis: Long,
    ) = PactOutcome(
        result = result,
        startedAtMillis = pact.startedAtMillis,
        endedAtMillis = nowMillis,
        durationDays = pact.durationDays,
        apps = pact.apps,
        breach = breach,
        witnesses = witnesses.size,
        reported = false,
        witnessesTold = witnesses.map { WitnessTold(it.label, it.gender) },
    )
}
