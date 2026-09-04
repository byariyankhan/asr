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

    /**
     * A card that is *about* something the app does, rather than a container
     * for input. Figma uses it for the permission cards and notes: a hair
     * greener than Surface, which is what makes a stack of them read as
     * grouped rather than as one long panel.
     */
    val SurfaceRaised = Color(0xFF0C1210)

    /** The filled background behind an accent-coloured pill. */
    val AccentMuted = Color(0xFF071A13)

    /**
     * A row the person has chosen: the app picker, and the limit rows after
     * it. Lighter than Surface rather than accent-tinted, because the accent
     * border already says "chosen" and a green fill under a green border
     * makes the app name harder to read, not easier.
     */
    val SurfaceSelected = Color(0xFF121513)

    /**
     * A card that sits *below* the ground rather than above it: the reset
     * note and the close button on the block screen. The block screen is
     * covering somebody's app against their wishes, and everything on it is
     * quieter than the rest of the app on purpose.
     */
    val SurfaceSunken = Color(0xFF0B0D0C)

    /** The unfilled part of a progress bar. */
    val Track = Color(0xFF17201D)

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

    /**
     * NOT from Figma. The file has no error state yet, and a form that
     * refuses a password has to say so in something other than the same grey
     * as its own labels. Chosen to sit at a similar lightness to Accent so it
     * does not shout, and marked here so it is replaced rather than copied
     * the moment the designer draws one.
     */
    val Error = Color(0xFFE5484D)

    /**
     * The delete-account screen, and nowhere else. Figma 31 is the only frame
     * in the file that uses red as a surface rather than as a line of text,
     * and it is the only screen where a mistake cannot be undone.
     */
    val Danger = Color(0xFFFF6B6B)
    val DangerMuted = Color(0xFF231011)
}
