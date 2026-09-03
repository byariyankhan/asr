package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/** The four places the app has. */
enum class AsrTab(val label: String, val glyph: String) {
    Home("Home", "⌂"),
    Progress("Progress", "▥"),
    Witnesses("Witnesses", "◎"),
    Profile("Profile", "○"),
}

/**
 * The bar along the bottom, from Figma 12 and drawn identically on 13, 14,
 * 15 and 28.
 *
 * The glyphs are the characters the design uses rather than icon assets.
 * That is what the Figma file specifies, they carry their meaning next to
 * the label under them, and a set of hand-drawn icons that only approximates
 * them would look worse than the design rather than better.
 */
@Composable
fun AsrBottomNav(
    selected: AsrTab,
    onSelect: (AsrTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(shape)
            .background(AsrColors.Surface)
            .border(1.dp, AsrColors.SurfaceBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in AsrTab.entries) {
            val active = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.glyph,
                    style = AsrType.display(16),
                    color = if (active) AsrColors.Accent else AsrColors.TextTertiary,
                )
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
