package io.joinasr.app

/**
 * How a duration in minutes is written everywhere in the app: the dashboard
 * ring, the block screen, the witness's view of someone's day.
 *
 * One function rather than a format string at each call site, because these
 * three places must agree — a limit that reads "1h 20m" on the dashboard and
 * "80m" on the block screen looks like two different numbers to the person
 * being told they are out of time.
 */
fun formatMinutes(minutes: Int): String {
    require(minutes >= 0) { "minutes must not be negative: $minutes" }
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
