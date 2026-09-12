package com.joinasr.app

import android.content.Intent

/**
 * A link that opened the app.
 *
 * One of them exists, and it is an App Link on joinasr.com rather than a
 * custom scheme, so a link that arrives on a phone without the app still
 * lands on a real web page instead of an error. A password reset used to be
 * the other; it is a code typed into the app now, and needs no link at all.
 */
sealed interface DeepLink {
    /** joinasr.com/w/<code> — Figma 18. */
    data class Invite(val code: String) : DeepLink

    /**
     * Figma 21, opened from the block screen.
     *
     * Not a URL: the block screen is this app's own activity and hands the
     * package over directly. It lives in the same reader because it arrives
     * the same way — as an intent that decides which screen opens — and two
     * places deciding that is how one of them gets forgotten.
     */
    data class Earn(val packageName: String) : DeepLink

    companion object {
        /**
         * Reads one out of an intent, or null.
         *
         * Anything that is not exactly the one shape yields null — the
         * launcher icon, a share, a link to some other path — so a stray
         * intent can never put somebody on an invite screen with no code.
         */
        /** The extra the block screen sends. */
        const val EXTRA_EARN_FOR = "com.joinasr.app.earn_for"

        fun from(intent: Intent?): DeepLink? {
            intent?.getStringExtra(EXTRA_EARN_FOR)
                ?.takeIf { it.isNotBlank() }
                ?.let { return Earn(it) }
            if (intent?.action != Intent.ACTION_VIEW) return null
            val segments = intent.data?.pathSegments ?: return null
            if (segments.size != 2) return null
            val value = segments[1].takeIf { it.isNotBlank() } ?: return null
            return if (segments[0] == "w") Invite(value) else null
        }
    }
}
