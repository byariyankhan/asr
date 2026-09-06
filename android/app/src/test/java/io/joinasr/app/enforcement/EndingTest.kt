package io.joinasr.app.enforcement

import io.joinasr.app.witness.Witness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two halves of an ending have to agree, because one is what the person
 * reads and the other is what their witnesses read.
 */
class EndingTest {

    private val pact = Pact(
        apps = listOf(PactApp("com.instagram.android", "Instagram", 30)),
        startedAtMillis = 1_000L,
        durationDays = 14,
    )

    private val mother = Witness("w1", "mother", 0L, accepted = true, name = "Rehana Khan", gender = "female")
    private val friend = Witness("w2", "friend", 0L, accepted = true, name = "Sabbir")
    private val two = listOf(mother, friend)

    @Test
    fun `giving up is a failure, reported as giving up`() {
        val ending = Endings.gaveUp(pact, witnesses = two, eventId = "e1", nowMillis = 5_000L)

        assertEquals(PactResult.Failed, ending.outcome.result)
        assertEquals("broken", ending.event.type)
        // The reason is what picks the sentence each witness reads. Told the
        // wrong one, somebody who stopped on purpose is reported as somebody
        // who was caught.
        assertEquals("user_gave_up", ending.event.reason)
    }

    @Test
    fun `nothing was breached, so nothing is blamed`() {
        val ending = Endings.gaveUp(pact, witnesses = emptyList(), eventId = "e1", nowMillis = 5_000L)

        // No limit was exceeded and no app is at fault. The ending screen
        // reads the absent breach and says the challenge ended early rather
        // than naming an app and a number that never happened.
        assertNull(ending.outcome.breach)
        assertNull(ending.event.appPackage)
    }

    @Test
    fun `finishing carries no reason at all`() {
        val ending = Endings.completed(pact, witnesses = two + mother.copy(id = "w3"), eventId = "e1", nowMillis = 5_000L)

        assertEquals(PactResult.Completed, ending.outcome.result)
        assertEquals("completed", ending.event.type)
        assertNull(ending.event.reason)
    }

    @Test
    fun `every ending keeps what the challenge was`() {
        for (ending in listOf(
            Endings.gaveUp(pact, two, "e1", 5_000L),
            Endings.completed(pact, two, "e2", 5_000L),
        )) {
            assertEquals(1_000L, ending.outcome.startedAtMillis)
            assertEquals(5_000L, ending.outcome.endedAtMillis)
            assertEquals(14, ending.outcome.durationDays)
            assertEquals(pact.apps, ending.outcome.apps)
            assertEquals(2, ending.outcome.witnesses)
            // Nothing has been sent yet; the outbox says when it has.
            assertEquals(false, ending.outcome.reported)
        }
    }

    @Test
    fun `the ending remembers who was told, in the words the screen will use`() {
        val ending = Endings.gaveUp(pact, two, "e1", 5_000L)

        // Name and gender, so the screen can say "Rehana Khan was told. Her
        // reaction appears on Witnesses" rather than "they" about one person.
        assertEquals(
            listOf(WitnessTold("Rehana Khan", "female"), WitnessTold("Sabbir", null)),
            ending.outcome.witnessesTold,
        )
    }

    @Test
    fun `the event is stamped with the challenge it ended`() {
        // The outbox can hold two challenges' worth of events; the stamp is
        // what files this one under the right pact when it finally goes out.
        assertEquals(1_000L, Endings.gaveUp(pact, two, "e1", 5_000L).event.pactStartedAtMillis)
        assertEquals(1_000L, Endings.completed(pact, two, "e2", 5_000L).event.pactStartedAtMillis)
    }
}
