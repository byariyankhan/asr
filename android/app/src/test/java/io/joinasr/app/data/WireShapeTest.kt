package io.joinasr.app.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What this app actually puts on the wire.
 *
 * These are golden strings on purpose, and the pact one is repeated
 * character for character in `backend/src/server/wire-contract.test.ts`,
 * where it is fed to the zod schema the server validates with. Neither test
 * can see the other language, so the literal is the contract: change the
 * shape on one side and the other side fails.
 *
 * The bug that earned this file: kotlinx omits any property left at its
 * default unless `encodeDefaults` is on, and it was off. `reset_time`
 * defaults to "00:00" and the server requires it, so every POST /v1/pacts
 * was rejected as invalid — silently, because the request is best-effort.
 * The server never had a copy of anybody's challenge, which surfaced only
 * once witnesses had to be invited to one.
 */
class WireShapeTest {

    @Test
    fun `a pact carries its reset time and its activity rules`() {
        val body = PactCreate(
            deviceId = "8f14e45f-ea9e-4c3b-9d1a-2b6c7d8e9f01",
            durationDays = 14,
            timezone = "Asia/Dhaka",
            snapshot = PactSnapshot(
                apps = listOf(
                    SnapshotApp(
                        packageName = "com.instagram.android",
                        label = "Instagram",
                        dailyLimitMinutes = 30,
                    ),
                ),
                activities = ActivityRules(
                    walkSteps = ActivityRule(rewardMinutes = 15, dailyCapMinutes = 60, target = 6000),
                    focusSession = ActivityRule(
                        rewardMinutes = 15,
                        dailyCapMinutes = 60,
                        targetMinutes = 25,
                    ),
                ),
            ),
        )

        assertEquals(PACT_CREATE_WIRE, ApiJson.encodeToString(body))
    }

    /**
     * The other half of `encodeDefaults`: an unset optional is absent, not
     * null. The server's optionals are zod `.optional()`, which refuses a
     * null, and PATCH /v1/me reads absent as "leave this alone".
     */
    @Test
    fun `an unset optional is left out rather than sent as null`() {
        val update = ProfileUpdate(country = "BD")
        assertEquals("""{"country":"BD"}""", ApiJson.encodeToString(update))
    }

    @Test
    fun `an activity rule sends only the target it has`() {
        val walk = ActivityRule(rewardMinutes = 15, dailyCapMinutes = 60, target = 6000)
        assertEquals(
            """{"reward_min":15,"daily_cap_min":60,"target":6000}""",
            ApiJson.encodeToString(walk),
        )
    }

    private companion object {
        // Kept on one line, and repeated verbatim in the backend test.
        const val PACT_CREATE_WIRE = """{"device_id":"8f14e45f-ea9e-4c3b-9d1a-2b6c7d8e9f01","duration_days":14,"timezone":"Asia/Dhaka","snapshot":{"apps":[{"package":"com.instagram.android","label":"Instagram","daily_limit_min":30}],"reset_time":"00:00","activities":{"walk_steps":{"reward_min":15,"daily_cap_min":60,"target":6000},"focus_session":{"reward_min":15,"daily_cap_min":60,"target_min":25}}}}"""
    }
}
