package io.joinasr.app.usage

/**
 * One thing the system told us happened, reduced to the three cases that
 * change how long an app has been in front of somebody.
 *
 * Android's own event stream carries a dozen kinds and its constants moved
 * names between versions. Translating at the edge, in [UsageReader], keeps
 * the arithmetic below free of Android entirely: the hard part of measuring
 * screen time is the walk over these events, and a walk over a list of data
 * classes can be tested on a laptop.
 */
data class UsageEvent(
    val packageName: String,
    val kind: Kind,
    val timestampMillis: Long,
) {
    enum class Kind {
        /** This app came to the front. Whatever was in front no longer is. */
        Resumed,

        /** This app left the front. */
        Paused,

        /**
         * Something ended everybody's foreground time without pausing an
         * app: the device shut down, or the screen went off in a way that
         * produced no pause. Closes whatever was open, whichever app it was.
         */
        Interrupted,
    }
}
