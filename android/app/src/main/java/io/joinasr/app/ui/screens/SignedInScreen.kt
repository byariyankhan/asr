package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.joinasr.app.apps.AppEntry
import io.joinasr.app.data.Me
import io.joinasr.app.formatMinutes
import io.joinasr.app.ui.components.AsrPanel
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Not a designed screen. Figma ends setup at the review screen (11) and the
 * dashboard (13), and neither exists yet; this is what stands in for them.
 *
 * Two things are shown, both of them real. The name and email are what the
 * *server* returned from GET /v1/me rather than what was typed, which is
 * what makes them proof the session token works. The limits are what the
 * person just set, held in memory, and the note below says plainly that
 * nothing enforces them yet -- a placeholder that implied otherwise would be
 * worse than no screen at all.
 */
@Composable
fun SignedInScreen(
    me: Me,
    apps: List<AppEntry>,
    limits: Map<String, Int>,
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

        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AsrPanel {
                Text("Your limits", style = AsrType.Label, color = AsrColors.TextSecondary)
                for (app in apps) {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            app.label,
                            style = AsrType.Body,
                            color = AsrColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatMinutes(limits[app.packageName] ?: 0),
                            style = AsrType.Body,
                            color = AsrColors.Accent,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Nothing above is enforced yet. The service that watches app " +
                "usage and shows the block screen is the next part being built, " +
                "and these limits are held in memory until the review screen " +
                "commits them.",
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
            apps = listOf(AppEntry("com.instagram.android", "Instagram")),
            limits = mapOf("com.instagram.android" to 30),
            onSignOut = {},
        )
    }
}
