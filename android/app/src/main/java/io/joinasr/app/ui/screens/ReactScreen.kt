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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.data.InboxItem
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Reaction
import io.joinasr.app.witness.Reactions

/**
 * Figma 25 — Notification / React — Breach (node 143:2).
 *
 * Reached by opening a notification about somebody this person is a witness
 * for. It reacts to the event named in that notification, which is how the
 * API works and also the honest shape: a reaction is about a thing that
 * happened, not about a person.
 *
 * Everything on it comes from the notification the server sent — the title,
 * the body, the time. This screen writes no sentence of its own about what
 * somebody did, because the version that matters is the one they were
 * already sent.
 */
@Composable
fun ReactScreen(
    item: InboxItem,
    /** Their name, when this phone knows it from the supporting list. */
    personName: String?,
    /** What was already sent for this event, if anything. */
    chosen: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onSend: (emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = Reactions.forEvent(if (item.kind == "pact_broken") "broken" else item.kind)
    var picked by remember(item.id) { mutableStateOf(Reactions.of(chosen) ?: options.first()) }
    val breach = item.kind == "pact_broken"
    val who = personName?.trim()?.takeIf { it.isNotBlank() } ?: "They"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(18.dp))
        Text("ACCOUNTABILITY", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(item.title, style = AsrType.display(32), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(ago(item.createdAt), style = AsrType.Field, color = AsrColors.TextSecondary)

        Spacer(Modifier.height(22.dp))
        EventCard(item = item, breach = breach)

        Spacer(Modifier.height(18.dp))
        VisibilityNote(who = who)

        Spacer(Modifier.height(26.dp))
        Text(
            if (personName != null) "React to $personName" else "React",
            style = AsrType.display(22),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick one. You can change it later.",
            style = AsrType.Legal.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for ((index, option) in options.withIndex()) {
                if (index > 0) Spacer(Modifier.width(11.dp))
                Tile(
                    option = option,
                    selected = picked.value == option.value,
                    modifier = Modifier.weight(1f),
                ) { picked = option }
            }
        }

        Spacer(Modifier.height(18.dp))
        AdaptiveNote(breach = breach)

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = if (busy) "Sending…" else "Send ${picked.label.lowercase()} ${picked.emoji}",
            onClick = { onSend(picked.value) },
            enabled = !busy,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            "Not now",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun EventCard(item: InboxItem, breach: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (breach) AsrColors.WarningMuted else AsrColors.AccentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (breach) "!" else "✓",
                style = AsrType.display(22),
                color = if (breach) AsrColors.Warning else AsrColors.Accent,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (breach) "Challenge breached" else "Challenge update",
                style = AsrType.RowTitle,
                color = AsrColors.TextPrimary,
            )
            item.body?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = AsrType.Label.copy(fontSize = 14.sp),
                    color = AsrColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        SmallPill(
            text = if (breach) "BREACH" else "UPDATE",
            colour = if (breach) AsrColors.Warning else AsrColors.Accent,
            fill = if (breach) AsrColors.WarningMuted else AsrColors.AccentMuted,
        )
    }
}

@Composable
private fun VisibilityNote(who: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Text(
            "Your reaction is visible to them",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "It appears with your profile photo on their Witnesses page.",
            style = AsrType.Legal.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun AdaptiveNote(breach: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (breach) "👏" else "🍅", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.width(12.dp))
        Text(
            if (breach) {
                "Completed challenges show positive reactions like Clap."
            } else {
                "Breaches show the other set. Nobody gets a tomato for finishing."
            },
            style = AsrType.Legal.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun Tile(
    option: Reaction,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AsrColors.AccentMuted else AsrColors.SurfaceSunken)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) AsrColors.Accent else AsrColors.FieldBorder,
                RoundedCornerShape(18.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(option.emoji, style = AsrType.display(28), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(9.dp))
        Text(
            option.label,
            style = AsrType.Legal.copy(fontSize = 11.sp),
            color = if (selected) AsrColors.Accent else AsrColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ReactPreview() {
    AsrTheme {
        ReactScreen(
            item = InboxItem(
                id = "1",
                kind = "pact_broken",
                title = "Rafi broke his pact",
                body = "Rafi exceeded a locked app limit during his 14-day challenge.",
                eventId = "e1",
            ),
            personName = "Rafi",
            chosen = null,
            busy = false,
            onBack = {},
            onSend = {},
        )
    }
}
