package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * The one filled button in the app: 58dp tall, fully rounded, accent filled.
 * Height and radius are the designed values rather than Material's — a 40dp
 * default button sitting next to a 58dp field reads as a mistake.
 */
@Composable
fun AsrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(if (enabled) AsrColors.Accent else AsrColors.SurfaceBorder)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AsrType.Button,
            color = if (enabled) AsrColors.OnAccent else AsrColors.TextSecondary,
        )
    }
}

/**
 * A sentence ending in one tappable phrase, as in
 * "Already have an account?  Log in".
 *
 * Two Text nodes rather than one annotated string: only the trailing phrase
 * is a target, and it should be a real one — a clickable span inside a
 * sentence is announced as part of the sentence and is hard to hit.
 * The tap area is padded out to a comfortable height for the same reason.
 */
@Composable
fun AsrInlineLink(
    prefix: String,
    linkText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, style = AsrType.Label, color = AsrColors.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(
            text = linkText,
            style = AsrType.Label,
            color = AsrColors.TextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 10.dp),
        )
    }
}

/** A standalone accent-coloured link, as in "Forgot password?". */
@Composable
fun AsrTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AsrType.Label,
        color = AsrColors.Accent,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}
