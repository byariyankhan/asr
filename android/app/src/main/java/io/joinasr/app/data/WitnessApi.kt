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

/** What the server hands back when an invite is created. */
@Serializable
data class WitnessInvite(
    val id: String,
    @SerialName("invite_code") val inviteCode: String,
    val relationship: String,
    /** The link to send. Issued by the server, not built here. */
    val url: String,
)

/** Somebody with an account, as they appear inside another resource. */
@Serializable
data class RemoteUser(val id: String, val name: String)

/** A witness of mine, as the server holds them. */
@Serializable
data class RemoteWitness(
    val id: String,
    /** "invited" or "accepted". */
    val status: String,
    val relationship: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("invite_url") val inviteUrl: String? = null,
    /** Null until they accept: an invite has no person behind it yet. */
    val user: RemoteUser? = null,
    /** True when they are also somebody I am a witness for. */
    val mutual: Boolean = false,
    @SerialName("invited_at") val invitedAt: String? = null,
) {
    val accepted: Boolean get() = status == "accepted"
}

/** Somebody I am a witness for. The other direction. */
@Serializable
data class SupportedPerson(
    val id: String,
    val relationship: String,
    val user: RemoteUser,
    val mutual: Boolean = false,
    @SerialName("views_progress") val viewsProgress: Boolean = true,
)

/**
 * GET /v1/witnesses, whole.
 *
 * The route has always answered with two lists and this app decoded it as
 * one array, so every refresh failed to parse and was reported as "the
 * server sent something unexpected" -- which is why the witness list only
 * ever showed what this phone had added locally. Both directions are read
 * now, which is also what Figma 16 draws.
 */
@Serializable
data class WitnessLists(
    @SerialName("my_witnesses") val myWitnesses: List<RemoteWitness> = emptyList(),
    @SerialName("i_witness") val iWitness: List<SupportedPerson> = emptyList(),
)

@Serializable
private data class InviteRequest(val relationship: String)

@Serializable
private data class ReactionRequest(
    @SerialName("event_id") val eventId: String,
    val emoji: String,
)

/**
 * The witness half of the API: creating an invite and reading the list back.
 *
 * The invite link is the server's. It allocates a code, stores it against
 * this account, and returns the URL that opens it — so the app shares what
 * the server will actually answer for, rather than composing a URL that
 * happens to look right.
 */
class WitnessApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun invite(token: String, relationship: String): ApiResult<WitnessInvite> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/witnesses/invites")
                .header("Authorization", "Bearer $token")
                .post(
                    ApiJson.encodeToString(InviteRequest(relationship)).toRequestBody(JSON_MEDIA),
                )
                .build()
            call<WitnessInvite>(request)
        }

    suspend fun list(token: String): ApiResult<WitnessLists> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/witnesses")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        call<WitnessLists>(request)
    }

    /** Figma 17: what somebody I am a witness for is doing. */
    suspend fun progress(token: String, witnessId: String): ApiResult<WitnessProgress> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/witnesses/$witnessId/progress")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            call<WitnessProgress>(request)
        }

    /**
     * Reacts to one of their events. One reaction per witness per event;
     * sending again replaces it, which is what "you can change it later" on
     * Figma 25 means.
     */
    suspend fun react(
        token: String,
        witnessId: String,
        eventId: String,
        emoji: String,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/witnesses/$witnessId/reactions")
            .header("Authorization", "Bearer $token")
            .post(
                ApiJson.encodeToString(ReactionRequest(eventId, emoji)).toRequestBody(JSON_MEDIA),
            )
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiResult.Ok(Unit)
                } else {
                    parseFailure(response.code, response.body?.string(), response.header("Retry-After"))
                }
            }
        } catch (e: IOException) {
            ApiResult.Offline(OFFLINE)
        }
    }

    suspend fun remove(token: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/witnesses/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiResult.Ok(Unit)
                } else {
                    parseFailure(response.code, response.body?.string(), response.header("Retry-After"))
                }
            }
        } catch (e: IOException) {
            ApiResult.Offline(OFFLINE)
        }
    }

    private inline fun <reified T> call(request: Request): ApiResult<T> = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                parseFailure(response.code, body, response.header("Retry-After"))
            } else {
                runCatching { ApiJson.decodeFromString<T>(body.orEmpty()) }
                    .fold(
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
        const val OFFLINE = "No connection. Check your network and try again."
    }
}
