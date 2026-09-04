package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.challenge.DayOutcome
import io.joinasr.app.challenge.WeeklyProgress
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.formatMinutes
import io.joinasr.app.usage.UsageHistory
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Figma 14 — Progress / Overview (node 88:2).
 *
 * Seven real days. Android keeps its usage events for weeks and will answer
 * for any window, so the week is seven queries rather than a table this app
 * would have to write, migrate and keep correct — and it comes back through
 * the same measurement the block screen uses, so the two can never disagree.
 *
 * One thing in the frame still stays at zero: past challenges, which needs
 * more than the one pact this app stores. It is drawn anyway, because a card
 * saying nothing has happened yet is honest and a card that is missing looks
 * like a bug.
 */
@Composable
fun ProgressScreen(
    /** Null when no challenge is running, which is an ordinary state. */
    pact: Pact?,
    /** Bonus minutes won today, per package. */
    earnedMinutes: Map<String, Int>,
    onStartChallenge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val limits = pact?.limitsByPackage.orEmpty()

    // Read once when the screen opens. Yesterday does not change, and a
    // week of queries is not something to repeat every few seconds.
    val days by produceState(initialValue = emptyList<DayOutcome>(), pact) {
        val history = UsageHistory.lastDays(context, days = 7)
        value = WeeklyProgress.outcomes(history, limits)
    }

    val allowance = WeeklyProgress.dailyAllowance(limits)
    val challenge = pact?.let { ChallengeProgress.of(it.startedAtMillis, it.durationDays) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "YOUR PROGRESS",
            style = AsrType.Eyebrow.copy(fontSize = 12.sp),
            color = AsrColors.Accent,
        )
        Spacer(Modifier.height(10.dp))
        Text("Progress", style = AsrType.display(30), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "See how you're doing over time.",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        // Nothing here can be computed from nothing: "days within limits"
        // needs limits, and a week of grey bars against an allowance of zero
        // would read as a week of failure rather than a week with no
        // challenge in it.
        if (pact == null) {
            Spacer(Modifier.height(26.dp))
            NothingToTrack(onStart = onStartChallenge)
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        Spacer(Modifier.height(26.dp))
        Text("This week", style = AsrType.display(20), color = AsrColors.TextPrimary)

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            StatCard(
                value = "${WeeklyProgress.daysWithinLimits(days)} / ${days.size.coerceAtLeast(1)}",
                caption = "DAYS WITHIN LIMITS",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = WeeklyProgress.breaches(days).toString(),
                caption = "BREACHES THIS WEEK",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Last 7 days",
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Controlled-app usage",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))
        UsageTrend(days = days, allowance = allowance)

        Spacer(Modifier.height(18.dp))
        EarnedTimeCard(minutes = earnedMinutes.values.sum())

        Spacer(Modifier.height(26.dp))
        Text("Challenges", style = AsrType.display(19), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(14.dp))
        if (challenge != null) ChallengeRow(pact = pact, progress = challenge)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(value: String, caption: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(76.dp)
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 15.dp, vertical = 11.dp),
    ) {
        Text(value, style = AsrType.display(25), color = AsrColors.TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            caption,
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = AsrColors.TextTertiary,
        )
    }
}

@Composable
private fun NothingToTrack(onStart: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Text("Nothing to track yet", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "This fills in once a challenge is running: the week's usage against " +
                "your limits, the days you kept them, and every challenge you have " +
                "finished.",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        AsrPrimaryButton(text = "Start a challenge", onClick = onStart)
    }
}

/**
 * The week as seven bars against the daily allowance.
 *
 * A day over the allowance is drawn grey rather than green, which is the
 * distinction the design makes and the only one that matters here: the
 * point of the chart is which days went over, not how tall the bars are.
 */
@Composable
private fun UsageTrend(days: List<DayOutcome>, allowance: Int) {
    val shape = RoundedCornerShape(18.dp)
    val ceiling = WeeklyProgress.chartCeiling(days, allowance)
    val plotHeight = 91.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Minutes used",
                style = AsrType.Label.copy(fontSize = 11.sp),
                color = AsrColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${formatMinutes(allowance)} total daily allowance",
                style = AsrType.Legal.copy(fontSize = 11.sp),
                color = AsrColors.TextTertiary,
            )
        }

        Spacer(Modifier.height(14.dp))
        if (days.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(plotHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Reading this week…",
                    style = AsrType.Label,
                    color = AsrColors.TextSecondary,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(plotHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                for (day in days) {
                    val fraction = (day.totalMinutes.toFloat() / ceiling).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(plotHeight * fraction)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (day.withinLimits) AsrColors.Accent else AsrColors.TextTertiary,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AsrColors.Track))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (day in days) {
                    Text(
                        initialOf(day.dayStartMillis),
                        style = AsrType.Label.copy(fontSize = 10.sp),
                        color = AsrColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(18.dp),
                    )
                }
            }
        }
    }
}

/** The one-letter weekday the design labels each bar with. */
private fun initialOf(dayStartMillis: Long): String =
    Instant.ofEpochMilli(dayStartMillis)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
        .getDisplayName(TextStyle.NARROW, Locale.getDefault())

@Composable
private fun EarnedTimeCard(minutes: Int) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Earned time", style = AsrType.Field, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (minutes > 0) {
                    "Earned today, on top of your limits"
                } else {
                    // Zero is a fact, not a gap: nothing has been earned
                    // today, and saying so beats an empty space.
                    "Walk or focus to earn more time"
                },
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AsrColors.Background)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${minutes}m",
                style = AsrType.Button.copy(fontSize = 13.sp),
                color = if (minutes > 0) AsrColors.Accent else AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun ChallengeRow(pact: Pact, progress: ChallengeProgress) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${pact.durationDays}-Day Challenge",
                style = AsrType.Field.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AsrColors.Background)
                    .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (progress.isComplete) "COMPLETED" else "ACTIVE",
                    style = AsrType.Eyebrow.copy(fontSize = 9.sp),
                    color = AsrColors.Accent,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (progress.isComplete) {
                "Finished · ${progress.totalDays} days"
            } else {
                "Day ${progress.dayNumber} of ${progress.totalDays}"
            },
            style = AsrType.Legal.copy(fontSize = 11.sp),
            color = AsrColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            pact.apps.joinToString(", ") { it.label },
            style = AsrType.Legal.copy(fontSize = 11.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ProgressPreview() {
    AsrTheme {
        ProgressScreen(
            pact = Pact(
                apps = listOf(
                    PactApp("com.instagram.android", "Instagram", 15),
                    PactApp("com.google.android.youtube", "YouTube", 30),
                ),
                startedAtMillis = System.currentTimeMillis(),
            ),
            earnedMinutes = emptyMap(),
            onStartChallenge = {},
        )
    }
}
