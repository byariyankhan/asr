package io.joinasr.app.sync

import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One outbox, possibly two challenges' worth of events in it. Each
 * challenge must send only its own, and must not adopt a pact the server
 * has open while another challenge's ending is still queued.
 */
class OutboxTest {

    private val old = Pact(
        apps = listOf(PactApp("com.instagram.android", "Instagram", 30)),
        startedAtMillis = 1_000L,
        durationDays = 7,
    )
    private val new = old.copy(startedAtMillis = 2_000L)

    private fun event(id: String, pact: Pact?) = PendingEvent(
        id = id,
        type = "limit_hit",
        occurredAtMillis = 0L,
        pactStartedAtMillis = pact?.startedAtMillis,
    )

    @Test
    fun `each challenge drains only its own events`() {
        val queued = listOf(event("gave-up", old), event("hit", new), event("hit-2", new))

        assertEquals(listOf("gave-up"), Outbox.forPact(queued, old).map { it.id })
        assertEquals(listOf("hit", "hit-2"), Outbox.forPact(queued, new).map { it.id })
    }

    @Test
    fun `another challenge's queued event blocks adopting the server's active pact`() {
        // The pact the server still has open is the old one, and the event
        // that will close it has not gone out. Adopting it as the new
        // challenge would file the new breaches against the old pact.
        val queued = listOf(event("gave-up", old), event("hit", new))
        assertFalse(Outbox.clearOfOthers(queued, new))

        // With the old ending sent, only the new challenge's own events
        // remain, and an active pact on the server can only be its own
        // create whose answer was lost.
        assertTrue(Outbox.clearOfOthers(listOf(event("hit", new)), new))
        assertTrue(Outbox.clearOfOthers(emptyList(), new))
    }

    @Test
    fun `an event from before events were stamped belongs to nobody`() {
        val legacy = event("old-build", null)

        assertTrue(Outbox.forPact(listOf(legacy), new).isEmpty())
        // And is treated as another's: the cautious answer.
        assertFalse(Outbox.clearOfOthers(listOf(legacy), new))
    }
}
