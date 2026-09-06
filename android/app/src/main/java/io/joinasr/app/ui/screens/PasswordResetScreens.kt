package io.joinasr.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
 * Figma 33 — Auth / Forgot Password (node 160:2).
 *
 * Moves on to the "check your email" screen whether or not the address has
 * an account, because the server answers the same way for both. Telling
 * somebody which addresses are registered is how account lists get
 * harvested, and a screen that helpfully said "no account with that email"
 * would undo the server's care.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onSend: (email: String) -> Unit,
    onBackToLogIn: () -> Unit,
    busy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    initialEmail: String = "",
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(34.dp))
        Text("PASSWORD RESET", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            "Forgot your\npassword?",
            style = AsrType.display(38),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Enter the email linked to your account. We'll send you a secure reset link.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(28.dp))
        AsrTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it.trim() },
            placeholder = "you@example.com",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
        )

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = if (busy) "Sending…" else "Send reset link",
            onClick = { onSend(email) },
            enabled = email.contains("@") && email.length > 3 && !busy,
        )

        Spacer(Modifier.height(22.dp))
        BackToLogIn(onBackToLogIn)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Figma 34 — Auth / Check Email (node 160:13).
 *
 * The reset link opens on the web, which is where the token is answered.
 * Coming back to the app afterwards and signing in with the new password is
 * the whole of this screen's job.
 */
@Composable
fun CheckEmailScreen(
    email: String,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onBackToLogIn: () -> Unit,
    busy: Boolean,
    notice: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth()) { AsrBackChevron(onBack) }

        Spacer(Modifier.height(50.dp))
        Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = AsrColors.Accent,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Text("✉", style = AsrType.display(30), color = AsrColors.Accent)
        }

        Spacer(Modifier.height(28.dp))
        Text("CHECK YOUR EMAIL", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            "Reset link sent.",
            style = AsrType.display(34),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "We sent a password reset link to\n$email",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(18.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(18.dp))
                .padding(17.dp),
        ) {
            Text(
                "Didn't get it?",
                style = AsrType.CardTitle.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Check spam, make sure the address is right, or resend after a moment.",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(AsrColors.SurfaceSunken)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(29.dp))
                .clickable(enabled = !busy, onClick = onResend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) "Sending…" else "Resend email",
                style = AsrType.Button,
                color = AsrColors.TextPrimary,
            )
        }

        notice?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Accent, textAlign = TextAlign.Center)
        }
        errorMessage?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(22.dp))
        BackToLogIn(onBackToLogIn)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Figma 35 — Auth / Reset Password (node 160:26).
 *
 * Opened from the link in the email, which carries the token. The screen is
 * useless without one, so it is only ever reached with a token in hand — see
 * the intent filter in the manifest.
 */
@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onSubmit: (password: String) -> Unit,
    busy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val longEnough = password.length >= MIN_PASSWORD
    val matches = password == confirm
    val ready = longEnough && matches && !busy

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(34.dp))
        Text("NEW PASSWORD", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            "Create a new\npassword.",
            style = AsrType.display(38),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Choose a password you haven't used here before.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        AsrTextField(
            label = "New password",
            value = password,
            onValueChange = { password = it },
            placeholder = "At least $MIN_PASSWORD characters",
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(16.dp))
        AsrTextField(
            label = "Confirm password",
            value = confirm,
            onValueChange = { confirm = it },
            placeholder = "Re-enter password",
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        if (confirm.isNotEmpty() && !matches) {
            Spacer(Modifier.height(8.dp))
            Text("Those two do not match.", style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.AccentMuted, RoundedCornerShape(16.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp, vertical = 20.dp),
        ) {
            Text(
                "${if (longEnough) "✓" else "·"}  $MIN_PASSWORD+ characters  ·  " +
                    "Use a unique password",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = if (longEnough) AsrColors.Accent else AsrColors.TextSecondary,
            )
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = if (busy) "Updating…" else "Update password",
            onClick = { onSubmit(password) },
            enabled = ready,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            "This reset link expires and can only be used once.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BackToLogIn(onClick: () -> Unit) {
    Text(
        "Back to log in",
        style = AsrType.Field.copy(fontSize = 13.sp),
        color = AsrColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ForgotPreview() {
    AsrTheme {
        ForgotPasswordScreen(
            onBack = {},
            onSend = {},
            onBackToLogIn = {},
            busy = false,
            errorMessage = null,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun CheckEmailPreview() {
    AsrTheme {
        CheckEmailScreen(
            email = "ariyan@example.com",
            onBack = {},
            onResend = {},
            onBackToLogIn = {},
            busy = false,
            notice = null,
            errorMessage = null,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ResetPreview() {
    AsrTheme {
        ResetPasswordScreen(onBack = {}, onSubmit = {}, busy = false, errorMessage = null)
    }
}
