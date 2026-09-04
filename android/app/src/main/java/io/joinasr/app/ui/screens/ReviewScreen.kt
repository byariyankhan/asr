package io.joinasr.app.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.limits.DailyLimit
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrAppIcon
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Witness

/**
 * Figma 11 — Review / Start Challenge (node 124:2).
 *
 * The last screen before anything is committed, and the only one on which
 * everything the person chose is visible at once. That is its whole job: a
 * commitment nobody was shown in full is not one they agreed to.
 *
 * The button is the moment the pact is written. Nothing before this screen
 * touches storage.
 */
@Composable
fun ReviewScreen(
    days: Int,
    apps: List<AppEntry>,
    limits: Map<String, Int>,
    witnesses: List<Witness>,
    protectionReady: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(24.dp))
        Text("FINAL CHECK", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Ready to commit?", style = AsrType.display(36), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Review your rules before the challenge starts.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        SummaryCard(days = days, apps = apps.size, witnesses = witnesses.size)

        Spacer(Modifier.height(26.dp))
        Text("Daily limits", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        for (app in apps) {
            LimitRow(
                packageName = app.packageName,
                label = app.label,
                minutes = limits[app.packageName] ?: DailyLimit.DEFAULT_MINUTES,
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        WitnessReview(witnesses)

        Spacer(Modifier.height(12.dp))
        ProtectionReview(ready = protectionReady)

        Spacer(Modifier.height(16.dp))
        LockNotice()

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = "Start $days-day challenge",
            onClick = onStart,
            // Never blocked on protection. Somebody who has come this far
            // and left a permission off should be able to commit and fix it
            // afterwards; the dashboard says loudly when it is not on, and a
            // challenge that cannot be started is a person who gives up.
            enabled = apps.isNotEmpty(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SummaryCard(days: Int, apps: Int, witnesses: Int) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(days.toString(), style = AsrType.display(34), color = AsrColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(
                "DAYS",
                style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                color = AsrColors.Accent,
                modifier = Modifier.weight(1f),
            )
            Pill("READY")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Starts immediately",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${plural(apps, "app")}  ·  ${plural(witnesses, "witness", "witnesses")}",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun LimitRow(packageName: String, label: String, minutes: Int) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsrAppIcon(packageName = packageName, label = label, size = 32.dp, corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$minutes min / day",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun WitnessReview(witnesses: List<Witness>) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Witnesses", style = AsrType.Field.copy(fontSize = 15.sp), color = AsrColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (witnesses.isEmpty()) {
                    "Nobody invited"
                } else {
                    witnesses.joinToString(" · ") { it.label }
                },
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Pill("${witnesses.size} SET", highlighted = witnesses.isNotEmpty())
    }
}

@Composable
private fun ProtectionReview(ready: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (ready) "Protection ready" else "Protection not ready",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (ready) {
                    "Usage access + app blocking enabled"
                } else {
                    "Turn both on and nothing here will be enforced"
                },
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (ready) "✓" else "!",
            style = AsrType.display(20),
            color = if (ready) AsrColors.Accent else AsrColors.Error,
        )
    }
}

@Composable
private fun LockNotice() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.AccentMuted, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✓", style = AsrType.display(18), color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "These rules lock when you start.",
                style = AsrType.Field.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Duration, apps, limits and witnesses cannot be made easier until the " +
                    "challenge ends.",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Pill(text: String, highlighted: Boolean = true) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(
                if (highlighted) AsrColors.AccentMuted else AsrColors.Background,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = if (highlighted) AsrColors.Accent else AsrColors.TextSecondary,
        )
    }
}

private fun plural(count: Int, one: String, many: String = "${one}s") =
    if (count == 1) "1 $one" else "$count $many"

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ReviewPreview() {
    AsrTheme {
        ReviewScreen(
            days = 14,
            apps = listOf(
                AppEntry("com.instagram.android", "Instagram"),
                AppEntry("com.google.android.youtube", "YouTube"),
            ),
            limits = mapOf("com.instagram.android" to 15, "com.google.android.youtube" to 30),
            witnesses = listOf(Witness("1", "mother", 0), Witness("2", "brother", 0)),
            protectionReady = true,
            onBack = {},
            onStart = {},
        )
    }
}
