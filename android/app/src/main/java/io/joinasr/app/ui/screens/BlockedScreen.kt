package io.joinasr.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.formatMinutes
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 20 — Blocked App / Limit Reached (node 128:2).
 *
 * Drawn over another app by [io.joinasr.app.enforcement.BlockOverlay], not
 * shown inside this one, so it has no back stack and no activity of its
 * own. Everything it needs is a parameter.
 *
 * The frame also carries an "Earn +10m" button and the note above it,
 * leading to the earn-time flow in Figma 21 to 24. That flow does not exist
 * yet, so the button is not drawn: a button that says a person can earn ten
 * minutes and then does nothing is worse than one that was never offered,
 * and on the one screen in this app that exists to be trusted. It goes in
 * when 21 to 24 do.
 *
 * What is left is a single way out, and it is the honest one: leave the app.
 */
@Composable
fun BlockedScreen(
    appLabel: String,
    icon: ImageBitmap?,
    usedMinutes: Int,
    limitMinutes: Int,
    availableAgain: String,
    onLeave: () -> Unit,
    /** Opens Figma 21. The one moment somebody wants to earn time is this one. */
    onEarnTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        CloseButton(onLeave)

        Spacer(Modifier.height(48.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LockedAppMark(appLabel = appLabel, icon = icon)
        }

        Spacer(Modifier.height(26.dp))
        Centered("LIMIT REACHED", AsrType.Eyebrow, AsrColors.Accent)
        Spacer(Modifier.height(18.dp))
        Centered("$appLabel is locked.", AsrType.display(36), AsrColors.TextPrimary)
        Spacer(Modifier.height(14.dp))
        Centered(
            "You've used today's ${formatMinutes(limitMinutes)} limit.",
            AsrType.Body,
            AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(28.dp))
        UsageSummary(appLabel = appLabel, usedMinutes = usedMinutes, limitMinutes = limitMinutes)

        Spacer(Modifier.height(18.dp))
        ResetNote(availableAgain)

        Spacer(Modifier.weight(1f))
        AsrPrimaryButton(text = "Close $appLabel", onClick = onLeave)
        Spacer(Modifier.height(14.dp))
        // Below the close button, not above it. The default this screen
        // offers is to stop, and earning more is the second thought — an app
        // whose block screen leads with a way around itself is a slot
        // machine with extra steps.
        EarnMore(onClick = onEarnTime)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The way out, under the way to stop.
 *
 * Two lines rather than one. The question is what a person standing here is
 * already asking themselves, and putting it in their words first makes the
 * offer an answer instead of a sales line; the second line is the only thing
 * on this screen that is tappable and green apart from the close button, so
 * it does not need to shout to be found.
 */
@Composable
private fun EarnMore(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Need more time?",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Earn ${EarnRules.REWARD_MINUTES} minutes",
            style = AsrType.Button.copy(fontSize = 15.sp),
            color = AsrColors.Accent,
        )
    }
}

@Composable
private fun Centered(text: String, style: TextStyle, color: Color) {
    Text(
        text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("×", style = AsrType.display(24), color = AsrColors.TextSecondary)
    }
}

/** The ring, the app's own icon, and the padlock badge over its corner. */
@Composable
private fun LockedAppMark(appLabel: String, icon: ImageBitmap?) {
    Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = AsrColors.Accent,
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .background(AsrColors.Background)
                .border(1.dp, AsrColors.FieldBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    appLabel.take(1).uppercase(),
                    style = AsrType.display(28),
                    color = AsrColors.Accent,
                )
            }
        }

        Box(
            modifier = Modifier
                // Placed by offset from the centre rather than by an
                // alignment, because it sits over the icon's lower-right
                // corner and no alignment names that spot.
                .align(Alignment.Center)
                .offset(x = 25.dp, y = 23.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AsrColors.AccentMuted)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("▣", style = AsrType.Label.copy(fontSize = 13.sp), color = AsrColors.Accent)
        }
    }
}

@Composable
private fun UsageSummary(appLabel: String, usedMinutes: Int, limitMinutes: Int) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appLabel,
                    style = AsrType.CardTitle,
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // The real number, not the limit. Being over happens --
                    // the phone was asleep, the service was killed, access
                    // was granted late -- and rounding it down to the limit
                    // would be the app quietly lying to make itself look
                    // precise.
                    "${formatMinutes(usedMinutes)} of ${formatMinutes(limitMinutes)} used today",
                    style = AsrType.Field.copy(fontSize = 14.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            LockedPill()
        }

        Spacer(Modifier.height(16.dp))
        UsageBar(usedMinutes = usedMinutes, limitMinutes = limitMinutes)
    }
}

@Composable
private fun LockedPill() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(AsrColors.AccentMuted)
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("LOCKED", style = AsrType.Eyebrow.copy(fontSize = 11.sp), color = AsrColors.Accent)
    }
}

@Composable
private fun UsageBar(usedMinutes: Int, limitMinutes: Int) {
    val fraction = if (limitMinutes <= 0) {
        1f
    } else {
        (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AsrColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AsrColors.Accent),
        )
    }
}

@Composable
private fun ResetNote(availableAgain: String) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 17.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "AVAILABLE AGAIN",
                style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                color = AsrColors.TextTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                availableAgain,
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("↻", style = AsrType.display(26), color = AsrColors.Accent)
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun BlockedPreview() {
    AsrTheme {
        BlockedScreen(
            appLabel = "TikTok",
            icon = null,
            usedMinutes = 20,
            limitMinutes = 20,
            availableAgain = "Tomorrow at 12:00 AM",
            onLeave = {},
            onEarnTime = {},
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun BlockedOverPreview() {
    AsrTheme {
        BlockedScreen(
            appLabel = "Instagram",
            icon = null,
            usedMinutes = 47,
            limitMinutes = 30,
            availableAgain = "Tomorrow at 12:00 AM",
            onLeave = {},
            onEarnTime = {},
        )
    }
}
