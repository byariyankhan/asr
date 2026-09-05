package io.joinasr.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * What analytics is allowed to say. The catalogue is the ten events the
 * product is measured by, and every parameter on every one of them is a
 * short code or a small number: never an app, a minute, a name or anything
 * a person typed.
 */
class AnalyticsTest {

    private val expected = listOf(
        "sign_up",
        "login",
        "onboarding_complete",
        "pact_created",
        "pact_started",
        "witness_invite_sent",
        "witness_invite_accepted",
        "extra_time_earned",
        "challenge_completed",
        "challenge_broken",
    )

    @Test
    fun `exactly the ten product events, by name`() {
        assertEquals(expected, Analytics.catalogue().map { it.name })
    }

    @Test
    fun `no event carries anything outside the allowed parameters`() {
        for (event in Analytics.catalogue()) {
            val stray = event.params.keys - Analytics.ALLOWED_PARAMS
            assertTrue("${event.name} carries $stray", stray.isEmpty())
        }
    }

    @Test
    fun `values are short codes or small numbers, never text somebody typed`() {
        val code = Regex("^[a-z][a-z0-9_]{0,31}$")
        for (event in Analytics.catalogue()) {
            for ((key, value) in event.params) {
                when (value) {
                    is Int -> assertTrue("${event.name}.$key = $value", value in 0..366)
                    is String -> assertTrue("${event.name}.$key = $value", code.matches(value))
                    else -> fail("${event.name}.$key is a ${value::class.simpleName}")
                }
            }
        }
    }

    @Test
    fun `names are what Firebase accepts`() {
        val name = Regex("^[a-z][a-z0-9_]{0,39}$")
        for (event in Analytics.catalogue()) {
            assertTrue(event.name, name.matches(event.name))
            for (key in event.params.keys) assertTrue("${event.name}.$key", name.matches(key))
        }
    }
}
