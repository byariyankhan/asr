package io.joinasr.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/** The four places the app has. */
enum class AsrTab(val label: String) {
    Home("Home"),
    Progress("Progress"),
    Witnesses("Witnesses"),
    Profile("Profile"),
}

/** How much room every icon in the bar gets. One number, so they match. */
private val ICON = 26.dp

/** Line weight, the same for all of them so none looks bolder than another. */
private const val STROKE = 2f

/**
 * The bar along the bottom, from Figma 12 and drawn identically on 13, 14,
 * 15 and 28.
 *
 * The icons are drawn rather than typed. They used to be the Unicode
 * characters the design names -- ⌂ ▥ ◎ ○ -- and the trouble with characters
 * is that a font decides how big each one is inside its em box. At one font
 * size the bullseye came out visibly smaller than the block beside it, so
 * Witnesses looked like a lesser tab than Progress. Raising the size raises
 * both and keeps the difference.
 *
 * Four shapes at one box size and one stroke weight is the only way they
 * actually match, and it is about thirty lines. The shapes are still the
 * design's: a house, bars, a ring around a dot.
 *
 * Profile is the person's own photo, because by the time this bar is on
 * screen the app has one, and a photograph of you is a better label for
 * "you" than a circle is.
 */
@Composable
fun AsrBottomNav(
    selected: AsrTab,
    onSelect: (AsrTab) -> Unit,
    modifier: Modifier = Modifier,
    /** For the Profile tab. Falls back to an initial, then to a plain ring. */
    profileImage: String? = null,
    profileName: String = "",
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(shape)
            .background(AsrColors.Surface)
            .border(1.dp, AsrColors.SurfaceBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in AsrTab.entries) {
            val active = tab == selected
            val tint = if (active) AsrColors.Accent else AsrColors.TextTertiary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (tab) {
                    AsrTab.Home -> Canvas(Modifier.size(ICON)) { house(tint) }
                    AsrTab.Progress -> Canvas(Modifier.size(ICON)) { bars(tint) }
                    AsrTab.Witnesses -> Canvas(Modifier.size(ICON)) { watching(tint) }
                    AsrTab.Profile -> ProfileTabIcon(profileImage, profileName, active)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    tab.label,
                    style = AsrType.Label.copy(fontSize = 10.sp),
                    color = if (active) AsrColors.Accent else AsrColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The person, ringed when this is where they are.
 *
 * The ring is drawn on the outside rather than as a border on the photo, so
 * the face is the same size on every tab and only the ring appears.
 */
@Composable
private fun ProfileTabIcon(image: String?, name: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(ICON)
            .clip(CircleShape)
            .then(
                if (active) Modifier.border(2.dp, AsrColors.Accent, CircleShape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsrProfilePhoto(
            imagePath = image,
            fallback = name,
            // Inside the ring rather than under it.
            size = if (active) 21.dp else ICON,
            initialSize = 10,
        )
    }
}

/** A roof over a body. */
private fun DrawScope.house(colour: Color) {
    val w = size.width
    val h = size.height
    val roof = Path().apply {
        moveTo(w * 0.10f, h * 0.46f)
        lineTo(w * 0.50f, h * 0.14f)
        lineTo(w * 0.90f, h * 0.46f)
    }
    drawPath(roof, colour, style = Stroke(width = STROKE, cap = StrokeCap.Round))
    // The walls, open at the top where the roof already is.
    val walls = Path().apply {
        moveTo(w * 0.21f, h * 0.42f)
        lineTo(w * 0.21f, h * 0.84f)
        lineTo(w * 0.79f, h * 0.84f)
        lineTo(w * 0.79f, h * 0.42f)
    }
    drawPath(walls, colour, style = Stroke(width = STROKE, cap = StrokeCap.Round))
}

/** Four uprights, like the block character they replaced. */
private fun DrawScope.bars(colour: Color) {
    val w = size.width
    val h = size.height
    val heights = listOf(0.46f, 0.70f, 0.34f, 0.58f)
    for ((index, tall) in heights.withIndex()) {
        val x = w * (0.19f + index * 0.207f)
        drawLine(
            colour,
            start = Offset(x, h * 0.84f),
            end = Offset(x, h * (0.84f - tall)),
            strokeWidth = STROKE + 0.6f,
            cap = StrokeCap.Round,
        )
    }
}

/** A ring around a dot: being watched. */
private fun DrawScope.watching(colour: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension * 0.40f
    drawCircle(colour, radius = outer, center = centre, style = Stroke(width = STROKE))
    drawCircle(colour, radius = size.minDimension * 0.145f, center = centre)
}
