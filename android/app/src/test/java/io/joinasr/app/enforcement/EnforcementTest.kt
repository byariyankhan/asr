package io.joinasr.app.enforcement

import io.joinasr.app.apps.AppEntry
import io.joinasr.app.limits.DailyLimit
import io.joinasr.app.usage.UsageSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val messages = "com.google.android.apps.messaging"

    private val pact = Pact(
        apps = listOf(
            PactApp(instagram, "Instagram", limitMinutes = 20),
            PactApp(youtube, "YouTube", limitMinutes = 45),
        ),
        startedAtMillis = 1_772_150_400_000L,
    )

    private fun snapshot(foreground: String?, vararg used: Pair<String, Int>) = UsageSnapshot(
        minutesByPackage = used.toMap(),
        foregroundPackage = foreground,
        dayStartMillis = 1_772_150_400_000L,
    )

    @Test
    fun `no pact means nothing to enforce`() {
        val seen = snapshot(instagram, instagram to 999)
        assertEquals(Decision.Allow, Enforcement.decide(null, seen))
    }

    @Test
    fun `an empty pact is nothing to enforce, not an error`() {
        val empty = Pact(apps = emptyList(), startedAtMillis = 0)
        assertFalse(empty.isEnforceable)
        val seen = snapshot(instagram, instagram to 999)
        assertEquals(Decision.Allow, Enforcement.decide(empty, seen))
    }

    @Test
    fun `an app nobody put under a limit is never blocked`() {
        // Not a detail: the picker refuses to offer Messages, and if a pact
        // somehow named it the loop must still leave it alone.
        val seen = snapshot(messages, messages to 500)
        assertEquals(Decision.Allow, Enforcement.decide(pact, seen))
    }

    @Test
    fun `an app over its limit but not open is left alone`() {
        // There is nothing to block. Covering the screen while somebody is
        // reading their messages would punish them for a limit they are not
        // currently exceeding.
        val seen = snapshot(messages, instagram to 300)
        assertEquals(Decision.Allow, Enforcement.decide(pact, seen))
    }

    @Test
    fun `nothing in the foreground is nothing to do`() {
        assertEquals(Decision.Allow, Enforcement.decide(pact, snapshot(null, instagram to 300)))
    }

    @Test
    fun `under the limit is allowed`() {
        assertEquals(Decision.Allow, Enforcement.decide(pact, snapshot(instagram, instagram to 19)))
    }

    @Test
    fun `the limit is spent when it is reached, not a minute later`() {
        // Twenty minutes of a twenty minute limit is all of it. Waiting for
        // twenty-one would give everybody a free minute and make the number
        // on the block screen a lie.
        val decision = Enforcement.decide(pact, snapshot(instagram, instagram to 20))
        val expected = Decision.Block(PactApp(instagram, "Instagram", 20), usedMinutes = 20)
        assertEquals(expected, decision)
    }

    @Test
    fun `past the limit blocks and reports what was actually used`() {
        // Being over can happen: the phone was asleep, the service was
        // killed, the person granted access late. The screen should say the
        // true number rather than the limit.
        val decision = Enforcement.decide(pact, snapshot(youtube, youtube to 61)) as Decision.Block
        assertEquals("YouTube", decision.app.label)
        assertEquals(45, decision.app.limitMinutes)
        assertEquals(61, decision.usedMinutes)
    }

    @Test
    fun `an app with no recorded time is at zero, not blocked`() {
        assertEquals(Decision.Allow, Enforcement.decide(pact, snapshot(instagram)))
    }

    @Test
    fun `the loop idles unless a watched app is in front`() {
        assertEquals(Enforcement.IDLE_MILLIS, Enforcement.pollDelayMillis(pact, snapshot(null)))
        assertEquals(
            Enforcement.IDLE_MILLIS,
            Enforcement.pollDelayMillis(pact, snapshot(messages, messages to 10)),
        )
        assertEquals(
            Enforcement.IDLE_MILLIS,
            Enforcement.pollDelayMillis(null, snapshot(instagram)),
        )
    }

    @Test
    fun `it watches loosely with time to spare and closely near the limit`() {
        assertEquals(
            Enforcement.WATCHING_MILLIS,
            Enforcement.pollDelayMillis(pact, snapshot(instagram, instagram to 5)),
        )
        // Two minutes left: close enough that a five second gap could let
        // somebody past their limit before the screen appears.
        assertEquals(
            Enforcement.CLOSE_MILLIS,
            Enforcement.pollDelayMillis(pact, snapshot(instagram, instagram to 18)),
        )
        assertEquals(
            Enforcement.CLOSE_MILLIS,
            Enforcement.pollDelayMillis(pact, snapshot(instagram, instagram to 40)),
        )
    }

    @Test
    fun `close watching is fast enough that the limit cannot be overshot much`() {
        // The design accepts that the block screen arrives a moment after
        // the app does. This pins how big a moment: at most one second of
        // slack once the loop is watching closely.
        assertTrue(Enforcement.CLOSE_MILLIS <= 1_000L)
        assertTrue(Enforcement.CLOSE_MILLIS < Enforcement.WATCHING_MILLIS)
        assertTrue(Enforcement.WATCHING_MILLIS < Enforcement.IDLE_MILLIS)
    }

    @Test
    fun `a pact survives being written down and read back`() {
        // The pact is stored as JSON. If this ever fails, every live
        // challenge on every phone stops being enforced at once.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val written = json.encodeToString(Pact.serializer(), pact)
        val restored = json.decodeFromString<Pact>(written)
        assertEquals(pact, restored)
        assertEquals(Pact.CURRENT_VERSION, restored.version)
    }

    @Test
    fun `a pact from a newer build still loads`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val fromTheFuture = """
            {"apps":[{"packageName":"$instagram","label":"Instagram","limitMinutes":20,
            "somethingNew":true}],"startedAtMillis":1,"version":9,"alsoNew":"x"}
        """.trimIndent()
        val restored = json.decodeFromString<Pact>(fromTheFuture)
        assertEquals(1, restored.apps.size)
        assertEquals(9, restored.version)
    }

    @Test
    fun `a limit no screen in this app can produce is not enforceable`() {
        // Nothing legitimate writes these. If one appears, the pact is
        // corrupt and enforcing it would block somebody on a number they
        // never chose.
        assertFalse(Pact(listOf(PactApp(instagram, "Instagram", 0)), 0).isEnforceable)
        assertFalse(Pact(listOf(PactApp(instagram, "Instagram", 100_000)), 0).isEnforceable)
        assertFalse(Pact(listOf(PactApp("", "Instagram", 20)), 0).isEnforceable)
        assertTrue(Pact(listOf(PactApp(instagram, "Instagram", 20)), 0).isEnforceable)
    }

    @Test
    fun `building one from the setup flow keeps every app and its limit`() {
        val built = Pact.from(
            apps = listOf(AppEntry(instagram, "Instagram"), AppEntry(youtube, "YouTube")),
            limits = mapOf(instagram to 15, youtube to 45),
            startedAtMillis = 7,
        )
        assertEquals(mapOf(instagram to 15, youtube to 45), built.limitsByPackage)
        assertEquals("YouTube", built.appFor(youtube)?.label)
        assertNull(built.appFor(messages))
        assertEquals(7L, built.startedAtMillis)
    }

    @Test
    fun `an app that somehow arrives without a limit gets the default, not zero`() {
        // Zero would block it the instant it opened.
        val built = Pact.from(listOf(AppEntry(instagram, "Instagram")), emptyMap(), 0)
        assertEquals(DailyLimit.DEFAULT_MINUTES, built.apps.single().limitMinutes)
        assertTrue(built.isEnforceable)
    }
}
