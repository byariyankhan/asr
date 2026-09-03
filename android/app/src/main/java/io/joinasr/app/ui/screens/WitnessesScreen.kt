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
import io.joinasr.app.witness.Witness
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/**
 * Figma 15 — Accountability / My Witnesses (node 91:2).
 *
 * Witnesses invited on Figma 08 appear here. They stay pending until the
 * other person accepts, which needs an invite the server issues and a link
 * that opens this app — so for now every one of them reads INVITED, which is
 * exactly what it is.
 *
 * The reaction badge the frame draws over each avatar is not here. A
 * reaction is something a witness sends, and nothing can reach them yet.
 */
@Composable
fun WitnessesScreen(
    witnesses: List<Witness>,
    onAdd: () -> Unit,
    addEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accepted = witnesses.count { it.accepted }

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
        SummaryCard(count = witnesses.size, accepted = accepted)

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your witnesses",
                style = AsrType.display(22),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (witnesses.isEmpty()) "none yet" else "${witnesses.size} invited",
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
private fun SummaryCard(count: Int, accepted: Int) {
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
                    accepted == count -> "ALL ACTIVE"
                    else -> "INVITED"
                },
                highlighted = count > 0 && accepted == count,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                count == 0 -> "Nobody is watching this challenge yet."
                accepted == count -> "They'll be notified if you break the pact."
                else -> "Invitations are out. They'll be notified once they accept."
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
                    witness.label.take(1).uppercase(),
                    style = AsrType.Button.copy(fontSize = 16.sp),
                    color = AsrColors.Accent,
                )
            }
        }

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
                "Relationship · ${witness.label}",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        StatusPill(if (witness.accepted) "ACTIVE" else "INVITED", witness.accepted)
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
                "Add one and Asr hands the invitation to whatever you already " +
                "use to talk to them.",
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
    AsrTheme { WitnessesScreen(witnesses = emptyList(), onAdd = {}, addEnabled = true) }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun WitnessesPreview() {
    AsrTheme {
        WitnessesScreen(
            witnesses = listOf(
                Witness("1", "mother", 0, accepted = true),
                Witness("2", "brother", 0),
            ),
            onAdd = {},
            addEnabled = true,
        )
    }
}
