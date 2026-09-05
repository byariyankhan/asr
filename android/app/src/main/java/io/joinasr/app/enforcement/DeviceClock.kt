package io.joinasr.app.enforcement

import android.content.Context
import android.provider.Settings

/** What the phone's clock can be trusted for. */
object DeviceClock {

    /**
     * Whether the phone sets its own time from the network.
     *
     * The date is the easiest thing on a phone to change, and a challenge
     * whose last day was decided by it could be finished from Settings in
     * ten seconds. The server checks every completion against its own clock;
     * this is for the moment the server cannot be asked. A phone that takes
     * its time from the network is trusted to finish a challenge offline. A
     * phone somebody sets by hand waits until the server can be asked.
     */
    fun isAutomatic(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
        }.getOrDefault(false)
}
