package io.joinasr.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.joinasr.app.R

/**
 * Inter, bundled rather than downloaded. Downloadable Fonts would keep ~1.6MB
 * out of the APK, but it fails silently — a device without the provider, or
 * without network at first launch, renders the whole app in Roboto and nobody
 * finds out. The design is specified in Inter; shipping it is the only way
 * the shipped app is the designed app.
 *
 * Licence: SIL OFL 1.1, see android/LICENSES/Inter-OFL.txt.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * The type scale, taken from the Figma frames rather than from Material's
 * defaults. Kept as named roles because the same style appears on many
 * screens: the eyebrow above every title, the label above every field.
 */
object AsrType {
    /** "RULE YOUR MIND" — accent-coloured, wide-tracked, above a title. */
    val Eyebrow = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        // 1.92px at 12px is exactly 0.16em; expressed relatively so it stays
        // correct when the reader has scaled their font size up.
        letterSpacing = 0.16.em,
    )

    /** The one big line on a screen. 44sp on Welcome, 38-42sp elsewhere. */
    fun display(size: Int = 44) = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = (size * 1.11f).sp,
    )

    val Body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Text typed into, or shown as a placeholder inside, a field. */
    val Field = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp)

    /** The small label above a field, and inline links. */
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)

    /** A card's own heading, as on the permission cards. */
    val CardTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

    /** A row's heading inside a list of them. */
    val RowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    val Legal = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp)
}
