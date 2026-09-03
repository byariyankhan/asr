package io.joinasr.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorEnvelopeTest {

    @Test
    fun `the envelope's message wins over any default`() {
        val f = parseFailure(409, """{"error":"pact_active","message":"You already have one."}""", null)
        assertEquals("pact_active", f.error)
        assertEquals("You already have one.", f.message)
    }

    @Test
    fun `a validation failure falls back to the first issue`() {
        val body = """{"error":"invalid_body","issues":[{"message":"Password too short"}]}"""
        assertEquals("Password too short", parseFailure(400, body, null).message)
    }

    @Test
    fun `an empty body still yields something worth reading`() {
        for (code in listOf(401, 403, 404, 409, 429, 500, 503)) {
            val message = parseFailure(code, "", null).message
            assertTrue("blank message for $code", message.isNotBlank())
        }
    }

    @Test
    fun `a 401 says what a person can do about it`() {
        // Deliberately not "unauthorized": the two things that produce a 401
        // here are a wrong password and an expired session, and the sentence
        // has to make sense for the first.
        assertTrue(parseFailure(401, null, null).message.contains("password"))
    }

    @Test
    fun `Retry-After is read only when it is a number`() {
        assertEquals(30, parseFailure(429, null, "30").retryAfterSeconds)
        // A date-form Retry-After is legal HTTP and unusable as seconds.
        assertEquals(null, parseFailure(429, null, "Wed, 21 Oct 2026 07:28:00 GMT").retryAfterSeconds)
    }
}
