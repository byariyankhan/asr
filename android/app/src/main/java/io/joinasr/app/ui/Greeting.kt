package io.joinasr.app.ui

/**
 * The line above the dashboard title.
 *
 * The design shows only "GOOD MORNING", which would be wrong for most of the
 * day. Evening runs late on purpose: somebody opening a screen-time app at
 * two in the morning is exactly who this product is for, and greeting them
 * with "GOOD MORNING" would be the app failing to notice.
 */
fun greetingFor(hourOfDay: Int): String = when (hourOfDay) {
    in 5..11 -> "GOOD MORNING"
    in 12..17 -> "GOOD AFTERNOON"
    else -> "GOOD EVENING"
}
