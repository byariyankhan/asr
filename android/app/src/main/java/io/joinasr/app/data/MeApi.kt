package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The subset of GET /v1/me the app reads today. */
@Serializable
data class Me(
    val id: String,
    val name: String,
    val email: String,
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
}
