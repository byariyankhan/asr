package io.joinasr.app.witness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invite is the only thing this product says on somebody else's phone,
 * to a person who has never heard of it, and it has to arrive as a message
 * from their child or their friend rather than from an app. It is worth a
 * test: the copy is nine strings a refactor can silently break, and nobody
 * would notice until a mother received a message addressed to "Someone".
 */
class RelationshipsTest {

    private val url = "https://joinasr.io/w/K7M2P9XQ4T"

    @Test
    fun `every offered relationship has its own greeting`() {
        val greetings = Relationships.all.map {
            Relationships.inviteText(it.value, 14, url).substringBefore("\n")
        }
        assertEquals(
            listOf(
                "Hey Mom,",
                "Hey Dad,",
                "Hey bro,",
                "Hey sis,",
                "Hey love,",
                "Hey love,",
                "Hey,",
                "Hi,",
                "Hi,",
            ),
            greetings,
        )
    }

    @Test
    fun `the duration is the one the person actually chose`() {
        for (days in listOf(7, 14, 21, 30)) {
            val text = Relationships.inviteText("mother", days, url)
            assertTrue(text, text.contains("$days-day challenge"))
        }
    }

    @Test
    fun `every message ends with the link the server issued`() {
        for (relationship in Relationships.all) {
            val text = Relationships.inviteText(relationship.value, 7, url)
            assertTrue(relationship.value, text.trimEnd().endsWith(url))
        }
    }

    @Test
    fun `every message carries the same closing line`() {
        val closing = "I’ve made a commitment to myself, and Asr will keep you updated"
        for (relationship in Relationships.all) {
            assertTrue(
                relationship.value,
                Relationships.inviteText(relationship.value, 7, url).contains(closing),
            )
        }
    }

    @Test
    fun `no message names anybody or talks about the sender in the third person`() {
        // The app never asks who the witness is, and the message arrives
        // from the sender's own number in their own thread -- so it is first
        // person throughout and contains no name at all.
        for (relationship in Relationships.all) {
            val text = Relationships.inviteText(relationship.value, 7, url)
            assertFalse(relationship.value, text.contains("Someone"))
            assertFalse(relationship.value, text.contains("their"))
        }
    }

    @Test
    fun `a relationship this build no longer offers still writes something`() {
        // Re-sharing an invite created before the list changed.
        val text = Relationships.inviteText("spouse", 21, url)
        assertTrue(text.startsWith("Hi,"))
        assertTrue(text.contains("21-day"))
        assertTrue(text.trimEnd().endsWith(url))
    }

    @Test
    fun `retired values still have a label, so old rows read properly`() {
        assertEquals("Brother or sister", Relationships.labelFor("sibling"))
        assertEquals("Someone else", Relationships.labelFor("other"))
        assertEquals("Mother", Relationships.labelFor("mother"))
        assertEquals("Witness", Relationships.labelFor("nonsense"))
    }
}
