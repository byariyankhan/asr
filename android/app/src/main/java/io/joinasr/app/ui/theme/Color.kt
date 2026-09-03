package io.joinasr.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Read out of the Figma file, not chosen here. Every value below appears
 * identically on screens 01, 02 and 32, which is why they are treated as the
 * palette rather than as three screens that happen to agree.
 *
 * Names say the role, not the colour: `Accent` can change to something other
 * than green without every call site reading as a lie.
 */
object AsrColors {
    /** The app's ground. Not pure black — #0a0a0a. */
    val Background = Color(0xFF0A0A0A)

    /** Raised panels, e.g. the auth form card. */
    val Surface = Color(0xFF0E1110)
    val SurfaceBorder = Color(0xFF1F2925)

    /** Text inputs sit slightly darker than the panel they are inside. */
    val Field = Color(0xFF0E0E0E)
    val FieldBorder = Color(0xFF212926)

    val Accent = Color(0xFF12B886)

    /** Text on top of Accent. A near-black green, never plain black. */
    val OnAccent = Color(0xFF04110D)

    val TextPrimary = Color(0xFFF5F5F2)
    val TextSecondary = Color(0xFF9A9F9C)

    /** Legal and disclaimer lines only. */
    val TextTertiary = Color(0xFF6B706E)
}
