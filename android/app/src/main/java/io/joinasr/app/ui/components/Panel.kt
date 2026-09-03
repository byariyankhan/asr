package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.theme.AsrColors

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
