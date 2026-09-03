package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * The Better Auth routes under /api/auth, which are the only ones that do not
 * take a bearer token — they issue it.
 *
 * The token arrives in the `set-auth-token` response header, not in the body:
 * that is what the bearer plugin does, and it is the whole reason this app
 * needs no cookie jar. A 200 without that header is treated as a failure
 * rather than a silent signed-out success.
 */
class AuthApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    @Serializable
    private data class SignUpBody(val email: String, val password: String, val name: String)

    @Serializable
    private data class SignInBody(val email: String, val password: String)

    /**
     * Better Auth requires a name at sign-up, and the designed screen (Figma
     * 02) asks only for an email and a password. Rather than add a field the
     * design does not have, the email's local part stands in until the About
     * You screen (Figma 03) sends the real one to PATCH /v1/me. It is never
     * shown as a name anywhere before then.
     */
    suspend fun signUp(email: String, password: String): ApiResult<String> {
        val placeholderName = email.substringBefore('@').take(80).ifBlank { "Asr user" }
        return post(
            path = "/api/auth/sign-up/email",
            json = ApiJson.encodeToString(SignUpBody(email, password, placeholderName)),
        )
    }

    suspend fun signIn(email: String, password: String): ApiResult<String> = post(
        path = "/api/auth/sign-in/email",
        json = ApiJson.encodeToString(SignInBody(email, password)),
    )

    /** Returns the session token on success. */
    private suspend fun post(path: String, json: String): ApiResult<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .post(json.toRequestBody(JSON_MEDIA))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    tokenFrom(response)
                }
            } catch (e: IOException) {
                // No answer at all: aeroplane mode, captive wifi, DNS, TLS.
                // Deliberately not reporting e.message to the person — it is
                // written for a developer and often names a hostname.
                ApiResult.Offline("No connection. Check your network and try again.")
            }
        }

    private fun tokenFrom(response: Response): ApiResult<String> {
        val body = response.body?.string()
        if (!response.isSuccessful) {
            return parseFailure(response.code, body, response.header("Retry-After"))
        }
        val token = response.header(TOKEN_HEADER)
        return if (token.isNullOrBlank()) {
            // A success with no token is a server or proxy problem, not
            // something the person did. Saying "signed in" and then failing
            // every subsequent call would be worse.
            ApiResult.Failure(
                code = response.code,
                error = "no_session_token",
                message = "Signed in, but no session came back. Try again.",
            )
        } else {
            ApiResult.Ok(token)
        }
    }

    private companion object {
        const val TOKEN_HEADER = "set-auth-token"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
