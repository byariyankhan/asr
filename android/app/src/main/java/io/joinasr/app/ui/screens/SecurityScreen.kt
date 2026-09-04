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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrTextField
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/** The shortest password the server will take. */
private const val MIN_PASSWORD = 8

/**
 * Figma 30 — Account / Email & Password (node 160:42).
 *
 * Changing the email address is drawn and not offered. Better Auth's
 * change-email endpoint is switched off in this deployment, so a row that
 * opened a form would end in a 404 the person would read as a bug in the
 * app. It says what it is instead.
 *
 * Changing the password is offered, and expands in place. The design has a
 * row with a chevron and no frame behind it; a whole screen to collect two
 * fields would be a screen the design never drew.
 */
@Composable
fun SecurityScreen(
    email: String,
    emailVerified: Boolean,
    onBack: () -> Unit,
    onChangePassword: (current: String, next: String) -> Unit,
    onSignOutOtherSessions: () -> Unit,
    busy: Boolean,
    errorMessage: String?,
    notice: String?,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }

    val ready = current.isNotBlank() && next.length >= MIN_PASSWORD && !busy

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
        Text("SECURITY", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Email & password", style = AsrType.display(32), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Manage how you sign in to your account.",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        CurrentEmail(email = email, verified = emailVerified)

        Spacer(Modifier.height(26.dp))
        Text("Account security", style = AsrType.display(20), color = AsrColors.TextPrimary)

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(AsrColors.SurfaceSunken, RoundedCornerShape(16.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Change email",
                    style = AsrType.Field.copy(fontSize = 15.sp),
                    color = AsrColors.TextTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Not available yet. Contact support to change it.",
                    style = AsrType.Label.copy(fontSize = 12.sp),
                    color = AsrColors.TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AsrColors.SurfaceSunken)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .then(
                    if (open) Modifier else Modifier.clickable(role = Role.Button) { open = true },
                )
                .padding(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Change password",
                        style = AsrType.Field.copy(fontSize = 15.sp),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Enter your current password first.",
                        style = AsrType.Label.copy(fontSize = 12.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
                if (!open) Text("›", style = AsrType.display(22), color = AsrColors.TextSecondary)
            }

            if (open) {
                Spacer(Modifier.height(16.dp))
                AsrTextField(
                    label = "Current password",
                    value = current,
                    onValueChange = { current = it },
                    placeholder = "Your password",
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                AsrTextField(
                    label = "New password",
                    value = next,
                    onValueChange = { next = it },
                    placeholder = "At least $MIN_PASSWORD characters",
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(14.dp))
                AsrPrimaryButton(
                    text = if (busy) "Saving…" else "Update password",
                    onClick = {
                        onChangePassword(current, next)
                        current = ""
                        next = ""
                    },
                    enabled = ready,
                )
            }
        }

        notice?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Accent)
        }
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.AccentMuted, RoundedCornerShape(18.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(18.dp))
                .padding(17.dp),
        ) {
            Text(
                "Keep your account recoverable",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use an email you can access. Password reset links are sent there.",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(AsrColors.SurfaceSunken)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(29.dp))
                .clickable(enabled = !busy, role = Role.Button, onClick = onSignOutOtherSessions),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Sign out of other sessions",
                style = AsrType.Button,
                color = AsrColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Use this if you signed in on a device you no longer control.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CurrentEmail(email: String, verified: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CURRENT EMAIL",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = AsrColors.TextTertiary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                email,
                style = AsrType.Field.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (verified) "VERIFIED" else "UNVERIFIED",
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = if (verified) AsrColors.Accent else AsrColors.TextTertiary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun SecurityPreview() {
    AsrTheme {
        SecurityScreen(
            email = "ariyan@example.com",
            emailVerified = true,
            onBack = {},
            onChangePassword = { _, _ -> },
            onSignOutOtherSessions = {},
            busy = false,
            errorMessage = null,
            notice = null,
        )
    }
}
