package io.joinasr.app.data

import kotlinx.serialization.decodeFromString
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
    fun `an app added to a running challenge is three fields and no added_on`() {
        // Repeated character for character in wire-contract.test.ts. The
        // day it came in is the server's to stamp; nothing here sends one.
        val body = PactAppAdd(
            packageName = "com.zhiliaoapp.musically",
            label = "TikTok",
            dailyLimitMinutes = 20,
        )
        assertEquals(
            """{"package":"com.zhiliaoapp.musically","label":"TikTok","daily_limit_min":20}""",
            ApiJson.encodeToString(body),
        )
    }

    @Test
    fun `an app the server stamped reads back with its day, and one it did not reads back without`() {
        val apps = ApiJson.decodeFromString<List<SnapshotApp>>(
            """[{"package":"com.instagram.android","label":"Instagram","daily_limit_min":30},""" +
                """{"package":"com.zhiliaoapp.musically","label":"TikTok","daily_limit_min":20,"added_on":"2026-09-06"}]""",
        )
        assertEquals(listOf(null, "2026-09-06"), apps.map { it.addedOn })
        // And a snapshot the phone sends never carries the field at all.
        assertEquals(
            """{"package":"com.instagram.android","label":"Instagram","daily_limit_min":30}""",
            ApiJson.encodeToString(apps.first()),
        )
    }

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

    /**
     * The other direction: what the app has to be able to read back.
     *
     * A challenge runs on one phone, and the only way a second phone knows
     * whether it is looking at its own challenge or at one in somebody's
     * other pocket is `device_id` -- with `device_model` for saying so on
     * screen. Both were added to the response after the app already shipped,
     * so a build that ignored them would silently show every phone a
     * challenge it could take over by accident.
     */
    @Test
    fun `the current pact says which phone is running it`() {
        val json = """{"id":"p1","user_id":"u1","device_id":"d9","device_model":"Galaxy A54",""" +
            """"duration_days":30,"timezone":"Asia/Dhaka","starts_at":"2026-09-01T10:00:00.000Z",""" +
            """"status":"active","snapshot":{"apps":[{"package":"com.instagram.android",""" +
            """"label":"Instagram","daily_limit_min":30}],"reset_time":"00:00","activities":{}}}"""

        val pact = ApiJson.decodeFromString<RemotePact>(json)

        assertEquals("d9", pact.deviceId)
        assertEquals("Galaxy A54", pact.deviceModel)
        assertEquals(30, pact.durationDays)
        assertEquals(1, pact.snapshot?.apps?.size)
    }

    /** A pact from a server that does not send them is readable, not fatal. */
    @Test
    fun `a response without the device fields still parses`() {
        val pact = ApiJson.decodeFromString<RemotePact>("""{"id":"p1"}""")
        assertEquals(null, pact.deviceId)
        assertEquals(null, pact.deviceModel)
    }

    private companion object {
        // Kept on one line, and repeated verbatim in the backend test.
        const val PACT_CREATE_WIRE = """{"device_id":"8f14e45f-ea9e-4c3b-9d1a-2b6c7d8e9f01","duration_days":14,"timezone":"Asia/Dhaka","snapshot":{"apps":[{"package":"com.instagram.android","label":"Instagram","daily_limit_min":30}],"reset_time":"00:00","activities":{"walk_steps":{"reward_min":15,"daily_cap_min":60,"target":6000},"focus_session":{"reward_min":15,"daily_cap_min":60,"target_min":25}}}}"""
    }
}
