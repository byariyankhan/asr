package io.joinasr.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrCard
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrTextLink
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 10 — Permission / App Blocking Disclosure (node 119:55), rewritten
 * for the mechanism this app actually ships.
 *
 * The frame describes Android Accessibility. This app blocks with
 * UsageStatsManager plus a "display over other apps" overlay instead, a
 * decision recorded in docs/ANDROID.md: Play's accessibility policy requires
 * that API to serve users with disabilities, and screen-time apps using it to
 * block get removed. A rejected app helps nobody, and the cost is that the
 * block screen appears about a second after the app opens rather than
 * instantly.
 *
 * The disclosure itself stays, and is the point of the screen. Overlay does
 * not oblige one the way accessibility does, but a person about to let an app
 * draw over everything else should be told what is read and what is not
 * before they are sent to Settings, not after.
 */
@Composable
fun BlockingDisclosureScreen(
    onBack: () -> Unit,
    onGranted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }

    LifecycleResumeEffect(Unit) {
        granted = Permissions.canDrawOverlays(context)
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

        Spacer(Modifier.height(26.dp))
        Text("DISPLAY OVER OTHER APPS", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Enable app blocking.", style = AsrType.display(34), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Required to stop a selected app when its daily limit is reached.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        AsrCard(radius = 20.dp) {
            Text("How this access is used", style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Asr checks which app is in the foreground and, once that app has used " +
                    "up its daily limit, draws the block screen on top of it.",
                style = AsrType.Label,
                color = AsrColors.TextSecondary,
            )

            Spacer(Modifier.height(18.dp))
            DisclosureBlock(
                heading = "ACCESSED",
                body = "The package name of the app in the foreground, and how many " +
                    "minutes each app you selected has been used today.",
            )
            Spacer(Modifier.height(14.dp))
            DisclosureBlock(
                heading = "NOT ACCESSED",
                body = "Messages, passwords, typed text, photos, or anything shown " +
                    "inside another app. Asr cannot read what is on your screen.",
            )
            Spacer(Modifier.height(14.dp))
            DisclosureBlock(
                heading = "WITNESS SHARING",
                body = "If a pact is broken, the app name and the fact that it broke " +
                    "may be sent to the witnesses you chose.",
            )
        }

        Spacer(Modifier.height(18.dp))
        AsrCard(background = AsrColors.Field) {
            Text(
                "This data is never sold and is never used for advertising.",
                style = AsrType.Legal,
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(
            text = if (granted) "Continue" else "I understand · Open settings",
            onClick = {
                if (granted) {
                    onGranted()
                } else {
                    runCatching { context.startActivity(Permissions.overlayIntent(context)) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                "This phone does not have that Settings screen. Look under " +
                                    "Settings, Special app access, Display over other apps.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        AsrTextLink(
            text = "Not now",
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(32.dp))
    }
}

/** One labelled paragraph of the disclosure. */
@Composable
private fun DisclosureBlock(heading: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(heading, style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(6.dp))
        Text(body, style = AsrType.Legal, color = AsrColors.TextPrimary)
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun BlockingDisclosurePreview() {
    AsrTheme { BlockingDisclosureScreen(onBack = {}, onGranted = {}, onSkip = {}) }
}
