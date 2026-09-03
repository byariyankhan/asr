package io.joinasr.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.challenge.ChallengeDuration
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrStepper
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 04 — Setup / Challenge Duration (node 40:2).
 *
 * The first setup step, and the only one with no way back: there is nothing
 * behind it but signing out, and the frame has no chevron for that reason.
 *
 * "Custom" in the design is a row with a chevron, leading to a screen that
 * was never drawn. It opens a stepper in place instead. A second screen to
 * choose one number would be the wrong shape, and leaving the row to do
 * nothing would be worse.
 */
@Composable
fun ChallengeDurationScreen(
    onContinue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var days by remember { mutableIntStateOf(ChallengeDuration.DEFAULT_DAYS) }
    var customOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("SETUP 1 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(20.dp))
        Text(
            "Choose your\nchallenge.",
            style = AsrType.display(40),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "How long do you want to commit?",
            style = AsrType.Body,
            color = AsrColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "You can extend it later, but you can't shorten it once it starts.",
            style = AsrType.Label,
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(28.dp))
        val presets = ChallengeDuration.Presets
        for (row in presets.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                for (preset in row) {
                    DurationTile(
                        days = preset.days,
                        caption = preset.caption,
                        selected = !customOpen && days == preset.days,
                        onClick = {
                            days = preset.days
                            customOpen = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        CustomRow(
            open = customOpen,
            days = days,
            onOpen = {
                customOpen = true
                // Starts from whatever was selected, so opening Custom on 30
                // does not silently drop the person back to 14.
                days = ChallengeDuration.clamp(days)
            },
            onChange = { days = it },
        )

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.SurfaceRaised, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 17.dp),
        ) {
            Text(ChallengeDuration.note(days), style = AsrType.Label, color = AsrColors.TextSecondary)
        }

        Spacer(Modifier.height(28.dp))
        AsrPrimaryButton(text = "Continue", onClick = { onContinue(days) })
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DurationTile(
    days: Int,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(94.dp)
            .clip(shape)
            .background(if (selected) AsrColors.SurfaceRaised else AsrColors.Field)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) AsrColors.Accent else AsrColors.FieldBorder,
                shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                days.toString(),
                style = AsrType.display(30),
                color = if (selected) AsrColors.Accent else AsrColors.TextPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "DAYS",
                style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                color = if (selected) AsrColors.Accent else AsrColors.TextSecondary,
                modifier = Modifier.weight(1f).padding(bottom = 5.dp),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AsrColors.Accent),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            caption,
            style = AsrType.Label.copy(fontSize = 12.sp),
            color = if (selected) AsrColors.TextPrimary else AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun CustomRow(
    open: Boolean,
    days: Int,
    onOpen: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (open) AsrColors.SurfaceRaised else AsrColors.Field)
            .border(1.dp, if (open) AsrColors.Accent else AsrColors.FieldBorder, shape)
            .then(if (open) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen))
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Custom",
                    style = AsrType.CardTitle.copy(fontSize = 16.sp),
                    color = AsrColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (open) {
                        "$days days, between ${ChallengeDuration.MINIMUM_DAYS} and " +
                            "${ChallengeDuration.MAXIMUM_DAYS}"
                    } else {
                        "Choose your own duration"
                    },
                    style = AsrType.Label.copy(fontSize = 12.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            if (!open) {
                Text("›", style = AsrType.display(28), color = AsrColors.Accent)
            }
        }

        if (open) {
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AsrStepper(
                    value = days.toString(),
                    label = "challenge length in days",
                    canDecrease = days > ChallengeDuration.MINIMUM_DAYS,
                    canIncrease = days < ChallengeDuration.MAXIMUM_DAYS,
                    onDecrease = { onChange(ChallengeDuration.clamp(days - 1)) },
                    onIncrease = { onChange(ChallengeDuration.clamp(days + 1)) },
                )
            }
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ChallengeDurationPreview() {
    AsrTheme { ChallengeDurationScreen(onContinue = {}) }
}
