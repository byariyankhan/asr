package io.joinasr.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxPolicyTest {

    @Test
    fun `the server's own failures are kept for another go`() {
        for (code in listOf(500, 502, 503, 504)) {
            assertTrue("$code should be kept", OutboxPolicy.keepAfter(code))
        }
    }

    @Test
    fun `not now is kept`() {
        for (code in listOf(401, 408, 425, 429)) {
            assertTrue("$code should be kept", OutboxPolicy.keepAfter(code))
        }
    }

    @Test
    fun `a refusal of the event itself is dropped`() {
        for (code in listOf(400, 403, 404, 409, 410, 422)) {
            assertFalse("$code should be dropped", OutboxPolicy.keepAfter(code))
        }
    }
}
