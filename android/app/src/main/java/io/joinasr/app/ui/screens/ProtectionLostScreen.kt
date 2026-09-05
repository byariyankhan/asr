package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.permissions.Permissions
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 27 — Protection / Lost (node 154:2).
 *
 * Reached from the dashboard when a required grant has gone while a
 * challenge is running. Nothing on it is decorative: every pill is read from
 * the system on each resume, so returning from Settings with the permission
 * still off shows OFF rather than a screen that congratulates the person for
 * pressing a button.
 *
 * The frame names Accessibility as the missing grant. This build blocks with
 * a full-screen activity launched from the background, which Android permits
 * only with "display over other apps" — so that is the permission the button
 * opens, and the wording says what this app actually needs. Sending somebody
 * to a screen that would not fix anything is worse than a design mismatch.
 */
@Composable
fun ProtectionLostScreen(
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether there is anything behind this screen to go back to.
     *
     * False while a challenge is running and a required grant is off. Then
     * this is not a warning about the dashboard, it is instead of it: a
     * challenge nothing can enforce must not be shown as a challenge being
     * kept, and a way past this screen would be a way to keep it in name
     * only.
     */
    dismissible: Boolean = true,
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = PermissionState.read(context)
        onPauseOrDispose {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        // No chevron when this is the screen instead of the dashboard: a
        // back arrow that cannot go back is worse than no back arrow.
        if (dismissible) AsrBackChevron(onBack) else Spacer(Modifier.height(28.dp))

        Spacer(Modifier.height(28.dp))
        Text(
            if (dismissible) "PROTECTION LOST" else "PROTECTION REQUIRED",
            style = AsrType.Eyebrow,
            color = AsrColors.Breach,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (dismissible) "Your challenge is exposed." else "Turn protection on to continue.",
            style = AsrType.display(30),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (dismissible) {
                "A required protection is off. Controlled apps cannot be reliably " +
                    "blocked until access is restored."
            } else {
                // Said plainly, because the two hours are real and start now.
                "Your challenge is running and nothing on this phone can enforce " +
                    "it. These permissions are granted per phone, so a new one -- " +
                    "or a reinstall -- starts without them. Your witnesses are told " +
                    "if this is still off in two hours."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Alert(
            title = if (!permissions.overlay) "App blocking is off" else "Usage tracking is off",
            body = if (!permissions.overlay) {
                "Restore \"Display over other apps\" to protect your active challenge."
            } else {
                "Restore usage access. Without it nothing can be measured, so nothing can be blocked."
            },
        )

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("Protection status", style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(14.dp))
            StatusRow("Usage access", "App usage tracking", permissions.usageAccess)
            Spacer(Modifier.height(16.dp))
            StatusRow("App blocking", "Display over other apps", permissions.overlay)
            Spacer(Modifier.height(16.dp))
            StatusRow("Notifications", "Protection alerts", permissions.notifications)
        }

        Spacer(Modifier.height(18.dp))
        Recorded()

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = "Restore protection",
            onClick = {
                val intent = if (!permissions.usageAccess) {
                    Permissions.usageAccessIntent()
                } else {
                    Permissions.overlayIntent(context)
                }
                runCatching { context.startActivity(intent) }
            },
            enabled = !permissions.requiredGranted,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (permissions.requiredGranted) {
                "Protection is back on. Your challenge is being enforced again."
            } else {
                "Android Settings will open. Turn the permission back on, then return."
            },
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = if (permissions.requiredGranted) AsrColors.Accent else AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (dismissible) {
            Spacer(Modifier.height(20.dp))
            BackToDashboard(onDismiss)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BackToDashboard(onDismiss: () -> Unit) {
    Text(
        "Back to dashboard",
        style = AsrType.Label.copy(fontSize = 13.sp),
        color = AsrColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun Alert(title: String, body: String) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.BreachMuted, shape)
            .border(1.dp, AsrColors.BreachBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "!",
            style = AsrType.display(26),
            color = AsrColors.Breach,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(7.dp))
            Text(
                body,
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun StatusRow(title: String, subtitle: String, on: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(72.dp)
                .clip(CircleShape)
                .background(if (on) AsrColors.AccentMuted else AsrColors.BreachMuted)
                .border(
                    1.dp,
                    if (on) AsrColors.AccentBorder else AsrColors.BreachBorder,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (on) "ON" else "OFF",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = if (on) AsrColors.Accent else AsrColors.Breach,
            )
        }
    }
}

@Composable
private fun Recorded() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "✓",
            style = AsrType.display(22),
            color = AsrColors.Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Protection loss recorded",
                style = AsrType.Field.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your challenge stays active, but this interruption is logged until " +
                    "protection is restored.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ProtectionLostPreview() {
    AsrTheme {
        ProtectionLostScreen(onBack = {}, onDismiss = {})
    }
}
