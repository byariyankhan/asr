package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class ActivityCreate(
    /** Made on the phone, and the idempotency key. */
    val id: String,
    val type: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("deadline_at") val deadlineAt: String,
    /**
     * The app whose limit sent them here.
     *
     * The server needs it to name the app in what a witness is told --
     * "reached the TikTok limit" rather than "reached a limit" -- and it
     * turns the package into a label using the pact's own snapshot, so the
     * name cannot drift or vanish because the app was uninstalled.
     */
    @SerialName("app_package") val appPackage: String? = null,
)

/**
 * The server's copy of an attempt.
 *
 * [target] and [rewardMinutes] come back from the rules locked into the pact
 * rather than from anything this app sent, which is the point: a phone
 * cannot ask for a cheaper walk.
 */
@Serializable
data class RemoteActivity(
    val id: String,
    val type: String,
    val status: String? = null,
    val target: Int? = null,
    @SerialName("reward_min") val rewardMinutes: Int? = null,
)

@Serializable
private data class CompleteActivity(
    @SerialName("event_id") val eventId: String,
    @SerialName("occurred_at") val occurredAt: String,
)

/**
 * Earning time, on the server's side.
 *
 * None of this is on the path of the reward being applied. The minutes are
 * added on the phone the instant an activity completes, because the person
 * standing in the street having walked two kilometres should not have to
 * find signal before their reward exists. This reports it — so the daily cap
 * is checked against every device, and so a witness sees the walk.
 */
class ActivityApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun start(
        token: String,
        pactId: String,
        body: ActivityCreate,
    ): ApiResult<RemoteActivity> = withContext(Dispatchers.IO) {
        call(
            Request.Builder()
                .url("$baseUrl/v1/pacts/$pactId/activities")
                .header("Authorization", "Bearer $token")
                .post(ApiJson.encodeToString(body).toRequestBody(JSON_MEDIA))
                .build(),
        )
    }

    suspend fun complete(
        token: String,
        activityId: String,
        eventId: String,
        occurredAt: String,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        post(
            "$baseUrl/v1/activities/$activityId/complete",
            token,
            ApiJson.encodeToString(CompleteActivity(eventId, occurredAt)),
        )
    }

    suspend fun cancel(token: String, activityId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            post("$baseUrl/v1/activities/$activityId/cancel", token, "{}")
        }

    private fun post(url: String, token: String, json: String): ApiResult<Unit> = try {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(json.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                ApiResult.Ok(Unit)
            } else {
                parseFailure(response.code, response.body?.string(), null)
            }
        }
    } catch (e: IOException) {
        ApiResult.Offline(OFFLINE)
    }

    private fun call(request: Request): ApiResult<RemoteActivity> = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                parseFailure(response.code, body, response.header("Retry-After"))
            } else {
                runCatching { ApiJson.decodeFromString<RemoteActivity>(body.orEmpty()) }.fold(
                    onSuccess = { ApiResult.Ok(it) },
                    onFailure = {
                        ApiResult.Failure(
                            code = response.code,
                            error = "unreadable_body",
                            message = "The server sent something unexpected.",
                        )
                    },
                )
            }
        }
    } catch (e: IOException) {
        ApiResult.Offline(OFFLINE)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val OFFLINE = "No connection."
    }
}
