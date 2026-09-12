package com.joinasr.app.ui.screens

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
import com.joinasr.app.ui.components.AsrBackChevron
import com.joinasr.app.ui.components.AsrCodeField
import com.joinasr.app.ui.components.AsrPrimaryButton
import com.joinasr.app.ui.components.AsrTextField
import com.joinasr.app.ui.theme.AsrColors
import com.joinasr.app.ui.theme.AsrTheme
import com.joinasr.app.ui.theme.AsrType

/** The shortest password the server will take. */
private const val MIN_PASSWORD = 8

/**
 * How many digits are in a reset code.
 *
 * The server decides this (`backend/src/lib/password.ts`) and generates the
 * code from it; this is the same number written again because Kotlin cannot
 * read TypeScript. `ResetCodeTest` fails if the two ever disagree.
 */
const val RESET_CODE_LENGTH = 7

/**
 * Figma 33 — Auth / Forgot Password (node 160:2).
 *
 * Moves on to the code screen whether or not the address has an account,
 * because the server answers the same way for both. Telling somebody which
 * addresses are registered is how account lists get harvested, and a screen
 * that helpfully said "no account with that email" would undo the server's
 * care.
 *
 * The drawn screen says a link is coming. It is a code: the reset is
 * finished inside the app that asked for it rather than in a browser, and
 * the deviation is listed in `docs/FIGMA_SCREENS.md`.
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
            "Enter the email linked to your account. We'll send you a $RESET_CODE_LENGTH-digit code.",
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
            text = if (busy) "Sending…" else "Send code",
            onClick = { onSend(email) },
            enabled = email.contains("@") && email.length > 3 && !busy,
        )

        Spacer(Modifier.height(22.dp))
        BackToLogIn(onBackToLogIn)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Figma 34 — Auth / Check Email (node 160:13), which drew a link.
 *
 * It takes the code instead. The drawn screen only announced that a link
 * had gone out and then had nothing to do, because the reset happened in a
 * browser; with a code this is where the reset actually continues, so the
 * boxes and a Continue sit under the same announcement. Everything else on
 * the screen is the drawing: the ring, the eyebrow, the "Didn't get it?"
 * card, the resend, the way back to log in.
 *
 * Continue only hands the code on. Nothing has checked it yet -- the server
 * sees it for the first time with the new password, on the screen after
 * this one -- so this screen never says a code is wrong, because it does
 * not know.
 */
@Composable
fun CheckEmailScreen(
    email: String,
    onBack: () -> Unit,
    onContinue: (code: String) -> Unit,
    onResend: () -> Unit,
    onBackToLogIn: () -> Unit,
    busy: Boolean,
    notice: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }

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
            "Code sent.",
            style = AsrType.display(34),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "We sent a $RESET_CODE_LENGTH-digit code to\n$email",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(26.dp))
        AsrCodeField(
            value = code,
            onValueChange = { code = it },
            length = RESET_CODE_LENGTH,
            enabled = !busy,
        )

        Spacer(Modifier.height(20.dp))
        AsrPrimaryButton(
            text = "Continue",
            onClick = { onContinue(code) },
            enabled = code.length == RESET_CODE_LENGTH && !busy,
        )

        Spacer(Modifier.height(28.dp))
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
                "Check spam, make sure the address is right, or ask for a new one. " +
                    "A code works for ten minutes.",
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
                if (busy) "Sending…" else "Send a new code",
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
 * Reached from the code screen, which carries the address and the code
 * here. Submitting sends all three together, so this is the first moment
 * anything checks the code — a wrong or expired one surfaces as the error
 * under this form, and going back one screen is where a new one is asked
 * for.
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
            onContinue = {},
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
