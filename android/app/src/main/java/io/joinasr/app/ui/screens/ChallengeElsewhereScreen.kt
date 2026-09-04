package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.sync.RemoteChallenge
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * The challenge is running, and it is running on another phone.
 *
 * This screen exists because of one thing Android cannot do: a phone can see
 * how long its own screen has shown Instagram, and nothing else's. Two phones
 * signed into the same account, each enforcing thirty minutes, is a person
 * with an hour -- and witnesses reading a number that flips between the two
 * every half hour depending on which one reported last.
 *
 * So a challenge runs on one handset at a time, and a second phone shows this
 * instead of a dashboard it could not tell the truth on. Moving it here is a
 * decision, taken on a screen that says what happens to the other phone.
 *
 * This is also what a reinstall lands on, and that is right rather than
 * unfortunate: uninstalling took usage access and the overlay grant with it,
 * so the challenge has to be picked back up deliberately and the permissions
 * asked for again. A challenge silently restored without them would be a
 * challenge that blocks nothing while saying PROTECTED.
 */
@Composable
fun ChallengeElsewhereScreen(
    challenge: RemoteChallenge,
    busy: Boolean,
    onContinueHere: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pact = challenge.pact
    val progress = ChallengeProgress.of(pact.startedAtMillis, pact.durationDays)
    val phone = challenge.phone ?: "your other phone"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Text("CHALLENGE RUNNING", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            "It is being kept on $phone.",
            style = AsrType.display(30),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "A challenge runs on one phone at a time. That phone is the only one " +
                "that can see how long these apps have been open, so it is the one " +
                "your witnesses are hearing from.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(26.dp))
        Summary(progress = progress, apps = pact.apps)

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(
            text = if (busy) "Moving…" else "Continue on this phone",
            onClick = onContinueHere,
            enabled = !busy,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This phone will start blocking, and $phone will stop. Your challenge, " +
                "its days and its witnesses all stay exactly as they are.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Summary(progress: ChallengeProgress, apps: List<PactApp>) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(18.dp),
    ) {
        Text(
            "${progress.totalDays}-DAY CHALLENGE",
            style = AsrType.Eyebrow.copy(fontSize = 11.sp),
            color = AsrColors.Accent,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Day ${progress.dayNumber}",
            style = AsrType.display(26),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${progress.daysLeft} to go",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(18.dp))
        for (app in apps) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    app.label,
                    style = AsrType.Field.copy(fontSize = 15.sp),
                    color = AsrColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${app.limitMinutes} min",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                    modifier = Modifier.width(70.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ChallengeElsewherePreview() {
    AsrTheme {
        ChallengeElsewhereScreen(
            challenge = RemoteChallenge(
                pact = Pact(
                    apps = listOf(
                        PactApp("com.instagram.android", "Instagram", 30),
                        PactApp("com.google.android.youtube", "YouTube", 45),
                    ),
                    startedAtMillis = System.currentTimeMillis() - 4L * 86_400_000,
                    durationDays = 30,
                ),
                remoteId = "pact",
                onThisPhone = false,
                phone = "Samsung Galaxy A54",
            ),
            busy = false,
            onContinueHere = {},
        )
    }
}
