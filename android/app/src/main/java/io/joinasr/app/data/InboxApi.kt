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

/**
 * One line in the inbox.
 *
 * The title and body are written by the server, not assembled here. They are
 * the same words that went out as a push notification and as an email, and
 * three places composing that sentence independently is three places for it
 * to drift.
 */
@Serializable
data class InboxItem(
    val id: String,
    @SerialName("about_user_id") val aboutUserId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    /** pact_started, pact_broken, pact_completed, witness_accepted, reaction, … */
    val kind: String,
    val title: String,
    val body: String? = null,
    @SerialName("deep_link") val deepLink: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val unread: Boolean get() = readAt == null
}

@Serializable
data class Inbox(
    val items: List<InboxItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
private data class MarkRead(val ids: List<String>? = null, val all: Boolean? = null)

/** Figma 19. */
class InboxApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun list(token: String, cursor: String? = null, limit: Int = 40): ApiResult<Inbox> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/v1/me/notifications?limit=$limit")
                if (!cursor.isNullOrBlank()) append("&cursor=$cursor")
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        return@use parseFailure(response.code, body, response.header("Retry-After"))
                    }
                    runCatching { ApiJson.decodeFromString<Inbox>(body.orEmpty()) }.fold(
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
            } catch (e: IOException) {
                ApiResult.Offline(OFFLINE)
            }
        }

    suspend fun markRead(token: String, ids: List<String>? = null): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val payload = if (ids == null) MarkRead(all = true) else MarkRead(ids = ids)
            val request = Request.Builder()
                .url("$baseUrl/v1/me/notifications/read")
                .header("Authorization", "Bearer $token")
                .post(ApiJson.encodeToString(payload).toRequestBody(JSON_MEDIA))
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
                ApiResult.Offline(OFFLINE)
            }
        }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val OFFLINE = "No connection. Check your network and try again."
    }
}
