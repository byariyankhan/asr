package io.joinasr.app

import android.content.Intent

/**
 * A link that opened the app.
 *
 * Two of them exist, and both are App Links on joinasr.io rather than a
 * custom scheme, so a link that arrives on a phone without the app still
 * lands on a real web page instead of an error.
 */
sealed interface DeepLink {
    /** joinasr.io/reset/<token> — Figma 35. */
    data class Reset(val token: String) : DeepLink

    /** joinasr.io/w/<code> — Figma 18. */
    data class Invite(val code: String) : DeepLink

    companion object {
        /**
         * Reads one out of an intent, or null.
         *
         * Anything that is not exactly one of the two shapes yields null —
         * the launcher icon, a share, a link to some other path — so a stray
         * intent can never put somebody on a reset screen with an empty
         * token or an invite screen with no code.
         */
        fun from(intent: Intent?): DeepLink? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            val segments = intent.data?.pathSegments ?: return null
            if (segments.size != 2) return null
            val value = segments[1].takeIf { it.isNotBlank() } ?: return null
            return when (segments[0]) {
                "reset" -> Reset(value)
                "w" -> Invite(value)
                else -> null
            }
        }
    }
}
