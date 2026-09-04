package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
private data class ChangePassword(
    val currentPassword: String,
    val newPassword: String,
    val revokeOtherSessions: Boolean = true,
)

@Serializable
private data class ForgetPassword(val email: String)

@Serializable
private data class ResetPassword(val newPassword: String, val token: String)

@Serializable
private data class DeleteAccount(val password: String)

/**
 * Everything about the account that is not the profile: the password, other
 * sessions, and deletion.
 *
 * Most of it lives under /api/auth, which is Better Auth's own surface
 * rather than this app's /v1. Deletion is the exception — it is a DELETE on
 * /v1/me, because it does more than remove a login: it schedules the account
 * and everything attached to it for removal, and signing in again inside the
 * grace window cancels that.
 *
 * Changing an email address is not here, because the server does not offer
 * it. Better Auth's change-email endpoint is off in this deployment, and a
 * client that called it would get a 404 dressed up as a bug in the app.
 */
class AccountApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun changePassword(
        token: String,
        current: String,
        next: String,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        post(
            path = "/api/auth/change-password",
            token = token,
            body = ApiJson.encodeToString(ChangePassword(current, next)),
        )
    }

    /**
     * Asks for a reset email. Always reports success to the caller when the
     * server accepts it, because the server deliberately answers the same
     * way for an address it has never seen: telling somebody which emails
     * have accounts is how account lists get harvested.
     */
    suspend fun sendResetEmail(email: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        post(
            path = "/api/auth/forget-password",
            token = null,
            body = ApiJson.encodeToString(ForgetPassword(email.trim())),
        )
    }

    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            post(
                path = "/api/auth/reset-password",
                token = null,
                body = ApiJson.encodeToString(ResetPassword(newPassword, token)),
            )
        }

    suspend fun revokeOtherSessions(token: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        post(path = "/api/auth/revoke-other-sessions", token = token, body = "{}")
    }

    /**
     * Schedules deletion and signs the person out. The password is checked
     * by the server against a real sign-in, so a stolen unlocked phone
     * cannot delete somebody's account by tapping through.
     */
    suspend fun deleteAccount(token: String, password: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/me")
                .header("Authorization", "Bearer $token")
                .delete(ApiJson.encodeToString(DeleteAccount(password)).toRequestBody(JSON_MEDIA))
                .build()
            send(request)
        }

    private fun post(path: String, token: String?, body: String): ApiResult<Unit> {
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody(JSON_MEDIA))
        if (token != null) builder.header("Authorization", "Bearer $token")
        return send(builder.build())
    }

    private fun send(request: Request): ApiResult<Unit> = try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                ApiResult.Ok(Unit)
            } else {
                parseFailure(response.code, response.body?.string(), response.header("Retry-After"))
            }
        }
    } catch (e: IOException) {
        ApiResult.Offline("No connection. Check your network and try again.")
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
