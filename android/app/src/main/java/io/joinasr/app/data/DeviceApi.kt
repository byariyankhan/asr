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

/** What POST /v1/devices answers with. Only the id is used. */
@Serializable
data class Device(val id: String)

@Serializable
data class DeviceRegistration(
    @SerialName("install_id") val installId: String,
    val model: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String,
    @SerialName("fcm_token") val fcmToken: String? = null,
)

@Serializable
private data class Heartbeat(
    @SerialName("protection_enabled") val protectionEnabled: Boolean,
    @SerialName("app_version") val appVersion: String,
)

/**
 * This install, as the server knows it.
 *
 * The device row is what a pact is created against, so registering is not
 * telemetry: without it there is nothing to attach a challenge to and no
 * witness can be told anything. It is idempotent on (user, install_id), so
 * calling it again on every start costs one request and never duplicates.
 */
class DeviceApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun register(
        token: String,
        registration: DeviceRegistration,
    ): ApiResult<Device> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/devices")
            .header("Authorization", "Bearer $token")
            .post(ApiJson.encodeToString(registration).toRequestBody(JSON_MEDIA))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return@use parseFailure(response.code, body, response.header("Retry-After"))
                }
                runCatching { ApiJson.decodeFromString<Device>(body.orEmpty()) }.fold(
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
        } catch (e: IOException) {
            ApiResult.Offline("No connection. Check your network and try again.")
        }
    }

    /**
     * Says whether protection is actually working on this phone. The server
     * uses it to tell witnesses that somebody's challenge has gone dark,
     * which only means anything if the value is measured rather than assumed.
     */
    suspend fun heartbeat(
        token: String,
        deviceId: String,
        protectionEnabled: Boolean,
        appVersion: String,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/devices/$deviceId/heartbeat")
            .header("Authorization", "Bearer $token")
            .post(
                ApiJson.encodeToString(Heartbeat(protectionEnabled, appVersion))
                    .toRequestBody(JSON_MEDIA),
            )
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiResult.Ok(Unit)
                } else {
                    parseFailure(response.code, response.body?.string(), null)
                }
            }
        } catch (e: IOException) {
            ApiResult.Offline("No connection.")
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
