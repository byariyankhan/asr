package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** The subset of GET /v1/me the app reads today. */
@Serializable
data class Me(
    val id: String,
    val name: String,
    val email: String,
    /** A path like /v1/media/avatars/... , or null. Never an absolute URL:
     *  the server stores a key and the client owns the base URL. */
    val image: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val country: String? = null,
    val gender: String? = null,
) {
    /**
     * Whether the About You screen still has to be shown. Decided from what
     * the server has, not from a local "did we show it" flag, so reinstalling
     * or signing in on a second phone does not ask again.
     *
     * The photo is deliberately not part of this: it is optional, and a
     * person who skipped it should not be sent back to the same screen on
     * every launch.
     */
    val profileComplete: Boolean
        get() = !dateOfBirth.isNullOrBlank() && !country.isNullOrBlank() && !gender.isNullOrBlank()
}

/** The subset of PATCH /v1/me this app sends. Absent fields are left alone
 *  by the server, so every property is optional here too. */
@Serializable
data class ProfileUpdate(
    val name: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val country: String? = null,
    val gender: String? = null,
)

/**
 * GET /v1/me, which is the first call that needs the bearer token — and
 * therefore the only honest proof that signing in actually worked. A token
 * that was accepted at sign-in but rejected here means the app is signed out
 * in every way that matters, and the caller is told so rather than shown a
 * screen with blanks in it.
 */
class MeApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun get(token: String): ApiResult<Me> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/me")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    parseFailure(response.code, body, response.header("Retry-After"))
                } else {
                    runCatching { ApiJson.decodeFromString<Me>(body.orEmpty()) }
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
            ApiResult.Offline("No connection. Check your network and try again.")
        }
    }

    /** PATCH /v1/me. Returns the profile as the server now holds it, which is
     *  what the app then trusts -- not the values it just sent. */
    suspend fun update(token: String, patch: ProfileUpdate): ApiResult<Me> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/me")
                .header("Authorization", "Bearer $token")
                .patch(ApiJson.encodeToString(patch).toRequestBody(JSON_MEDIA))
                .build()
            call(request)
        }

    /**
     * POST /v1/me/avatar with the JPEG bytes as the whole body.
     *
     * Not multipart: there is one field, and multipart would mean a boundary
     * format on both ends to carry a single blob.
     */
    suspend fun uploadAvatar(token: String, jpeg: ByteArray): ApiResult<Me> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/me/avatar")
                .header("Authorization", "Bearer $token")
                .post(jpeg.toRequestBody(JPEG_MEDIA))
                .build()
            // The route answers { image }, not the whole profile, so the
            // caller re-reads /v1/me rather than being handed a half object.
            when (val result = call<AvatarSet>(request)) {
                is ApiResult.Ok -> get(token)
                is ApiResult.Failure -> result
                is ApiResult.Offline -> result
            }
        }

    @Serializable
    private data class AvatarSet(val image: String? = null)

    private inline fun <reified T> call(request: Request): ApiResult<T> = try {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                parseFailure(response.code, body, response.header("Retry-After"))
            } else {
                runCatching { ApiJson.decodeFromString<T>(body.orEmpty()) }.fold(
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
        ApiResult.Offline("No connection. Check your network and try again.")
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val JPEG_MEDIA = "image/jpeg".toMediaType()
    }
}
