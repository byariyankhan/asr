package io.joinasr.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.BuildConfig
import io.joinasr.app.diagnostics.TestCrash
import io.joinasr.app.support.SupportAnswer
import io.joinasr.app.support.SupportTexts
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 35 — Profile / Help & support.
 *
 * The row existed and led nowhere, which is worse than not having it: a
 * person with a problem pressed the one thing on the screen that promised
 * help and got nothing.
 *
 * Answers first, address second, and in that order on purpose. Three of the
 * questions are about things this app does that look like faults until they
 * are explained -- the permanent notification, the two permissions, and
 * limits that cannot be edited once a challenge starts. A support page that
 * does not answer those receives all three by email, from people who were
 * about to uninstall.
 */
@Composable
fun HelpAndSupportScreen(
    onBack: () -> Unit,
    /** The signed-in address, put in the draft so support knows whose account it is. */
    accountEmail: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(22.dp))
        Text("HELP & SUPPORT", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Questions", style = AsrType.display(32), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "The ones that come up most. If yours is not here, write to us.",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        for (entry in SupportTexts.questions) {
            QuestionCard(entry)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(16.dp))
        ContactCard(
            onWrite = {
                // ACTION_SENDTO with a mailto: URI, so only mail apps offer
                // to handle it -- ACTION_SEND would put the browser and every
                // messenger in the chooser for something addressed to one
                // address.
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:${SupportTexts.EMAIL}")
                    putExtra(Intent.EXTRA_SUBJECT, "Asr support")
                    putExtra(Intent.EXTRA_TEXT, draft(accountEmail))
                }
                runCatching { context.startActivity(intent) }
            },
        )

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * What the mail is prefilled with.
 *
 * The version and the phone, because the first reply to a bug report is
 * always a request for them, and the account address, because the second is
 * "which account?". Everything here is above the person's own signature and
 * visible before they press send: nothing is collected, it is written into a
 * draft they can delete.
 */
internal fun draft(accountEmail: String?): String = buildString {
    append("\n\n")
    append("——\n")
    append("Asr ${BuildConfig.VERSION_NAME}\n")
    append("Android ${Build.VERSION.RELEASE}\n")
    append("${Build.MANUFACTURER} ${Build.MODEL}\n")
    if (!accountEmail.isNullOrBlank()) append("Account: $accountEmail\n")
}

/** A question that opens when pressed. Closed by default: eight open answers is a wall. */
@Composable
private fun QuestionCard(entry: SupportAnswer) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button) { open = !open }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.question,
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (open) "−" else "+",
                style = AsrType.display(18),
                color = AsrColors.Accent,
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text(
                entry.answer,
                style = AsrType.Legal.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun ContactCard(onWrite: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    // TEMPORARY: the hidden test-crash trigger, see diagnostics/TestCrash.kt.
    val context = LocalContext.current
    var versionTaps by remember { mutableIntStateOf(0) }
    var askTestCrash by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(18.dp),
    ) {
        Text("Still stuck?", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Write to us and say what happened. We read every one.",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Email support",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.Accent,
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.AccentMuted, RoundedCornerShape(14.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onWrite)
                .padding(vertical = 13.dp, horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))
        // Selectable, and shown whether or not a mail app exists to open:
        // a phone with no mail app set up is exactly the phone where the
        // button does nothing, and an address you can copy still works.
        SelectionContainer {
            Text(
                SupportTexts.EMAIL,
                style = AsrType.Legal.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Asr ${BuildConfig.VERSION_NAME}",
            style = AsrType.Legal.copy(fontSize = 11.sp),
            color = AsrColors.TextTertiary,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                versionTaps += 1
                if (versionTaps >= TestCrash.TAPS) {
                    versionTaps = 0
                    askTestCrash = true
                }
            },
        )
    }

    // TEMPORARY, with the trigger above. Asks before crashing so a stray
    // seventh tap never closes the app on somebody; the non-fatal goes out
    // the moment the question appears.
    if (askTestCrash) {
        LaunchedEffect(Unit) { TestCrash.nonFatal(context) }
        AlertDialog(
            onDismissRequest = { askTestCrash = false },
            title = { Text("Send a test crash report?") },
            text = {
                Text(
                    "Asr will close. Open it again afterwards: Crashlytics uploads the " +
                        "report on the next launch. A non-fatal test report was just recorded too.",
                )
            },
            confirmButton = {
                TextButton(onClick = { TestCrash.crash() }) { Text("Crash now") }
            },
            dismissButton = {
                TextButton(onClick = { askTestCrash = false }) { Text("Cancel") }
            },
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun HelpAndSupportPreview() {
    AsrTheme {
        HelpAndSupportScreen(onBack = {}, accountEmail = "someone@example.com")
    }
}
