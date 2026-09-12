package com.joinasr.app.data

import com.joinasr.app.ui.screens.RESET_CODE_LENGTH
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reset code, on both sides of a border neither side can see across.
 *
 * The server generates the code from `RESET_CODE_LENGTH` in
 * `backend/src/lib/password.ts`; this app draws that many boxes and refuses
 * Continue until they are full. Nothing connects the two but the number
 * being written twice, so it is asserted twice — here, and in
 * `backend/src/server/wire-contract.test.ts`, which holds the same literal.
 * Change one and the other goes red.
 *
 * The bodies are golden strings for the same reason the pact's is: kotlinx
 * leaves a property at its default out of the JSON, and Better Auth's
 * validation of these is not ours to read.
 */
class ResetCodeTest {

    @Test
    fun `the code is seven digits, which is what the server generates`() {
        // Also asserted in wire-contract.test.ts against RESET_CODE_LENGTH.
        assertEquals(7, RESET_CODE_LENGTH)
    }

    @Test
    fun `asking for a code sends the address and nothing else`() {
        assertEquals(
            """{"email":"ariyan@example.com"}""",
            ApiJson.encodeToString(RequestPasswordReset("ariyan@example.com")),
        )
    }

    @Test
    fun `spending it sends the address, the code and the new password together`() {
        // One request, because Better Auth checks the code and sets the
        // password in one endpoint. The field is `otp`, not `code`: that is
        // the name on the wire, whatever the screens call it.
        assertEquals(
            """{"email":"ariyan@example.com","otp":"4830192","password":"a new password"}""",
            ApiJson.encodeToString(
                ResetWithCode(
                    email = "ariyan@example.com",
                    otp = "4830192",
                    password = "a new password",
                ),
            ),
        )
    }
}
