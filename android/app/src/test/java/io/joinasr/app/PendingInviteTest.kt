package io.joinasr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The referrer parser. It runs once per install, on the launch that decides
 * whether somebody who has just installed the app sees the invitation they
 * came for or a welcome screen — and it is not a thing anybody will notice
 * is broken until a witness is lost to it.
 */
class PendingInviteTest {

    @Test
    fun `takes the code the invitation page sent`() {
        assertEquals("HQ5RLL8FQ5", PendingInvite.codeIn("w=HQ5RLL8FQ5"))
    }

    @Test
    fun `finds it among the parameters Play adds`() {
        assertEquals(
            "HQ5RLL8FQ5",
            PendingInvite.codeIn("utm_source=google-play&w=HQ5RLL8FQ5&utm_medium=organic"),
        )
    }

    @Test
    fun `ignores an install that came from anywhere else`() {
        // What an organic Play install actually carries.
        assertNull(PendingInvite.codeIn("utm_source=google-play&utm_medium=organic"))
        assertNull(PendingInvite.codeIn(""))
        assertNull(PendingInvite.codeIn("w="))
    }

    @Test
    fun `does not mistake another parameter for the code`() {
        // The key is `w`, not "ends in w" and not "contains w".
        assertNull(PendingInvite.codeIn("gclid=abc&ww=HQ5RLL8FQ5"))
        assertNull(PendingInvite.codeIn("show=HQ5RLL8FQ5"))
    }

    @Test
    fun `refuses a value that is not shaped like a code`() {
        // A referrer is attacker-controllable: anybody can send a Play link
        // with any referrer on it. The server decides whether a code is
        // real; this only keeps a stray value from opening the screen.
        assertNull(PendingInvite.codeIn("w=../../etc/passwd"))
        assertNull(PendingInvite.codeIn("w=abc def"))
        assertNull(PendingInvite.codeIn("w=" + "A".repeat(400)))
    }
}
