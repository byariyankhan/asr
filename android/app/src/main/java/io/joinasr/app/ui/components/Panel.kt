package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * The raised card the auth forms sit in: 24dp radius, one-pixel border, and
 * 18dp of inset — which is where the design's 42px left edge comes from,
 * 24 of screen margin plus 18 of panel padding.
 */
@Composable
fun AsrPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, RoundedCornerShape(24.dp))
            .border(1.dp, AsrColors.SurfaceBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        content = content,
    )
}

/**
 * A card the app talks *through*: permission explanations, notes, rows. Takes
 * its radius and ground from the caller because the design uses several --
 * 16 for a footnote, 18 for a row, 20 and 22 for the big explanatory cards --
 * and rounding them all to one value is the kind of tidying that makes a
 * screen stop matching its design for no gain.
 */
@Composable
fun AsrCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    background: Color = AsrColors.SurfaceRaised,
    padding: Dp = 17.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(radius))
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

/**
 * The small capsule that states a status: REQUIRED, ON, NOT ENABLED. Accent
 * when it is a good or expected state, muted grey when it is not -- the
 * design uses colour alone here, so the words carry the meaning too.
 */
@Composable
fun AsrPill(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = true,
) {
    Box(
        modifier = modifier
            .background(
                if (highlighted) AsrColors.AccentMuted else AsrColors.SurfaceRaised,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = if (highlighted) AsrColors.Accent else AsrColors.TextSecondary,
        )
    }
}
