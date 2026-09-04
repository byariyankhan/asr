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

    /**
     * Defaults are wire values here, not placeholders.
     *
     * kotlinx leaves a property at its default out of the JSON entirely
     * unless told otherwise, and this app had it untold. `reset_time` on a
     * pact snapshot defaults to "00:00" and the server requires it, so
     * every single POST /v1/pacts this app has ever sent was rejected as
     * invalid -- silently, because creating the server's copy of a
     * challenge is best-effort by design and nothing was watching it fail.
     *
     * The account therefore never had a pact on the server. Nothing
     * depended on that until a witness became something you invite to a
     * challenge, and then the invite endpoint quite correctly refused:
     * "Start a challenge before inviting witnesses to it", about a
     * challenge that had been running on the phone for days.
     *
     * The same omission dropped the activity rules out of the snapshot and
     * left `revokeOtherSessions` off every password change.
     */
    encodeDefaults = true

    /**
     * And nulls stay out.
     *
     * With defaults now encoded, every optional property would otherwise go
     * out as an explicit `null` -- which is not what any of them mean. The
     * server's optional fields are zod `.optional()`, which rejects null,
     * and PATCH /v1/me reads an absent field as "leave this alone" and a
     * present one as "set it to this". Omission is the intent in both
     * directions.
     */
    explicitNulls = false
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
