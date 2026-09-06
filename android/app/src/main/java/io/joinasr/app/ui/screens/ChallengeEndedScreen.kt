package io.joinasr.app.ui.screens

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.daysLabel
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.enforcement.Breach
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.enforcement.PactOutcome
import io.joinasr.app.enforcement.PactResult
import io.joinasr.app.enforcement.WitnessTold
import io.joinasr.app.witness.Pronouns
import io.joinasr.app.ui.components.AsrAppIcon
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.util.Date

/**
 * Figma 26 — Challenge / Failed — Pact Broken (node 152:2).
 *
 * Shown once, when a challenge ends. There is no back chevron on it in this
 * build even though the frame draws one: there is nothing behind it. The
 * challenge is over, the pact is gone, and the only two ways on are the two
 * the design gives.
 *
 * The completed case has no frame of its own — the file has 37 screens and
 * none of them is "challenge finished". Rather than leaving the payoff of
 * the whole product as a silent return to the setup flow, it is drawn in
 * this frame's structure with the result reversed. That is an adaptation and
 * is marked as one; when the designer draws the real screen, this splits.
 */
@Composable
fun ChallengeEndedScreen(
    outcome: PactOutcome,
    onStartNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = outcome.result == PactResult.Failed
    val accent = if (failed) AsrColors.Breach else AsrColors.Accent

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            if (failed) "PACT BROKEN" else "PACT KEPT",
            style = AsrType.Eyebrow,
            color = accent,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (failed) "Challenge failed." else "Challenge complete.",
            style = AsrType.display(36),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (failed) {
                // The day it ended on, whether a limit broke or the person
                // stopped it. A breach carries its own, recorded when it
                // happened; a give-up is counted back from the two
                // timestamps every outcome has.
                val day = outcome.breach?.dayNumber ?: ChallengeProgress.of(
                    startedAtMillis = outcome.startedAtMillis,
                    durationDays = outcome.durationDays,
                    nowMillis = outcome.endedAtMillis,
                ).dayNumber
                if (outcome.breach != null) {
                    "Your ${outcome.durationDays}-day challenge ended on Day $day."
                } else {
                    "You ended your ${outcome.durationDays}-day challenge on Day $day."
                }
            } else {
                "You kept every limit for ${daysLabel(outcome.durationDays)}."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        // Keyed on how it ended, not on whether there is a breach to draw.
        // Those came apart the moment a challenge could be given up: a
        // failure with nothing breached used to fall through to the card
        // that says "Limits held · KEPT", congratulating somebody on the
        // screen telling them they quit.
        if (outcome.breach != null) {
            BreachCard(outcome.breach)
        } else if (failed) {
            GaveUpCard(outcome.apps)
        } else {
            KeptCard(outcome.apps, outcome.durationDays)
        }

        Spacer(Modifier.height(18.dp))
        WitnessCard(
            count = outcome.witnesses,
            reported = outcome.reported,
            failed = failed,
            told = outcome.witnessesTold,
        )

        Spacer(Modifier.height(18.dp))
        RecordCard(failed = failed)

        Spacer(Modifier.height(26.dp))
        Text("What happens now?", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Start a new challenge whenever you are ready. Your previous result " +
                "remains in your history.",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(text = "Start a new challenge", onClick = onStartNew)

        Spacer(Modifier.height(18.dp))
        Text(
            "Back to dashboard",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * What actually happened, in the numbers that were true at the time.
 *
 * The used figure is whole minutes because that is what the app measures.
 * The frame shows "18m 24s", and inventing a seconds column out of a minute
 * counter would be making the most important number on the screen slightly
 * false in the one place somebody might check it.
 */
@Composable
private fun BreachCard(breach: Breach) {
    val shape = RoundedCornerShape(20.dp)
    val context = LocalContext.current
    val at = DateFormat.getTimeFormat(context).format(Date(breach.atMillis))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsrAppIcon(
                packageName = breach.packageName,
                label = breach.label,
                size = 48.dp,
                corner = 14.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    breach.label,
                    style = AsrType.CardTitle,
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Daily limit exceeded",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            Pill("BREACH", AsrColors.Breach, AsrColors.BreachMuted)
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(AsrColors.FieldBorder))
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Figure("DAILY LIMIT", "${breach.limitMinutes} min", AsrColors.TextPrimary, Modifier.weight(1f))
            Figure("USED", "${breach.usedMinutes} min", AsrColors.Breach, Modifier.weight(1f))
            Figure("BREACHED", at, AsrColors.TextPrimary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "Day ${breach.dayNumber}",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
                modifier = Modifier.width(96.dp),
            )
            Text(
                "${breach.label} stayed active past the locked daily limit.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The completed case: the same card, listing what was actually held to. */
/**
 * Ended by the person, not by a limit.
 *
 * No breach to show and nothing to congratulate: what these apps were meant
 * to be held to is the whole of what there is to say. The apps are listed
 * with their limits because that is what was given up on, and a screen that
 * showed only a sentence would leave somebody unsure what they had just
 * cancelled.
 */
@Composable
private fun GaveUpCard(apps: List<PactApp>) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You ended it",
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Pill("GAVE UP", AsrColors.Breach, AsrColors.BreachMuted)
        }
        Spacer(Modifier.height(14.dp))
        for (app in apps) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    app.label,
                    style = AsrType.Field.copy(fontSize = 14.sp),
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${app.limitMinutes} min/day",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "These limits are off now.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun KeptCard(apps: List<PactApp>, days: Int) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Limits held",
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Pill("KEPT", AsrColors.Accent, AsrColors.AccentMuted)
        }
        Spacer(Modifier.height(14.dp))
        for (app in apps) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    app.label,
                    style = AsrType.Field.copy(fontSize = 14.sp),
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${app.limitMinutes} min/day",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "$days days, every day.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

/**
 * What the witnesses know.
 *
 * The frame says "3 witnesses notified · SENT", and this says that only when
 * it is true. A challenge that ended with no signal has an event still in the
 * outbox, and telling somebody their mother was notified when the request has
 * not left the phone is exactly the kind of lie this app cannot afford.
 */
@Composable
private fun WitnessCard(count: Int, reported: Boolean, failed: Boolean, told: List<WitnessTold>) {
    val shape = RoundedCornerShape(20.dp)
    val sent = reported && count > 0
    // One person is named and takes their own pronoun; two or more are
    // "they". Outcomes written before the names were kept have only the
    // count, and read as they did.
    val one = told.singleOrNull()?.takeIf { count == 1 }
    val their = one?.let { Pronouns.of(it.gender).their.replaceFirstChar { c -> c.uppercase() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    count == 0 -> "No witnesses"
                    one != null && sent -> "${one.label} notified"
                    one != null -> "${one.label} to notify"
                    sent -> "$count ${plural(count)} notified"
                    else -> "$count ${plural(count)} to notify"
                },
                style = AsrType.RowTitle,
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (count > 0) {
                if (sent) {
                    Pill("SENT", AsrColors.Accent, AsrColors.AccentMuted)
                } else {
                    Pill("QUEUED", AsrColors.TextSecondary, AsrColors.Field)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                count == 0 && failed ->
                    "Nobody was watching this one. A challenge with witnesses is harder to walk away from."
                count == 0 -> "Nobody was watching this one."
                one != null && sent ->
                    "${one.label} was told as soon as it happened. $their reaction appears on Witnesses."
                one != null -> "${one.label} will be told as soon as this phone is back online."
                sent -> "They were told as soon as it happened. Their reactions appear on Witnesses."
                else -> "They will be told as soon as this phone is back online."
            },
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun RecordCard(failed: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (failed) AsrColors.BreachMuted else AsrColors.AccentMuted, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            if (failed) "×" else "✓",
            style = AsrType.display(24),
            color = if (failed) AsrColors.Breach else AsrColors.Accent,
            modifier = Modifier.width(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (failed) "Recorded as failed" else "Recorded as completed",
                style = AsrType.Field.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This challenge stays in Progress history. It cannot be restored or edited.",
                style = AsrType.Legal.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, colour: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = AsrColors.TextTertiary)
        Spacer(Modifier.height(7.dp))
        Text(
            value,
            style = AsrType.CardTitle,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Pill(text: String, colour: Color, fill: Color) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, AsrColors.FieldBorder, CircleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = colour)
    }
}

private fun plural(count: Int) = if (count == 1) "witness" else "witnesses"

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ChallengeFailedPreview() {
    AsrTheme {
        ChallengeEndedScreen(
            outcome = PactOutcome(
                result = PactResult.Failed,
                startedAtMillis = 0,
                endedAtMillis = 1_772_193_720_000L,
                durationDays = 14,
                apps = listOf(PactApp("com.instagram.android", "Instagram", 15)),
                breach = Breach("com.instagram.android", "Instagram", 15, 18, 1_772_193_720_000L, 6),
                witnesses = 3,
                reported = true,
            ),
            onStartNew = {},
            onDismiss = {},
        )
    }
}
