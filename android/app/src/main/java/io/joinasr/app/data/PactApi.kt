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
data class SnapshotApp(
    @SerialName("package") val packageName: String,
    val label: String,
    @SerialName("daily_limit_min") val dailyLimitMinutes: Int,
)

@Serializable
data class PactSnapshot(
    val apps: List<SnapshotApp>,
    /**
     * When the day rolls over. Always local midnight, because that is where
     * [io.joinasr.app.usage.Day] puts it, and a server counting from a
     * different hour than the phone would show witnesses a different number
     * from the one the person is looking at.
     */
    @SerialName("reset_time") val resetTime: String = "00:00",
)

@Serializable
data class PactCreate(
    @SerialName("device_id") val deviceId: String,
    @SerialName("duration_days") val durationDays: Int,
    val timezone: String,
    val snapshot: PactSnapshot,
)

/** The server's copy of a challenge. Only the id is used by this app. */
@Serializable
data class RemotePact(val id: String, val status: String? = null)

@Serializable
data class EventCreate(
    /** Generated on the phone, and the idempotency key: retries are free. */
    val id: String,
    val type: String,
    val reason: String? = null,
    @SerialName("app_package") val appPackage: String? = null,
    val minutes: Int? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

/**
 * The server's half of a challenge.
 *
 * The phone enforces; this is what lets anybody else know. Nothing here is
 * on the path of a limit being applied — a person with no signal is still
 * blocked on time — but without it a witness is never told anything, which
 * is the entire difference between this app and a timer.
 */
class PactApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun create(token: String, body: PactCreate): ApiResult<RemotePact> =
        post("/v1/pacts", token, ApiJson.encodeToString(body))

    /** The active pact, or a 404 failure when there is none. */
    suspend fun current(token: String): ApiResult<RemotePact> = withContext(Dispatchers.IO) {
        send(
            Request.Builder()
                .url("$baseUrl/v1/pacts/current")
                .header("Authorization", "Bearer $token")
                .get()
                .build(),
        )
    }

    suspend fun postEvent(
        token: String,
        pactId: String,
        event: EventCreate,
    ): ApiResult<RemoteEvent> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/pacts/$pactId/events")
            .header("Authorization", "Bearer $token")
            .post(ApiJson.encodeToString(event).toRequestBody(JSON_MEDIA))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    runCatching { ApiJson.decodeFromString<RemoteEvent>(body.orEmpty()) }.fold(
                        onSuccess = { ApiResult.Ok(it) },
                        // The event landed; only the answer was unreadable.
                        // Reporting failure here would make the outbox send
                        // it again forever.
                        onFailure = { ApiResult.Ok(RemoteEvent(event.id)) },
                    )
                } else {
                    parseFailure(response.code, body, response.header("Retry-After"))
                }
            }
        } catch (e: IOException) {
            ApiResult.Offline("No connection.")
        }
    }

    private suspend fun post(
        path: String,
        token: String,
        json: String,
    ): ApiResult<RemotePact> = withContext(Dispatchers.IO) {
        send(
            Request.Builder()
                .url("$baseUrl$path")
                .header("Authorization", "Bearer $token")
                .post(json.toRequestBody(JSON_MEDIA))
                .build(),
        )
    }

    private fun send(request: Request): ApiResult<RemotePact> = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                parseFailure(response.code, body, response.header("Retry-After"))
            } else {
                runCatching { ApiJson.decodeFromString<RemotePact>(body.orEmpty()) }.fold(
                    onSuccess = { ApiResult.Ok(it) },
                    onFailure = {
                        ApiResult.Failure(
                            code = response.code,
                            error = "invalid_response",
                            message = "The server sent something this app could not read.",
                        )
                    },
                )
            }
        }
    } catch (e: IOException) {
        ApiResult.Offline("No connection.")
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
data class RemoteEvent(val id: String)
