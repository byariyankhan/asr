package io.joinasr.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The server's error shape, from docs/API.md:
 *
 *   { "error": "pact_active", "message": "You already have an active pact." }
 *
 * Better Auth's own routes under /api/auth answer with `{ "message": ... }`
 * and sometimes a `code`, so both are optional and neither is trusted to be
 * there.
 */
@Serializable
private data class ErrorBody(
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    @SerialName("issues") val issues: List<JsonIssue>? = null,
) {
    @Serializable
    data class JsonIssue(val message: String? = null, val path: List<String>? = null)
}

internal val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Turns a failed response into something a screen can show.
 *
 * The fallbacks matter more than the happy path: an error body can be empty,
 * truncated, or an HTML page from a proxy that never reached the app. In
 * every one of those cases the person still needs a sentence, and it must
 * not be the raw body — a wall of HTML in a form is worse than a generic
 * apology.
 */
fun parseFailure(code: Int, body: String?, retryAfter: String?): ApiResult.Failure {
    val parsed = body?.takeIf { it.isNotBlank() }?.let {
        runCatching { ApiJson.decodeFromString<ErrorBody>(it) }.getOrNull()
    }
    val slug = parsed?.error ?: parsed?.code
    val fromIssues = parsed?.issues?.firstNotNullOfOrNull { it.message }
    val message = parsed?.message?.takeIf { it.isNotBlank() }
        ?: fromIssues
        ?: defaultMessage(code, slug)
    return ApiResult.Failure(
        code = code,
        error = slug,
        message = message,
        retryAfterSeconds = retryAfter?.toIntOrNull(),
    )
}

private fun defaultMessage(code: Int, slug: String?): String = when {
    slug == "invalid_body" -> "Please check what you entered."
    code == 401 -> "Your email or password is not right."
    code == 403 -> "You do not have access to that."
    code == 404 -> "That is not there."
    code == 409 -> "That conflicts with something already set up."
    code == 429 -> "Too many attempts. Wait a moment and try again."
    code >= 500 -> "Something broke on our side. Try again shortly."
    else -> "That did not work."
}
