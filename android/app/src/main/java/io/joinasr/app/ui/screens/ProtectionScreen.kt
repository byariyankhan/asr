package io.joinasr.app.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrCard
import io.joinasr.app.ui.components.AsrPill
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 09 — Setup / Protection Access (node 119:29).
 *
 * Three rows, each showing what it is for and its live state. Two are
 * required and one is not, and the screen says which is which rather than
 * presenting them as one wall: losing notifications costs the witness
 * updates, losing either of the others means no limits at all.
 *
 * Every state here is read from the system, never remembered. A person can
 * revoke any of these in Settings at any moment, and a screen showing a
 * cached "ON" for something switched off is how an app ends up promising
 * protection it is not providing.
 */
@Composable
fun ProtectionScreen(
    onBack: () -> Unit,
    onReviewBlocking: () -> Unit,
    /** Battery optimisation and the manufacturer's own switches. */
    onBackgroundActivity: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionState.read(context)) }

    LifecycleResumeEffect(Unit) {
        state = PermissionState.read(context)
        onPauseOrDispose {}
    }

    val askForNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state = PermissionState.read(context) }

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
        Text("SETUP 6 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Enable protection.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Two permissions power the challenge. The other two keep it running.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        AsrCard {
            Text(
                "2 required  ·  2 recommended",
                style = AsrType.Label,
                color = AsrColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(18.dp))
        PermissionRow(
            title = "Usage access",
            detail = "Tracks selected-app time",
            granted = state.usageAccess,
            actionLabel = "ENABLE",
            onAction = {
                runCatching { context.startActivity(Permissions.usageAccessIntent()) }
                    .onFailure { context.toastNoSettingsScreen() }
            },
        )

        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "App blocking",
            detail = "Detects selected apps and shows the block screen",
            granted = state.overlay,
            actionLabel = "REVIEW",
            onAction = onReviewBlocking,
        )

        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Notifications",
            detail = "Protection, invite and witness updates",
            granted = state.notifications,
            actionLabel = "ALLOW",
            onAction = {
                // Below Android 13 there is no dialog to show, and after a
                // refusal the dialog never appears again -- both cases have to
                // go to the app's notification settings or the button is a
                // no-op the second time it is pressed.
                if (Permissions.notificationsAreRequestable && !state.notifications) {
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching {
                        context.startActivity(Permissions.appNotificationSettingsIntent(context))
                    }.onFailure { context.toastNoSettingsScreen() }
                }
            },
        )

        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Background activity",
            detail = "Stops the phone from switching protection off to save battery",
            granted = state.batteryUnrestricted,
            actionLabel = "SET UP",
            onAction = onBackgroundActivity,
        )

        Spacer(Modifier.height(18.dp))
        AsrCard(radius = 16.dp, padding = 15.dp) {
            Text(
                "App blocking is required. Notifications can be changed later without " +
                    "disabling your core limits.",
                style = AsrType.Legal,
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = if (state.requiredGranted) "Continue" else "Enable required access",
            onClick = onContinue,
            // Disabled until both required grants are real. The design draws
            // it that way, and it is the honest state: continuing without them
            // reaches a dashboard that cannot enforce anything.
            enabled = state.requiredGranted,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
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
            if (granted) {
                AsrPill("ON")
            } else {
                // A pill that does something, so it is a real target rather
                // than a label that happens to be tappable.
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

private fun android.content.Context.toastNoSettingsScreen() {
    Toast.makeText(
        this,
        "This phone does not have that Settings screen. Look under Settings, " +
            "Special app access.",
        Toast.LENGTH_LONG,
    ).show()
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ProtectionPreview() {
    AsrTheme {
        ProtectionScreen(onBack = {}, onReviewBlocking = {}, onBackgroundActivity = {}, onContinue = {})
    }
}
