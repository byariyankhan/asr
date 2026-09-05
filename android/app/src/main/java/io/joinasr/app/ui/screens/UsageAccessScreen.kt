package io.joinasr.app.ui.screens

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrCard
import io.joinasr.app.ui.components.AsrPill
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 05 — Setup / Usage Access (node 119:2).
 *
 * The screen exists to earn a grant, so most of it is an explanation of
 * exactly what the permission gives us and what it does not. That is not
 * decoration: Play's usage-access policy requires a disclosure, and a person
 * being asked for a permission this broad deserves the same sentence the
 * policy does.
 */
@Composable
fun UsageAccessScreen(
    onBack: () -> Unit,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    // Set once Settings has been opened, so the note below appears only for
    // somebody who has actually been there and come back without the grant.
    var triedSettings by remember { mutableStateOf(false) }

    // Nothing tells the app when a Settings toggle flips, so the state is
    // re-read every time this screen comes back to the front. Without it the
    // person grants access, returns, and is still looking at "NOT ENABLED".
    LifecycleResumeEffect(Unit) {
        granted = Permissions.hasUsageAccess(context)
        if (granted) onGranted()
        onPauseOrDispose {}
    }

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
        Text("SETUP 2 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Measure app time.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Android Usage Access lets us track how long selected apps are used.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        AsrCard(radius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Usage Access",
                    style = AsrType.CardTitle,
                    color = AsrColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                AsrPill("REQUIRED")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "We use only the minimum data needed to enforce your daily limits.",
                style = AsrType.Label,
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            GrantedLine("App usage time", "Minutes used by each selected app")
            Spacer(Modifier.height(12.dp))
            GrantedLine("Foreground app", "Which selected app is currently in use")
            Spacer(Modifier.height(12.dp))
            GrantedLine("Usage events", "When an app starts or leaves the foreground")
        }

        Spacer(Modifier.height(20.dp))
        AsrCard(background = AsrColors.Field) {
            Text(
                "We do not read app content.",
                style = AsrType.Field,
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No messages, passwords, photos, searches or typed text. Usage data is " +
                    "used for limits, progress and challenge records.",
                style = AsrType.Legal,
                color = AsrColors.TextSecondary,
            )
        }

        // What leaves the phone, said here rather than only in the privacy
        // policy. Play's prominent-disclosure rule wants the sending named
        // where the permission is asked for, and a person deciding whether
        // to grant it deserves the same sentence.
        Spacer(Modifier.height(12.dp))
        AsrCard(background = AsrColors.Field) {
            Text(
                "What leaves your phone",
                style = AsrType.Field,
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Daily totals for the apps you choose to limit, and the moment one of " +
                    "them reaches its limit, are sent to Asr's server so the witnesses you " +
                    "name can see how your challenge is going. Nothing about any other app " +
                    "ever leaves your phone.",
                style = AsrType.Legal,
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(20.dp))
        AsrPill(if (granted) "ENABLED" else "NOT ENABLED", highlighted = granted)

        // Android blocks this setting outright for an app installed from an
        // APK rather than from a store, and says only "App was denied access"
        // with no route forward. There is a route, it is three taps away, and
        // nothing on the phone tells you where -- so this does, but only
        // after somebody has been to Settings and come back without it, so
        // that everybody else is not read an instruction they do not need.
        if (triedSettings && !granted) {
            Spacer(Modifier.height(16.dp))
            AsrCard(background = AsrColors.Field) {
                Text(
                    "If Settings said \"App was denied access\"",
                    style = AsrType.Field,
                    color = AsrColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android restricts this setting for apps installed from a file " +
                        "instead of from a store. To allow it: Settings, Apps, Asr, " +
                        "then the three-dot menu at the top right, then \"Allow " +
                        "restricted settings\". After that, come back here and try " +
                        "again.",
                    style = AsrType.Legal,
                    color = AsrColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        AsrPrimaryButton(
            text = if (granted) "Continue" else "Open Usage Access",
            onClick = {
                if (granted) {
                    onGranted()
                } else {
                    try {
                        context.startActivity(Permissions.usageAccessIntent())
                        triedSettings = true
                    } catch (e: ActivityNotFoundException) {
                        // Some manufacturers ship without this Settings screen.
                        // Saying so beats a crash and beats a button that does
                        // nothing at all.
                        Toast.makeText(
                            context,
                            "This phone has no Usage Access screen. Look for it under " +
                                "Settings, Special app access.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Android Settings will open. Enable access for this app, then return.",
            style = AsrType.Legal,
            color = AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
    }
}

/** One thing the permission grants, ticked. */
@Composable
private fun GrantedLine(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("✓", style = AsrType.Button, color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = AsrType.Label, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = AsrType.Legal, color = AsrColors.TextSecondary)
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun UsageAccessPreview() {
    AsrTheme { UsageAccessScreen(onBack = {}, onGranted = {}) }
}
