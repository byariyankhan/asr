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
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/** One person keeping somebody honest. */
data class Witness(
    val id: String,
    val name: String,
    val relationship: String,
    val active: Boolean,
    /** The last thing they reacted with, if they have. */
    val reaction: String? = null,
)

/**
 * Figma 15 — Accountability / My Witnesses (node 91:2).
 *
 * The list is empty, and will be until witnesses can be invited: that needs
 * an invite on the server, a link for the other person to open, and a way to
 * tell them a pact broke, none of which exist yet. The screen is drawn
 * anyway, with the counts reading zero and the summary saying what that
 * means, because the alternative is either a tab that is not there or a tab
 * showing three people who do not exist.
 *
 * Everything else on the frame is real and ready: the moment the invite flow
 * lands, the cards below fill in.
 */
@Composable
fun WitnessesScreen(
    witnesses: List<Witness>,
    onAdd: () -> Unit,
    addEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = witnesses.count { it.active }

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
        Text("Witnesses", style = AsrType.display(34), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "People who keep your challenge honest.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        SummaryCard(count = witnesses.size, active = active)

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your witnesses",
                style = AsrType.display(22),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$active active",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))
        if (witnesses.isEmpty()) {
            EmptyState()
        } else {
            for (witness in witnesses) {
                WitnessCard(witness)
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        AddWitnessButton(onClick = onAdd, enabled = addEnabled)

        Spacer(Modifier.height(16.dp))
        LockNote()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCard(count: Int, active: Int) {
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
            StatusPill(
                text = when {
                    count == 0 -> "NONE YET"
                    active == count -> "ALL ACTIVE"
                    else -> "SOME PENDING"
                },
                highlighted = count > 0 && active == count,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (count == 0) {
                "Nobody is watching this challenge yet."
            } else {
                "They'll be notified if you break the pact."
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
        Box(contentAlignment = Alignment.BottomStart) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AsrColors.Background)
                    .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    witness.name.take(1).uppercase(),
                    style = AsrType.Button.copy(fontSize = 16.sp),
                    color = AsrColors.Accent,
                )
            }
            if (witness.reaction != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AsrColors.Background)
                        .border(1.5.dp, AsrColors.Accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(witness.reaction, style = AsrType.Label.copy(fontSize = 13.sp))
                }
            }
        }

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                witness.name,
                style = AsrType.CardTitle,
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Relationship · ${witness.relationship}",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        StatusPill(if (witness.active) "ACTIVE" else "PENDING", witness.active)
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
            "A witness is somebody who is told when you break your pact. " +
                "Inviting them needs the invite link, which is the next part " +
                "being built.",
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
private fun WitnessesEmptyPreview() {
    AsrTheme { WitnessesScreen(witnesses = emptyList(), onAdd = {}, addEnabled = false) }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun WitnessesPreview() {
    AsrTheme {
        WitnessesScreen(
            witnesses = listOf(
                Witness("1", "Mom", "Mom", active = true, reaction = "👏"),
                Witness("2", "Brother", "Brother", active = true, reaction = "😂"),
                Witness("3", "Friend", "Friend", active = false),
            ),
            onAdd = {},
            addEnabled = true,
        )
    }
}
