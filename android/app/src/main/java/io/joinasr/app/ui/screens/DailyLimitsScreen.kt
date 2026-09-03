package io.joinasr.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.formatMinutes
import io.joinasr.app.limits.DailyLimit
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrStepper
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 07 — Setup / Set Daily Limits (node 60:2).
 *
 * One card per app the person chose on screen 06, each with a minus, the
 * limit, and a plus. The values move along [DailyLimit]'s ladder rather than
 * by a fixed step, so pressing plus and then minus always returns to where
 * it started — see the comment there for why that is not automatic.
 *
 * The note at the top is a promise the app has to keep: limits lock when the
 * challenge starts. It is stated here, before anything is committed, because
 * this is the last screen where changing them is free.
 */
@Composable
fun DailyLimitsScreen(
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onContinue: (Map<String, Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed by package name, seeded once. A recomposition must not reset a
    // limit somebody has just adjusted, and a person adding an app on the
    // previous screen and coming back should find their other limits intact.
    val limits = remember(apps) {
        mutableStateMapOf<String, Int>().apply {
            putAll(DailyLimit.defaultsFor(apps.map { it.packageName }))
        }
    }

    val context = LocalContext.current
    val icons by produceState(initialValue = emptyMap<String, ImageBitmap>(), apps) {
        val loaded = mutableMapOf<String, ImageBitmap>()
        for (entry in apps) {
            InstalledApps.icon(context, entry.packageName)?.let {
                loaded[entry.packageName] = it
                value = loaded.toMap()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(AsrColors.Background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(22.dp))
                    AsrBackChevron(onBack)

                    Spacer(Modifier.height(22.dp))
                    Text("SETUP 4 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Set your daily limits.",
                        style = AsrType.display(34),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Choose how much time each selected app gets per day.",
                        style = AsrType.Field,
                        color = AsrColors.TextSecondary,
                    )

                    Spacer(Modifier.height(20.dp))
                    LockNote()
                    Spacer(Modifier.height(10.dp))
                }
            }

            items(apps, key = { it.packageName }) { entry ->
                LimitCard(
                    entry = entry,
                    icon = icons[entry.packageName],
                    minutes = limits[entry.packageName] ?: DailyLimit.DEFAULT_MINUTES,
                    onChange = { limits[entry.packageName] = it },
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Adjust each limit before you start.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            AsrPrimaryButton(
                text = "Continue",
                onClick = { onContinue(limits.toMap()) },
                // Never disabled: every app already has a limit, so there is
                // nothing the person still has to do here.
                enabled = apps.isNotEmpty(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LockNote() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", style = AsrType.Button.copy(fontSize = 15.sp), color = AsrColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(
            "Limits lock when your challenge starts.",
            style = AsrType.Label,
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun LimitCard(
    entry: AppEntry,
    icon: ImageBitmap?,
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LimitIcon(entry, icon)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = AsrType.CardTitle.copy(fontSize = 16.sp),
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "DAILY LIMIT",
                    style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                    color = AsrColors.TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AsrStepper(
                value = formatMinutes(minutes),
                label = "${entry.label} daily limit",
                canDecrease = DailyLimit.canDecrease(minutes),
                canIncrease = DailyLimit.canIncrease(minutes),
                onDecrease = { onChange(DailyLimit.decreased(minutes)) },
                onIncrease = { onChange(DailyLimit.increased(minutes)) },
            )
        }
    }
}

@Composable
private fun LimitIcon(entry: AppEntry, icon: ImageBitmap?) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(AsrColors.Background)
            .border(1.dp, AsrColors.FieldBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null, // The name is right beside it.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                entry.label.take(1).uppercase(),
                style = AsrType.Button,
                color = AsrColors.Accent,
            )
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DailyLimitsPreview() {
    AsrTheme {
        DailyLimitsScreen(
            apps = listOf(
                AppEntry("com.instagram.android", "Instagram"),
                AppEntry("com.google.android.youtube", "YouTube"),
                AppEntry("com.zhiliaoapp.musically", "TikTok"),
            ),
            onBack = {},
            onContinue = {},
        )
    }
}
