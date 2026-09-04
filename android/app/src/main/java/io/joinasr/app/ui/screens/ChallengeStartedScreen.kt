package io.joinasr.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.daysLabel
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 12 — Challenge / Started (node 125:2).
 *
 * The only screen in the app whose job is to make somebody feel something.
 * Everything on it is nonetheless true: the three lines each report a state
 * that was checked rather than assumed, and the protection line says PAUSED
 * when a permission is missing, because a congratulation that is not true is
 * the fastest way to lose the person on their first day.
 */
@Composable
fun ChallengeStartedScreen(
    days: Int,
    witnesses: Int,
    protectionReady: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(96.dp))
        Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = AsrColors.Accent,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Text("✓", style = AsrType.display(42), color = AsrColors.Accent)
        }

        Spacer(Modifier.height(30.dp))
        Text("CHALLENGE LIVE", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            "You're locked in.",
            style = AsrType.display(38),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your $days-day challenge has started. " +
                if (protectionReady) {
                    "Protection is active and your rules are now locked."
                } else {
                    "Your rules are locked, but protection is off until you grant " +
                        "the permissions."
                },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(30.dp))
        StatusCard(days = days, protectionReady = protectionReady)

        Spacer(Modifier.height(18.dp))
        Assurance(
            title = "Rules locked",
            detail = "Apps, limits and duration can't be reduced.",
            ok = true,
        )
        Spacer(Modifier.height(12.dp))
        Assurance(
            title = if (witnesses == 0) {
                "No witnesses attached"
            } else if (witnesses == 1) {
                "1 witness attached"
            } else {
                "$witnesses witnesses attached"
            },
            detail = if (witnesses == 0) {
                "Nobody will be told if the pact breaks."
            } else {
                "They'll be notified if the pact is breached."
            },
            ok = witnesses > 0,
        )
        Spacer(Modifier.height(12.dp))
        Assurance(
            title = if (protectionReady) "App blocking active" else "App blocking off",
            detail = if (protectionReady) {
                "Selected apps will lock when limits are reached."
            } else {
                "Turn on usage access and app blocking to enforce your limits."
            },
            ok = protectionReady,
        )

        Spacer(Modifier.height(28.dp))
        AsrPrimaryButton(text = "Go to dashboard", onClick = onContinue)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatusCard(days: Int, protectionReady: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$days-day challenge",
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Day 1 · ${daysLabel(days - 1)} left",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .height(30.dp)
                .background(
                    if (protectionReady) AsrColors.AccentMuted else AsrColors.Background,
                    RoundedCornerShape(15.dp),
                )
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (protectionReady) "PROTECTED" else "NOT PROTECTED",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = if (protectionReady) AsrColors.Accent else AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Assurance(title: String, detail: String, ok: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "✓" else "!",
            style = AsrType.display(20),
            color = if (ok) AsrColors.Accent else AsrColors.TextTertiary,
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = AsrType.Field.copy(fontSize = 15.sp), color = AsrColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun StartedPreview() {
    AsrTheme {
        ChallengeStartedScreen(
            days = 14,
            witnesses = 3,
            protectionReady = true,
            onContinue = {},
        )
    }
}
