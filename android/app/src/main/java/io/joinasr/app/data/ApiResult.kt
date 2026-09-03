package io.joinasr.app.data

/**
 * What a call to the API can be. Deliberately not an exception-carrying
 * Result: a wrong password and a dead network are both ordinary outcomes a
 * screen has to say something about, and neither is a programming error.
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /**
     * The server answered and refused. [code] is the HTTP status, [error] the
     * machine-readable slug from the envelope (docs/API.md), and [message]
     * the sentence to show a person.
     */
    data class Failure(
        val code: Int,
        val error: String?,
        val message: String,
        val retryAfterSeconds: Int? = null,
    ) : ApiResult<Nothing>

    /** The request never got an answer: no network, DNS, TLS, timeout. */
    data class Offline(val message: String) : ApiResult<Nothing>
}
