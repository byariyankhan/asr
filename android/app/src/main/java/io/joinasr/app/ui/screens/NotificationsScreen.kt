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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.data.InboxItem
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrProfilePhoto
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Figma 19 — Notifications / Inbox (node 136:2).
 *
 * Every line's words come from the server, which is also what went out as
 * the push notification and the email. Three places writing "Mom accepted
 * your invite" independently is three places for it to drift, and the only
 * one a witness will ever quote back is the one they were sent.
 *
 * The frame splits Today and Earlier, and so does this — from the
 * timestamps, not from a position in the list.
 */
@Composable
fun NotificationsScreen(
    items: List<InboxItem>,
    unread: Int,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpen: (InboxItem) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()
    val today = items.filter { withinHours(it.createdAt, 24, now) }
    val earlier = items - today.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(20.dp))
        Text("UPDATES", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications",
                style = AsrType.display(34),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (unread > 0) {
                Text(
                    "Mark all read",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onMarkAllRead)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Summary(unread = unread, count = items.size, loaded = loaded)

        if (today.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Today", style = AsrType.display(18), color = AsrColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            for (item in today) {
                NotificationCard(item, onOpen)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (earlier.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Earlier", style = AsrType.display(18), color = AsrColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            for (item in earlier) {
                NotificationCard(item, onOpen)
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Summary(unread: Int, count: Int, loaded: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Text(
            when {
                !loaded -> "Loading…"
                unread > 0 -> "$unread unread"
                count == 0 -> "Nothing yet"
                else -> "All read"
            },
            style = AsrType.CardTitle,
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (count == 0 && loaded) {
                "Updates about your challenge, your witnesses and your protection appear here."
            } else {
                "Important accountability and protection updates."
            },
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun NotificationCard(item: InboxItem, onOpen: (InboxItem) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val warning = item.kind in WARNING_KINDS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button) { onOpen(item) }
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Their face where the glyph was.
        //
        // Every one of these is a message about a person -- their mother
        // accepted, their friend reacted -- and a green tick made them all
        // look like the same event from nobody. The glyph stays for the ones
        // that are genuinely about the account rather than about somebody:
        // protection stopping, a limit going.
        val about = item.aboutUser
        if (about != null) {
            Box {
                AsrProfilePhoto(
                    imagePath = about.image,
                    fallback = about.name,
                    size = 40.dp,
                    initialSize = 15,
                )
                // The glyph does not disappear with the photo; it moves onto
                // it, small, so a broken pact still reads as one at a
                // glance.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (warning) AsrColors.WarningMuted else AsrColors.AccentMuted)
                        .border(
                            1.dp,
                            AsrColors.SurfaceSunken,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        glyphFor(item.kind),
                        style = AsrType.Legal.copy(fontSize = 10.sp),
                        color = if (warning) AsrColors.Warning else AsrColors.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        } else {
            Icon(
                glyph = glyphFor(item.kind),
                colour = if (warning) AsrColors.Warning else AsrColors.Accent,
                fill = if (warning) AsrColors.WarningMuted else AsrColors.AccentMuted,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
            )
            item.body?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ago(item.createdAt),
                style = AsrType.Legal.copy(fontSize = 11.sp),
                color = AsrColors.TextTertiary,
            )
        }
        if (item.unread) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AsrColors.Accent),
            )
        }
    }
}

@Composable
private fun Icon(glyph: String, colour: Color, fill: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = AsrType.RowTitle,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** The kinds that mean something needs doing, rather than something happened. */
private val WARNING_KINDS = setOf("pact_broken", "protection_lost", "protection_issue")

private fun glyphFor(kind: String): String = when (kind) {
    "pact_broken", "protection_lost", "protection_issue" -> "!"
    "activity_completed", "earned_time" -> "+"
    // A challenge that changed phones. Not a warning and not an achievement,
    // and a tick beside "moved to another phone" would read as approval of
    // something nobody has approved of yet.
    "pact_moved" -> "→"
    "reaction" -> "♥"
    else -> "✓"
}

private fun withinHours(isoTimestamp: String?, hours: Long, now: Instant): Boolean {
    val at = parseInstant(isoTimestamp) ?: return false
    return Duration.between(at, now).toHours() < hours
}

internal fun parseInstant(isoTimestamp: String?): Instant? {
    val text = isoTimestamp ?: return null
    return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(text) }.getOrNull()
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun NotificationsPreview() {
    AsrTheme {
        NotificationsScreen(
            items = listOf(
                InboxItem(
                    id = "1",
                    kind = "witness_accepted",
                    title = "Mum accepted your invite",
                    body = "Mum is now an active witness for your 14-day challenge.",
                ),
            ),
            unread = 1,
            loaded = true,
            onBack = {},
            onOpen = {},
            onMarkAllRead = {},
        )
    }
}
