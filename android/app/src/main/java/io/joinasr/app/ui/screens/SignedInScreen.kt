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
import io.joinasr.app.data.Me
import io.joinasr.app.ui.components.AsrPanel
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Not a designed screen — the Figma flow goes from sign-up straight into
 * profile setup (03) and the permission steps (05, 09, 10), and those are
 * next.
 *
 * It exists so the flow has an end, and it deliberately shows the name and
 * email as the *server* reported them from GET /v1/me rather than what was
 * typed. That difference is the point: seeing them here is the proof that
 * the session token works, which a screen echoing local state would not
 * give.
 */
@Composable
fun SignedInScreen(
    me: Me,
    onSignOut: () -> Unit,
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

        Text("SIGNED IN", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "You have an account.",
            style = AsrType.display(38),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        AsrPanel {
            Text("Name on the server", style = AsrType.Label, color = AsrColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(me.name, style = AsrType.Body, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            Text("Email", style = AsrType.Label, color = AsrColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(me.email, style = AsrType.Body, color = AsrColors.TextPrimary)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Setting up your first pact is the next part being built. " +
                "The name above is a placeholder until the About You screen exists.",
            style = AsrType.Legal,
            color = AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))
        AsrPrimaryButton(text = "Sign out", onClick = onSignOut, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun SignedInPreview() {
    AsrTheme {
        SignedInScreen(
            me = Me(id = "1", name = "ariyanfiles", email = "ariyanfiles@gmail.com"),
            onSignOut = {},
        )
    }
}
