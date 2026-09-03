package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.joinasr.app.ui.components.AsrInlineLink
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 01 — Onboarding / Welcome (node 2:2).
 *
 * The design places the text block at y=245 and the actions at y=690 on an
 * 852-tall frame. Those are not reproduced as absolute offsets: a fixed y is
 * wrong on every phone that is not a Pixel, and wrong again the moment
 * someone scales their font up. The same reading — a centred block with the
 * actions held at the bottom — is expressed as weighted space, which lands
 * in the same place on the designed size and stays sensible off it.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "RULE YOUR MIND",
            style = AsrType.Eyebrow,
            color = AsrColors.Accent,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Take back your attention.",
            style = AsrType.display(44),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Set limits. Earn extra time. Stay accountable.\n" +
                "Make your screen time intentional.",
            style = AsrType.Body,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1.4f))

        AsrPrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        AsrInlineLink(
            prefix = "Already have an account?",
            linkText = "Log in",
            onClick = onLogIn,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun WelcomePreview() {
    AsrTheme { WelcomeScreen(onContinue = {}, onLogIn = {}) }
}
