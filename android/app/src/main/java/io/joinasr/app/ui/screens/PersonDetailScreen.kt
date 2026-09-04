package io.joinasr.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.daysLabelUpper
import io.joinasr.app.daysLabel
import io.joinasr.app.data.ProgressApp
import io.joinasr.app.data.RemotePactEvent
import io.joinasr.app.data.SupportedPerson
import io.joinasr.app.data.WitnessProgress
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.rememberNow
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Reaction
import io.joinasr.app.witness.Reactions
import io.joinasr.app.witness.Relationships
import java.time.Duration
import java.time.Instant

/**
 * Figma 17 — Supporting / Person Detail (node 173:2).
 *
 * What a witness is allowed to see, which is exactly what the person sees
 * about themselves and nothing else: today's totals for the apps they chose
 * to limit, the day they are on, and the events on the ledger. No raw usage,
 * nothing about any other app on their phone.
 *
 * Reactions attach to an event, not to a person — that is how the API works
 * and it is also the honest shape: there is nothing to react to until
 * something has happened. So the row reacts to their most recent event and
 * says which one.
 *
 * The frame draws Respect ♛, Strong 🔥, Push + and Roast 😂. None of those
 * exist as values; the API takes laugh, haha, shoe, tomato and clap. Sending
 * the drawn ones would be a 400 on every tap, so the server's set is what is
 * offered. See [Reactions].
 */
