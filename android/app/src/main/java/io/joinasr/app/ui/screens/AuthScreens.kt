package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.components.AsrInlineLink
import io.joinasr.app.ui.components.AsrPanel
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrTextField
import io.joinasr.app.ui.components.AsrTextLink
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 02 — Auth / Sign Up (32:2) and 32 — Auth / Log In (37:19).
 *
 * The two frames are the same screen with different words: back arrow,
 * eyebrow, title, then a panel holding the fields and the action. They share
 * one scaffold here rather than being copied, so a change to the panel's
 * spacing cannot land on one and miss the other.
 *
 * Both are scrollable. The design assumes an 852dp frame with nothing in the
 * way; on a real phone the keyboard takes roughly half the screen, and a
 * fixed layout would bury the button under it.
 */
@Composable
private fun AuthScaffold(
    eyebrow: String,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    panel: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        BackChevron(onBack)

        Spacer(Modifier.height(52.dp))
        Text(eyebrow, style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(title, style = AsrType.display(38), color = AsrColors.TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(subtitle, style = AsrType.Body, color = AsrColors.TextSecondary)
        }

        Spacer(Modifier.height(40.dp))
        panel()
        footer()
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The chevron is drawn as text rather than shipped as an icon: the design
 * uses the character U+2039, and a 48dp touch target is added around it
 * because the glyph itself is far below the minimum a thumb can hit.
 */
@Composable
private fun BackChevron(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", style = AsrType.display(30), color = AsrColors.TextPrimary)
    }
}

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScaffold(
        eyebrow = "CREATE ACCOUNT",
        title = "Create your account.",
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
        footer = {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "By continuing, you agree to the Terms and Privacy Policy.",
                style = AsrType.Legal,
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        AsrPanel {
            AsrTextField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(18.dp))
            AsrTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                placeholder = "At least 8 characters",
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            Spacer(Modifier.height(24.dp))
            AsrPrimaryButton(
                text = if (submitting) "Creating account…" else "Create account",
                onClick = { onSubmit(email.trim(), password) },
                // The server is the authority on what a valid password is;
                // this only refuses a submission that cannot possibly succeed,
                // so the button is never dead for a reason nobody explained.
                enabled = !submitting && email.isNotBlank() && password.isNotEmpty(),
            )
            AsrFormError(errorMessage)
            Spacer(Modifier.height(10.dp))
            AsrInlineLink(
                prefix = "Already have an account?",
                linkText = "Log in",
                onClick = onLogIn,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun LogInScreen(
    onBack: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScaffold(
        eyebrow = "WELCOME BACK",
        title = "Log in.",
        subtitle = "Pick up where you left off.",
        onBack = onBack,
        modifier = modifier,
    ) {
        AsrPanel {
            AsrTextField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(18.dp))
            AsrTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                placeholder = "Your password",
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            Spacer(Modifier.height(4.dp))
            AsrTextLink(
                text = "Forgot password?",
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(10.dp))
            AsrPrimaryButton(
                text = if (submitting) "Logging in…" else "Log in",
                onClick = { onSubmit(email.trim(), password) },
                enabled = !submitting && email.isNotBlank() && password.isNotEmpty(),
            )
            AsrFormError(errorMessage)
            Spacer(Modifier.height(10.dp))
            AsrInlineLink(
                prefix = "New here?",
                linkText = "Create account",
                onClick = onCreateAccount,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * What the server said, under the button that caused it. Occupies no space
 * when there is nothing to say, so the panel does not jump the first time a
 * password is wrong — the field it refers to is right above it.
 */
@Composable
private fun AsrFormError(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        style = AsrType.Label,
        color = AsrColors.Error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun SignUpPreview() {
    AsrTheme { SignUpScreen(onBack = {}, onSubmit = { _, _ -> }, onLogIn = {}) }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun LogInPreview() {
    AsrTheme {
        LogInScreen(onBack = {}, onSubmit = { _, _ -> }, onForgotPassword = {}, onCreateAccount = {})
    }
}
