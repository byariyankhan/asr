package io.joinasr.app.witness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PronounsTest {

    @Test
    fun `a man is he and a woman is she`() {
        assertEquals("his", Pronouns.of("male").their)
        assertEquals("is", Pronouns.of("male").are)
        assertEquals("her", Pronouns.of("female").their)
        assertEquals("hers", Pronouns.of("female").theirs)
    }

    @Test
    fun `anybody who did not say is they, and the verb follows`() {
        for (gender in listOf(null, "other", "prefer_not_to_say", "")) {
            assertSame(Pronouns.THEY, Pronouns.of(gender))
        }
        assertEquals("are", Pronouns.THEY.are)
    }
}
