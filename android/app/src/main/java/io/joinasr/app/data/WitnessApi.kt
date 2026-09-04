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

/**
 * Somebody with an account, as they appear inside another resource.
 *
 * The photo travels with the name everywhere the name goes. A witness list
 * is a list of people, and a column of coloured initials is a list of
 * strangers -- which is the opposite of what a circle of people who agreed
 * to hold somebody to something should look like.
 */
@Serializable
data class RemoteUser(
    val id: String,
    val name: String,
    /** A path like /v1/media/avatars/..., or null. Never an absolute URL. */
    val image: String? = null,
)

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

/**
 * What GET /v1/witnesses/invites/{code} answers with, and deliberately all
 * of it. The route takes no session -- somebody deciding whether to vouch
 * for a person has no account yet -- so it never returns their app list,
 * their limits or how long the challenge runs. Figma 18 draws those; they
 * cannot be shown before accepting, and inventing them would be worse.
 */
@Serializable
data class InvitePeek(
    @SerialName("inviter_name") val inviterName: String,
    @SerialName("inviter_image") val inviterImage: String? = null,
    val relationship: String,
    /** How long the challenge runs, when one is running. */
    val days: Int? = null,
    /**
     * True when the person reading this is the one who sent it.
     *
     * Nobody witnesses themselves and the server refuses it, but refusing
     * after the button is pressed is a worse way to learn that than never
     * being offered the button. It happens by accident: testing your own
     * link, or tapping it in the thread you just shared it to.
     */
    val own: Boolean = false,
    /**
     * True when the reader already accepted this challenge.
     *
     * One link serves everybody it is sent to, so it stays open after
     * somebody takes it — and somebody who accepted an hour ago and taps it
     * again in the same chat was getting the whole "will you be a witness"
     * page a second time, with an error under the button. They are already
     * a witness; the circle is where the link was taking them.
     */
    val already: Boolean = false,
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

    /**
     * Figma 18, before accepting. The token is optional and sent when there
     * is one.
     *
     * The route needs no session -- somebody deciding whether to vouch for a
     * person has no account yet, which is the whole reason it is public --
     * but with one it can also say whether the reader is the person who sent
     * this invitation, so the app stops offering them a button that would be
     * refused.
     */
    suspend fun peekInvite(code: String, token: String? = null): ApiResult<InvitePeek> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url("$baseUrl/v1/witnesses/invites/$code").get()
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            call<InvitePeek>(builder.build())
        }

    suspend fun acceptInvite(token: String, code: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            answerInvite(token, code, "accept")
        }

    suspend fun declineInvite(token: String, code: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            answerInvite(token, code, "decline")
        }

    private fun answerInvite(token: String, code: String, action: String): ApiResult<Unit> {
        val request = Request.Builder()
            .url("$baseUrl/v1/witnesses/invites/$code/$action")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON_MEDIA))
            .build()
        return try {
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
