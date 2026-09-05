package io.joinasr.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.enforcement.OemSettings
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrCard
import io.joinasr.app.ui.components.AsrPill
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Keeping the protection service alive on phones that kill it.
 *
 * Not in the Figma file. It exists because a foreground service is a promise
 * Android keeps and several manufacturers do not: Xiaomi, Oppo, Vivo, Huawei
 * and Samsung all ship a second battery layer that stops the service when
 * the screen goes off, and from then on nothing is blocked while the
 * dashboard says LOCKED. A day later the witnesses are told the phone went
 * dark. Both are worse than asking for one more setting.
 *
 * Two rows. Android's own battery optimisation, read live and opened from
 * here; and the manufacturer's screen where this phone has one, with the
 * exact words to look for once there, because those screens are different
 * on every brand and none of them says "Asr needs this".
 */
@Composable
fun BackgroundActivityScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = Permissions.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose {}
    }
    val guide = remember { OemSettings.guideFor() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(22.dp))
        Text("KEEP IT RUNNING", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Let Asr stay awake.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your limits are enforced by a small service that has to keep running. " +
                "Phones stop background apps to save battery, and if this one is stopped, " +
                "nothing is blocked until you open Asr again.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        SettingRow(
            title = "Battery optimisation",
            detail = if (unrestricted) "Asr is not restricted" else "Choose Don't optimise, or Unrestricted, for Asr",
            done = unrestricted,
            actionLabel = "OPEN",
            onAction = {
                val opened = runCatching {
                    context.startActivity(Permissions.batteryOptimizationIntent())
                }.isSuccess
                if (!opened) context.openAppDetails()
            },
        )

        if (guide != null) {
            Spacer(Modifier.height(12.dp))
            SettingRow(
                title = "${guide.brand} background settings",
                detail = guide.steps,
                done = null,
                actionLabel = "OPEN",
                onAction = {
                    if (!OemSettings.open(context, guide)) context.openAppDetails()
                },
            )
        }

        Spacer(Modifier.height(18.dp))
        AsrCard(radius = 16.dp, padding = 15.dp) {
            Text(
                "Asr uses almost no battery: it only looks while the screen is on, and " +
                    "sleeps when it is off. These settings stop the phone from switching " +
                    "it off in the middle of your challenge.",
                style = AsrType.Legal,
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(text = "Done", onClick = onDone)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    /** True when done, false when not, null when this app cannot read it. */
    done: Boolean?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    AsrCard(background = AsrColors.Field) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AsrType.RowTitle, color = AsrColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(detail, style = AsrType.Label, color = AsrColors.TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            if (done == true) {
                AsrPill("ON")
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(2.dp),
                ) {
                    AsrPill(actionLabel)
                }
            }
        }
    }
}

/** The app's own page, where Battery lives on every phone under some name. */
private fun Context.openAppDetails() {
    val opened = runCatching { startActivity(Permissions.appDetailsIntent(this)) }.isSuccess
    if (!opened) {
        Toast.makeText(
            this,
            "Open Settings, find Asr under Apps, and set Battery to Unrestricted.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun BackgroundActivityPreview() {
    AsrTheme { BackgroundActivityScreen(onBack = {}, onDone = {}) }
}
