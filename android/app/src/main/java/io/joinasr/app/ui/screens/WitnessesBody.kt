package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.components.AsrProfilePhoto
import io.joinasr.app.witness.Reactions
import io.joinasr.app.witness.Witness
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 15 — Accountability / My Witnesses (node 91:2).
 *
 * The people who accepted appear here, by name. Nothing about invitations
 * that have not been answered does — not as rows and not as a count — so
 * every number on this screen is a number of people who would actually be
 * told.
 *
 * It has no header and no scroller of its own, because Figma 16 puts it
 * under a tab bar. Two nested scrolling columns is a thing Compose will
 * happily let you build and a thing no scroll gesture survives.
 *
 * The reaction badge the frame draws over each avatar is not here yet. The
 * endpoint exists; nothing in this app reads it, and an empty badge on every
 * row would be three pixels of decoration pretending to be data.
 */
@Composable
fun ColumnScope.WitnessesBody(
    witnesses: List<Witness>,
    onAdd: () -> Unit,
    /** False when no challenge is running: there is nothing to witness yet. */
    hasChallenge: Boolean = true,
) {
    // Only the people who accepted.
    //
    // An invite that has been sent is not a witness, it is a message
    // somebody may never open, and a list of them is a list of things the
    // person cannot act on: they already know who they sent it to. Worse,
    // three rows reading "Partner · INVITED" look like three witnesses,
    // which is the one number on this screen that has to be true — it is
    // what decides whether anybody finds out if the pact breaks.
    //
    // Nor is the count of them shown. "You have sent 3 invites" was the same
    // claim with the rows taken away: still a number about people who are
    // not watching, still nothing to do about it, and still read as progress
    // toward being watched when it is none.
    val joined = witnesses.filter { it.accepted }

    Spacer(Modifier.height(20.dp))
    SummaryCard(count = joined.size)

    Spacer(Modifier.height(26.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Your witnesses",
            style = AsrType.display(22),
            color = AsrColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (joined.isEmpty()) "none yet" else "${joined.size} active",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
    }

    Spacer(Modifier.height(14.dp))
    if (joined.isEmpty()) {
        EmptyState()
    } else {
        for (witness in joined) {
            WitnessCard(witness)
            Spacer(Modifier.height(12.dp))
        }
    }

    Spacer(Modifier.height(14.dp))
    AddWitnessButton(onClick = onAdd, enabled = hasChallenge)
    if (!hasChallenge) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Witnesses are invited to a challenge, so this opens once one is running.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )
    }

    Spacer(Modifier.height(16.dp))
    LockNote()
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SummaryCard(count: Int) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(count.toString(), style = AsrType.display(36), color = AsrColors.TextPrimary)
            Spacer(Modifier.width(14.dp))
            Text(
                "ACTIVE WITNESSES",
                style = AsrType.Eyebrow.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = if (count > 0) "ACTIVE" else "NONE YET", highlighted = count > 0)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (count > 0) {
                "They'll be notified if you break the pact."
            } else {
                "Nobody is watching this challenge yet."
            },
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun StatusPill(text: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) AsrColors.AccentMuted else AsrColors.Background)
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = if (highlighted) AsrColors.Accent else AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun WitnessCard(witness: Witness) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsrProfilePhoto(
            imagePath = witness.image,
            fallback = witness.label,
            size = 48.dp,
            initialSize = 16,
        )

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                witness.label,
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Relationship · ${witness.relationshipLabel}",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // Status above, what they have actually done below it.
        //
        // Reacting is the only thing a witness can do, and it was a push
        // notification and then nothing: somebody's brother throws a tomato,
        // the phone buzzes once, and by the evening there is no trace of it.
        // This is the screen listing the people who did it.
        Column(horizontalAlignment = Alignment.End) {
            StatusPill("ACTIVE", highlighted = true)
            if (witness.reactions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    for (reaction in witness.reactions) {
                        val emoji = Reactions.of(reaction)?.emoji ?: continue
                        Text(
                            emoji,
                            style = AsrType.Field.copy(fontSize = 15.sp),
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(18.dp),
    ) {
        Text("No witnesses yet", style = AsrType.CardTitle, color = AsrColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "A witness is somebody who is told when you break your pact. Add one " +
                "and Asr issues an invite link, then hands it to whatever you " +
                "already use to talk to them. They appear here by name once they " +
                "accept.",
            style = AsrType.Legal,
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun AddWitnessButton(onClick: () -> Unit, enabled: Boolean) {
    val shape = RoundedCornerShape(27.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) AsrColors.AccentMuted else AsrColors.Surface)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "+",
            style = AsrType.display(22),
            color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "Add another witness",
            style = AsrType.CardTitle.copy(fontSize = 17.sp),
            color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "›",
            style = AsrType.display(22),
            color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
        )
    }
}

@Composable
private fun LockNote() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", style = AsrType.display(20), color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Text(
            "Witnesses stay locked until the challenge ends.",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextPrimary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun WitnessesPreview() {
    AsrTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AsrColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            WitnessesBody(
                witnesses = listOf(
                    Witness("1", "mother", 0, accepted = true, name = "Mum"),
                    Witness("2", "brother", 0),
                ),
                onAdd = {},
                )
        }
    }
}
