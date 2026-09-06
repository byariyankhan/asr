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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.data.SupportedPerson
import io.joinasr.app.witness.Pronouns
import io.joinasr.app.ui.components.AsrProfilePhoto
import io.joinasr.app.data.WitnessProgress
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Relationships
import io.joinasr.app.witness.Witness

/** Which half of Figma 16 is showing. */
enum class CircleTab { Mine, Supporting }

/**
 * Figma 16 — Accountability / Two-Way Overview (node 172:2).
 *
 * The same tab this app already had, with the other direction added: people
 * this person is a witness *for*. The server has always answered
 * GET /v1/witnesses with both lists; this app decoded it as one array, so
 * every refresh failed to parse and the screen only ever showed what this
 * phone had added locally. That is fixed in the same change.
 *
 * Each supporting card needs a request of its own — the list carries the
 * relationship and the name, and the day, streak and limits come from
 * /witnesses/{id}/progress. Three people is three requests, which is the
 * right trade against inventing a batch endpoint for a list this short.
 */
@Composable
fun CircleScreen(
    tab: CircleTab,
    onTab: (CircleTab) -> Unit,
    witnesses: List<Witness>,
    supporting: List<SupportedPerson>,
    progress: Map<String, WitnessProgress>,
    onLoadProgress: (String) -> Unit,
    onOpenPerson: (SupportedPerson) -> Unit,
    onAdd: () -> Unit,
    hasChallenge: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(supporting) {
        for (person in supporting) if (person.id !in progress) onLoadProgress(person.id)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("ACCOUNTABILITY", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Your circle", style = AsrType.display(34), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Mutual accountability, without the oversharing.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(16.dp))
        Tabs(
            tab = tab,
            onTab = onTab,
            // Accepted only, like the list under it. A tab reading "3" over
            // a list of nobody is the app disagreeing with itself.
            mine = witnesses.count { it.accepted },
            supporting = supporting.size,
        )

        when (tab) {
            CircleTab.Mine -> WitnessesBody(
                witnesses = witnesses,
                onAdd = onAdd,
                hasChallenge = hasChallenge,
            )

            CircleTab.Supporting -> {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "People I support",
                        style = AsrType.display(20),
                        color = AsrColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (supporting.isEmpty()) "none" else "${supporting.size} active",
                        style = AsrType.Legal.copy(fontSize = 12.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (supporting.isEmpty()) {
                    NobodyYet()
                } else {
                    for (person in supporting) {
                        SupportCard(
                            person = person,
                            progress = progress[person.id],
                            onOpen = { onOpenPerson(person) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))
                PrivacyNote()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Tabs(tab: CircleTab, onTab: (CircleTab) -> Unit, mine: Int, supporting: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(AsrColors.Surface, RoundedCornerShape(24.dp))
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(24.dp))
            .padding(4.dp),
    ) {
        Tab("My witnesses · $mine", tab == CircleTab.Mine, Modifier.weight(1f)) {
            onTab(CircleTab.Mine)
        }
        Tab("I support · $supporting", tab == CircleTab.Supporting, Modifier.weight(1f)) {
            onTab(CircleTab.Supporting)
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AsrColors.AccentMuted else AsrColors.Surface)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = if (selected) AsrColors.Accent else AsrColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One person being supported.
 *
 * Everything numeric on it waits for the progress request rather than
 * guessing: a card that read "0 day streak · 0 of 0 within limits" for a
 * second would be telling a witness their friend had failed.
 */
@Composable
private fun SupportCard(
    person: SupportedPerson,
    progress: WitnessProgress?,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val current = progress?.current
    val within = current?.withinLimits

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsrProfilePhoto(
                imagePath = person.user.image,
                fallback = person.user.name,
                size = 44.dp,
                initialSize = 16,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.user.name,
                    style = AsrType.CardTitle,
                    color = AsrColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        current != null -> "${current.of}-day pact · Day ${current.day}"
                        progress != null -> "No challenge running"
                        else -> Relationships.labelFor(person.relationship)
                    },
                    style = AsrType.Legal.copy(fontSize = 12.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (current != null) {
                    SmallPill("ACTIVE", AsrColors.Accent, AsrColors.AccentMuted)
                }
                if (person.mutual) {
                    if (current != null) Spacer(Modifier.height(4.dp))
                    SmallPill("MUTUAL", AsrColors.TextSecondary, AsrColors.Surface)
                }
            }
        }

        if (current != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${progress.streakDays} day streak",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextPrimary,
                )
                Text(
                    "  •  ",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextTertiary,
                )
                Text(
                    if (within != null) {
                        "${within.within} of ${within.total} within limits"
                    } else {
                        "limits not reported today"
                    },
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(10.dp))
            Bar(fraction = current.day.toFloat() / current.of.coerceAtLeast(1))

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View progress",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier.weight(1f),
                )
                Text("›", style = AsrType.display(22), color = AsrColors.Accent)
            }
        } else if (progress == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Loading ${Pronouns.of(person.user.gender).their} progress…",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
internal fun Bar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AsrColors.Surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AsrColors.Accent),
        )
    }
}

@Composable
internal fun SmallPill(
    text: String,
    colour: androidx.compose.ui.graphics.Color,
    fill: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, AsrColors.FieldBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = colour)
    }
}

@Composable
private fun NobodyYet() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Text(
            "Nobody has asked you yet",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "When somebody invites you as their witness, they appear here and you " +
                "are told if they break their pact.",
            style = AsrType.Label.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun PrivacyNote() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", style = AsrType.display(16), color = AsrColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(
            "You only see progress they explicitly share with you.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun CirclePreview() {
    AsrTheme {
        CircleScreen(
            tab = CircleTab.Supporting,
            onTab = {},
            witnesses = emptyList(),
            supporting = emptyList(),
            progress = emptyMap(),
            onLoadProgress = {},
            onOpenPerson = {},
            onAdd = {},
            hasChallenge = true,
        )
    }
}
