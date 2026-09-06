package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrTextField
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 31 — Account / Delete Account (node 112:15).
 *
 * The password is not a formality. The server checks it by attempting a real
 * sign-in, so an unlocked phone in somebody else's hand cannot delete an
 * account by tapping through — which on an app whose whole point is being
 * hard to escape is the difference between a safeguard and a loophole.
 *
 * The button is the only red thing in this app, and stays refusable: the
 * cancel line under it is the same size as the button's own text.
 */
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onDelete: (password: String) -> Unit,
    busy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(26.dp))
        Text("ACCOUNT", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Delete account.", style = AsrType.display(30), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "This permanently deletes your account and associated app data.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.DangerMuted, RoundedCornerShape(18.dp))
                .border(1.dp, AsrColors.Danger, RoundedCornerShape(18.dp))
                .padding(15.dp),
        ) {
            Text(
                "This cannot be undone",
                style = AsrType.CardTitle.copy(fontSize = 17.sp),
                color = AsrColors.Danger,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your profile, challenge history, witness relationships, synced usage " +
                    "data and earned-time history will be removed. Data that must be " +
                    "retained for legal, security or fraud-prevention reasons is handled " +
                    "under the Privacy Policy.",
                style = AsrType.Legal.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(24.dp))
        AsrTextField(
            label = "Confirm your password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Enter your password",
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .padding(15.dp),
        ) {
            Text(
                "What happens next",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We'll start the deletion request and sign you out. Signing in again " +
                    "within seven days cancels it. You'll be told if any data must be " +
                    "retained for a limited legal or security reason.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        errorMessage?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(26.dp))
        val enabled = password.isNotBlank() && !busy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(if (enabled) AsrColors.DangerMuted else AsrColors.Surface)
                .border(
                    1.dp,
                    if (enabled) AsrColors.Danger else AsrColors.FieldBorder,
                    RoundedCornerShape(27.dp),
                )
                .clickable(enabled = enabled, role = Role.Button) { onDelete(password) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) "Deleting…" else "Delete my account",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = if (enabled) AsrColors.Danger else AsrColors.TextTertiary,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Cancel and keep my account",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DeleteAccountPreview() {
    AsrTheme {
        DeleteAccountScreen(onBack = {}, onDelete = {}, busy = false, errorMessage = null)
    }
}
