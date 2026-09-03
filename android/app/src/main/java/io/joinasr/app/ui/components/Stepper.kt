package io.joinasr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * Minus, a value, plus. Used for daily limits and for a custom challenge
 * length, which is why it is here rather than beside either of them.
 *
 * The two ends grey out when there is nothing further to go. A control that
 * looks live and does nothing when pressed reads as a broken app, not as a
 * boundary.
 */
@Composable
fun AsrStepper(
    value: String,
    label: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Less $label", canDecrease, onDecrease)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .background(AsrColors.AccentMuted, RoundedCornerShape(16.dp))
                .border(1.dp, AsrColors.Accent, RoundedCornerShape(16.dp))
                // Read out as what it is: without this a screen reader
                // announces a bare number between two unnamed buttons.
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = AsrType.Button.copy(fontSize = 13.sp), color = AsrColors.Accent)
        }
        Spacer(Modifier.width(8.dp))
        StepButton("+", "More $label", canIncrease, onIncrease)
    }
}

/**
 * 34x32 as drawn, which is under the 48dp minimum a thumb reliably hits, so
 * the tap area is grown around the visible pill rather than by making the
 * pill bigger than the design.
 */
@Composable
private fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(32.dp)
                .background(AsrColors.Background, shape)
                .border(1.dp, AsrColors.FieldBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                style = AsrType.Label.copy(fontSize = 17.sp),
                color = if (enabled) AsrColors.TextPrimary else AsrColors.TextTertiary,
            )
        }
    }
}
