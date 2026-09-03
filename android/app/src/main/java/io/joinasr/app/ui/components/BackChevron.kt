package io.joinasr.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * The back chevron, drawn as text rather than shipped as an icon: the design
 * uses the character U+2039, and a 48dp touch target is added around it
 * because the glyph itself is far below the minimum a thumb can hit.
 */
@Composable
fun AsrBackChevron(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", style = AsrType.display(30), color = AsrColors.TextPrimary)
    }
}