@Composable
fun PersonDetailScreen(
    person: SupportedPerson,
    progress: WitnessProgress?,
    reactions: Map<String, String>,
    onBack: () -> Unit,
    onReact: (eventId: String, emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = progress?.current
    val latest = progress?.recentEvents?.firstOrNull()
    // Ticking, so "9 hr ago" is nine hours ago rather than nine hours after
    // whatever last redrew this screen.
    val now by rememberNow()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsrBackChevron(onBack)
            Text("SUPPORTING", style = AsrType.Eyebrow, color = AsrColors.Accent)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            person.user.name,
            style = AsrType.display(34),
            color = AsrColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                current != null -> "${current.of}-day pact · Day ${current.day}"
                progress != null -> "No challenge running · ${Relationships.labelFor(person.relationship)}"
                else -> Relationships.labelFor(person.relationship)
            },
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(18.dp))
        StreakHero(progress = progress, active = current != null)

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Today's limits",
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            current?.withinLimits?.let {
                Text(
                    "${it.within} of ${it.total} in limits",
                    style = AsrType.Legal.copy(fontSize = 11.sp),
                    color = AsrColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TodayCard(apps = current?.apps.orEmpty(), loaded = progress != null)

        Spacer(Modifier.height(22.dp))
        Text("Recent activity", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        // The ledger stores package names; the labels live on the pact
        // snapshot. Showing "com.instagram.android" to somebody's mother
        // would be leaking an implementation detail into a sentence.
        val labels = current?.apps.orEmpty().associate { it.packageName to it.label }
        ActivityCard(
            events = progress?.recentEvents.orEmpty(),
            labels = labels,
            loaded = progress != null,
            now = now,
        )

        if (latest != null) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "React",
                    style = AsrType.display(20),
                    color = AsrColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Visible to ${person.user.name}",
                    style = AsrType.Legal.copy(fontSize = 11.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "To: " + Reactions.describe(
                    latest.type,
                    latest.appPackage?.let { labels[it] ?: it },
                    latest.minutes,
                ),
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
            ReactionRow(
                options = Reactions.forEvent(latest.type),
                chosen = reactions[latest.id],
                onPick = { onReact(latest.id, it.value) },
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StreakHero(progress: WitnessProgress?, active: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val current = progress?.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (progress == null) "—" else daysLabel(progress.streakDays),
                style = AsrType.display(30),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    progress == null -> "READING THEIR PROGRESS"
                    current != null -> {
                        val left = (current.of - current.day).coerceAtLeast(0)
                        "CURRENT STREAK · ${daysLabelUpper(left)} LEFT"
                    }
                    else -> "LONGEST STREAK ${daysLabelUpper(progress.longestStreakDays)}"
                },
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = AsrColors.TextTertiary,
            )
        }
        if (progress != null) {
            SmallPill(
                text = if (active) "PACT ACTIVE" else "NO PACT",
                colour = if (active) AsrColors.Accent else AsrColors.TextSecondary,
                fill = if (active) AsrColors.AccentMuted else AsrColors.Surface,
            )
        }
    }
}

@Composable
private fun TodayCard(apps: List<ProgressApp>, loaded: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (apps.isEmpty()) {
            Text(
                if (loaded) "No challenge running." else "Reading their limits…",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            return@Column
        }
        for ((index, app) in apps.withIndex()) {
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = AsrType.Label.copy(fontSize = 14.sp),
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        // Null is not zero. Zero means they have not opened
                        // it; null means their phone has not reported today,
                        // and reading the second as the first would be this
                        // screen inventing good news.
                        app.minutesUsed == null -> "${app.limitMinutes} min limit"
                        app.atLimit -> "${app.minutesUsed} / ${app.limitMinutes} min · locked"
                        else -> "${app.minutesUsed} / ${app.limitMinutes} min"
                    },
                    style = AsrType.Legal.copy(fontSize = 12.sp),
                    color = if (app.atLimit) AsrColors.Accent else AsrColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            Bar(fraction = app.fraction ?: 0f)
        }
        if (apps.any { it.minutesUsed == null }) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Figures appear once their phone reports today.",
                style = AsrType.Legal.copy(fontSize = 11.sp),
                color = AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun ActivityCard(
    events: List<RemotePactEvent>,
    labels: Map<String, String>,
    loaded: Boolean,
    /** Passed in rather than read here, so the times move when it does. */
    now: Instant,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (events.isEmpty()) {
            Text(
                if (loaded) "Nothing has happened yet." else "Reading the ledger…",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            return@Column
        }
        for ((index, event) in events.take(6).withIndex()) {
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (event.type == "broken") {
                                AsrColors.BreachMuted
                            } else {
                                AsrColors.AccentMuted
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (event.type) {
                            "broken" -> "×"
                            "activity_completed" -> "+"
                            "completed" -> "✓"
                            "limit_hit" -> "!"
                            else -> "↑"
                        },
                        style = AsrType.display(14),
                        color = if (event.type == "broken") {
                            AsrColors.Breach
                        } else {
                            AsrColors.Accent
                        },
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Reactions.describe(
                            event.type,
                            event.appPackage?.let { labels[it] ?: it },
                            event.minutes,
                        ),
                        style = AsrType.Label.copy(fontSize = 13.sp),
                        color = AsrColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        ago(event.receivedAt, now),
                        style = AsrType.Legal.copy(fontSize = 11.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(options: List<Reaction>, chosen: String?, onPick: (Reaction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for ((index, option) in options.withIndex()) {
            if (index > 0) Spacer(Modifier.width(8.dp))
            val selected = chosen == option.value
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) AsrColors.AccentMuted else AsrColors.Surface)
                    .border(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) AsrColors.Accent else AsrColors.FieldBorder,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(role = Role.Button) { onPick(option) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(option.emoji, style = AsrType.display(20), color = AsrColors.TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    option.label,
                    style = AsrType.Legal.copy(fontSize = 10.sp),
                    color = if (selected) AsrColors.Accent else AsrColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * "2 days ago", from an ISO timestamp. Rough on purpose: a witness needs to
 * know whether this is fresh, not the second it landed.
 */
internal fun ago(isoTimestamp: String?, now: Instant = Instant.now()): String {
    val at = parseInstant(isoTimestamp) ?: return "recently"
    val minutes = Duration.between(at, now).toMinutes()
    return when {
        minutes < 0 -> "just now"
        minutes < 2 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hr ago"
        minutes < 60 * 48 -> "yesterday"
        else -> "${daysLabel((minutes / (60 * 24)).toInt())} ago"
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PersonDetailPreview() {
    AsrTheme {
        PersonDetailScreen(
            person = SupportedPerson(
                id = "1",
                relationship = "brother",
                user = io.joinasr.app.data.RemoteUser("2", "Rafi"),
            ),
            progress = null,
            reactions = emptyMap(),
            onBack = {},
            onReact = { _, _ -> },
        )
    }
}
