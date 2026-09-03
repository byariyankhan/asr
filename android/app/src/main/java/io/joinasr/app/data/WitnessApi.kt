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

/** A witness as the server holds them. */
@Serializable
data class RemoteWitness(
    val id: String,
    /** "invited" or "accepted". */
    val status: String,
    val relationship: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("witness_name") val witnessName: String? = null,
    @SerialName("invited_at") val invitedAt: String? = null,
) {
    val accepted: Boolean get() = status == "accepted"
}

@Serializable
private data class InviteRequest(val relationship: String)

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

    suspend fun list(token: String): ApiResult<List<RemoteWitness>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/witnesses")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        call<List<RemoteWitness>>(request)
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
