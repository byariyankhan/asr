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
 * Three things and nothing else: what happened (the notification's own
 * title and body, with a mark for breach or update and when it was), the
 * reactions to choose from, and one button to send the one chosen. The
 * screen writes no sentence of its own about what somebody did, because
 * the version that matters is the one they were already sent; and it
 * explains nothing about reactions, because somebody who has tapped a
 * notification to react does not need telling that the reaction will be
 * seen, or that they can pick one.
 */
@Composable
fun ReactScreen(
    item: InboxItem,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        // What happened: the kind and the time on one line, then the
        // notification's own words.
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallPill(
                text = if (breach) "BREACH" else "UPDATE",
                colour = if (breach) AsrColors.Warning else AsrColors.Accent,
                fill = if (breach) AsrColors.WarningMuted else AsrColors.AccentMuted,
            )
            Spacer(Modifier.width(10.dp))
            Text(ago(item.createdAt), style = AsrType.Field, color = AsrColors.TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Text(item.title, style = AsrType.display(32), color = AsrColors.TextPrimary)
        item.body?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(16.dp))
            EventCard(body = it, breach = breach)
        }

        // The choices.
        Spacer(Modifier.height(28.dp))
        Text("REACT", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for ((index, option) in options.withIndex()) {
                if (index > 0) Spacer(Modifier.width(10.dp))
                Tile(
                    option = option,
                    selected = picked.value == option.value,
                    modifier = Modifier.weight(1f),
                ) { picked = option }
            }
        }

        // The one action. The tile already shows what is being sent.
        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(
            text = if (busy) "Sending…" else "Send",
            onClick = { onSend(picked.value) },
            enabled = !busy,
        )

        Spacer(Modifier.height(12.dp))
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

/** The notification's body beside a mark for what kind of thing it was. */
@Composable
private fun EventCard(body: String, breach: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (breach) AsrColors.WarningMuted else AsrColors.AccentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (breach) "!" else "✓",
                style = AsrType.display(20),
                color = if (breach) AsrColors.Warning else AsrColors.Accent,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            body,
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.TextPrimary,
            modifier = Modifier.weight(1f),
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
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(option.emoji, style = AsrType.display(30), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
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
            chosen = null,
            busy = false,
            onBack = {},
            onSend = {},
        )
    }
}
