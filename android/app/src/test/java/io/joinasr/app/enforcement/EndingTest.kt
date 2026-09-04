package io.joinasr.app.enforcement

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

    @Test
    fun `giving up is a failure, reported as giving up`() {
        val ending = Endings.gaveUp(pact, witnesses = 2, eventId = "e1", nowMillis = 5_000L)

        assertEquals(PactResult.Failed, ending.outcome.result)
        assertEquals("broken", ending.event.type)
        // The reason is what picks the sentence each witness reads. Told the
        // wrong one, somebody who stopped on purpose is reported as somebody
        // who was caught.
        assertEquals("user_gave_up", ending.event.reason)
    }

    @Test
    fun `nothing was breached, so nothing is blamed`() {
        val ending = Endings.gaveUp(pact, witnesses = 0, eventId = "e1", nowMillis = 5_000L)

        // No limit was exceeded and no app is at fault. The ending screen
        // reads the absent breach and says the challenge ended early rather
        // than naming an app and a number that never happened.
        assertNull(ending.outcome.breach)
        assertNull(ending.event.appPackage)
    }

    @Test
    fun `a broken limit names the app that broke it`() {
        val breach = Breach(
            packageName = "com.instagram.android",
            label = "Instagram",
            limitMinutes = 30,
            usedMinutes = 41,
            atMillis = 4_000L,
            dayNumber = 3,
        )
        val ending = Endings.broken(pact, breach, witnesses = 1, eventId = "e1", nowMillis = 5_000L)

        assertEquals(PactResult.Failed, ending.outcome.result)
        assertEquals("limit_exceeded", ending.event.reason)
        assertEquals("com.instagram.android", ending.event.appPackage)
        assertEquals(breach, ending.outcome.breach)
    }

    @Test
    fun `finishing carries no reason at all`() {
        val ending = Endings.completed(pact, witnesses = 3, eventId = "e1", nowMillis = 5_000L)

        assertEquals(PactResult.Completed, ending.outcome.result)
        assertEquals("completed", ending.event.type)
        assertNull(ending.event.reason)
    }

    @Test
    fun `every ending keeps what the challenge was`() {
        for (ending in listOf(
            Endings.gaveUp(pact, 2, "e1", 5_000L),
            Endings.completed(pact, 2, "e2", 5_000L),
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
}
