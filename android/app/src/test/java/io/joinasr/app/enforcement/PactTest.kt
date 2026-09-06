package io.joinasr.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PactTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val instagram = PactApp("com.instagram.android", "Instagram", 30)
    private val tiktok = PactApp("com.zhiliaoapp.musically", "TikTok", 20, addedOn = "2026-09-06")

    @Test
    fun `an app added later is judged from the start of that local day`() {
        val pact = Pact(apps = listOf(instagram, tiktok), startedAtMillis = 0L)
        val expected = LocalDate.of(2026, 9, 6).atStartOfDay(dhaka).toInstant().toEpochMilli()
        assertEquals(mapOf(tiktok.packageName to expected), pact.judgedFrom(dhaka))
    }

    @Test
    fun `apps the challenge started with are absent, so they are judged on every day`() {
        val pact = Pact(apps = listOf(instagram), startedAtMillis = 0L)
        assertEquals(emptyMap<String, Long>(), pact.judgedFrom(dhaka))
    }

    @Test
    fun `a day that cannot be read is treated as from the start`() {
        // Judging more rather than less: the safe direction for a value the
        // server wrote and this build could not parse.
        val odd = PactApp("com.twitter.android", "X", 25, addedOn = "yesterday")
        val pact = Pact(apps = listOf(odd), startedAtMillis = 0L)
        assertEquals(emptyMap<String, Long>(), pact.judgedFrom(dhaka))
    }

    @Test
    fun `an added app is still enforceable and still in the limits`() {
        val pact = Pact(apps = listOf(instagram, tiktok), startedAtMillis = 0L)
        assertEquals(true, pact.isEnforceable)
        assertEquals(20, pact.limitsByPackage[tiktok.packageName])
    }
}
