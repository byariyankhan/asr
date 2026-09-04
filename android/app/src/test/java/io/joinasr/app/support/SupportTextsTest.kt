package io.joinasr.app.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The support page is the one screen somebody reaches when they are already
 * annoyed, so the address has to be right and the answers have to be there.
 */
class SupportTextsTest {

    @Test
    fun `the address is the one support reads`() {
        assertEquals("hi@ariyankhan.com", SupportTexts.EMAIL)
    }

    @Test
    fun `every question has an answer worth reading`() {
        assertTrue(SupportTexts.questions.isNotEmpty())
        for (entry in SupportTexts.questions) {
            assertTrue(entry.question, entry.question.endsWith("?"))
            // Long enough to be an answer rather than a shrug. The shortest
            // real one here is about two lines.
            assertTrue(entry.question, entry.answer.length > 80)
        }
    }

    /**
     * The three that arrive by email otherwise, from people who were about
     * to uninstall: the permanent notification, the permissions, and limits
     * that cannot be edited once a challenge starts.
     */
    @Test
    fun `it answers the three that look like faults`() {
        val asked = SupportTexts.questions.joinToString(" ") { it.question }.lowercase()
        assertTrue(asked, "notification" in asked)
        assertTrue(asked, "usage access" in asked)
        assertTrue(asked, "change my limits" in asked)
    }

    @Test
    fun `no question is asked twice`() {
        val questions = SupportTexts.questions.map { it.question }
        assertEquals(questions.size, questions.toSet().size)
    }
}
