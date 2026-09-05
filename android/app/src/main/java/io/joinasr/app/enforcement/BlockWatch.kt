package io.joinasr.app.enforcement

/**
 * What the loop has done about the app it is blocking, and what it should
 * do next.
 *
 * Blocking is a background activity launch, and from Android 10 a launch the
 * system refuses is not an error: `startActivity` returns, nothing appears,
 * and the person carries on scrolling. The one grant that exempts this app,
 * "display over other apps", is not enough on every phone -- MIUI keeps a
 * second switch for pop-ups from the background, and a handful of other
 * skins do the same under other names. So the loop used to launch once per
 * blocked app and believe it, which on those phones was a block screen that
 * never came and a dashboard that said LOCKED over it.
 *
 * This is the loop not believing it. The activity is launched; if the app
 * being blocked is still in front [launchGraceMillis] later, the launch was
 * dropped, and the loop falls back to a window it draws itself, which needs
 * only the overlay grant and no exemption. If that cannot be shown either,
 * it tries again every [retryMillis] rather than never, so the dashboard's
 * warning is written and a block that starts working again is used.
 *
 * Pure so it can be argued with in a test. The service owns the clock and
 * the launching; this only remembers what was tried and when.
 */
class BlockWatch(
    private val launchGraceMillis: Long = LAUNCH_GRACE_MILLIS,
    private val retryMillis: Long = RETRY_MILLIS,
) {
    enum class Step {
        /** Nothing to do this pass: nothing to block, or a block already standing. */
        Nothing,

        /** Start the block activity over the app. */
        LaunchActivity,

        /** The activity did not appear. Draw the block as a window instead. */
        ShowOverlay,
    }

    enum class Via { Activity, Overlay }

    /** The app being blocked, if any. */
    var packageName: String? = null
        private set

    private var via: Via? = null
    private var sinceMillis = 0L

    /** Whether the block currently standing is the self-drawn window. */
    val showingOverlay: Boolean get() = packageName != null && via == Via.Overlay

    /**
     * One pass of the loop. [blocked] is the app in front that has run out
     * of time, or null when nothing in front should be blocked.
     */
    fun next(blocked: String?, nowMillis: Long): Step {
        if (blocked == null) {
            clear()
            return Step.Nothing
        }
        if (blocked != packageName) {
            packageName = blocked
            via = null
            sinceMillis = nowMillis
            return Step.LaunchActivity
        }
        val elapsed = nowMillis - sinceMillis
        return when (via) {
            // Launched, and the blocked app is still what is in front. If it
            // had worked, this app would be in front instead.
            Via.Activity -> if (elapsed >= launchGraceMillis) Step.ShowOverlay else Step.Nothing
            Via.Overlay -> Step.Nothing
            null -> if (elapsed >= retryMillis) Step.LaunchActivity else Step.Nothing
        }
    }

    /** What the last step actually put up, and when. */
    fun shown(via: Via, nowMillis: Long) {
        this.via = via
        sinceMillis = nowMillis
    }

    /** The last step could not be carried out at all. */
    fun failed(nowMillis: Long) {
        via = null
        sinceMillis = nowMillis
    }

    fun clear() {
        packageName = null
        via = null
        sinceMillis = 0L
    }

    companion object {
        /**
         * How long a launched activity gets to show up in the usage stream
         * before it is taken as dropped. A launch that lands is in front
         * within a second on anything; the margin is for a phone under load.
         */
        const val LAUNCH_GRACE_MILLIS = 2_500L

        /** How long to wait before trying again after neither route worked. */
        const val RETRY_MILLIS = 10_000L
    }
}
