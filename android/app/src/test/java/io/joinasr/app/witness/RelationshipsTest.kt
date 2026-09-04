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

    // ---- who can hold a relationship ----

    private fun accepted(relationship: String) =
        Witness("1", relationship, 0, accepted = true)

    private fun invited(relationship: String) = Witness("2", relationship, 0)

    @Test
    fun `a relationship only one person can hold disappears once taken`() {
        val left = Relationships.available(listOf(accepted("mother")))
        assertFalse(left.any { it.value == "mother" })
        // The other singular ones are untouched: one mother does not mean
        // one father.
        assertTrue(left.any { it.value == "father" })
        assertTrue(left.any { it.value == "wife" })
    }

    @Test
    fun `a relationship several people can hold stays on the list`() {
        val left = Relationships.available(listOf(accepted("brother"), accepted("friend")))
        assertTrue(left.any { it.value == "brother" })
        assertTrue(left.any { it.value == "friend" })
        assertEquals(Relationships.all.size, left.size)
    }

    @Test
    fun `an unanswered invite does not take the slot`() {
        // The common case is a mother who has not opened the link yet.
        // Hiding "Mother" then would stop somebody re-sending it to her.
        val left = Relationships.available(listOf(invited("mother")))
        assertTrue(left.any { it.value == "mother" })
    }

    @Test
    fun `the singular set is exactly the four nobody has two of`() {
        for (one in listOf("mother", "father", "husband", "wife")) {
            assertTrue(one, Relationships.isSingular(one))
        }
        for (many in listOf("brother", "sister", "friend", "mentor", "colleague")) {
            assertFalse(many, Relationships.isSingular(many))
        }
    }
}
