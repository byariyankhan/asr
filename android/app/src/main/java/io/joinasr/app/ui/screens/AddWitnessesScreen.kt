package io.joinasr.app.ui.screens

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrSelectField
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Relationship
import io.joinasr.app.witness.Relationships
import io.joinasr.app.witness.ShareInvite
import io.joinasr.app.witness.Witness

/**
 * Figma 08 — Setup / Add Witnesses (node 67:25).
 *
 * The design's own answer to inviting somebody is Android's share sheet, and
 * it is the right one: a relationship is chosen, the server issues an invite
 * link for it, and Share hands the invitation to whatever the person already
 * uses to talk to their mother.
 *
 * The link comes back from the server rather than being composed here. It
 * allocates the code and stores it against the account, so what gets shared
 * is a URL something will actually answer.
 *
 * There is no name field because the frame has none. A witness is a
 * relationship and an invitation; the name arrives with the person when they
 * accept.
 */
@Composable
fun AddWitnessesScreen(
    fromName: String,
    challengeDays: Int,
    witnesses: List<Witness>,
    onBack: () -> Unit,
    onInvite: (relationship: String) -> Unit,
    onContinue: () -> Unit,
    /** Set once the server has issued a link; cleared by [onShared]. */
    pendingShare: ShareInvite?,
    onShared: () -> Unit,
    inviting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    /** The setup step shows its number and requires one; the tab does not. */
    showStepNumber: Boolean = true,
) {
    val context = LocalContext.current

    // The sheet opens when the invite comes back, not when Share is pressed:
    // there is nothing to share until the server has issued the link.
    LaunchedEffect(pendingShare) {
        val invite = pendingShare ?: return@LaunchedEffect
        val text = Relationships.inviteText(
            fromName = fromName,
            relationship = invite.relationship,
            days = challengeDays,
            url = invite.url,
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(share, "Invite a witness")) }
        onShared()
    }

    // What each empty slot has selected but not yet shared. Keyed by slot so
    // choosing a relationship in slot 2 does not disturb slot 3.
    val chosen = remember { mutableStateMapOf<Int, Relationship>() }

    val enough = witnesses.size >= Relationships.REQUIRED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(22.dp))
        if (showStepNumber) {
            Text("SETUP 5 OF 6", style = AsrType.Eyebrow, color = AsrColors.Accent)
            Spacer(Modifier.height(14.dp))
        }
        Text("Add your witnesses.", style = AsrType.display(34), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose who can track your progress and get notified if you break the challenge.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        RuleCard()

        Spacer(Modifier.height(18.dp))
        for (slot in 0 until Relationships.SLOTS) {
            val invited = witnesses.getOrNull(slot)
            WitnessSlot(
                number = slot + 1,
                invited = invited,
                selected = chosen[slot],
                onSelect = { chosen[slot] = it },
                busy = inviting,
                onShare = {
                    val relationship = chosen[slot]?.value ?: return@WitnessSlot
                    onInvite(relationship)
                    chosen.remove(slot)
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${witnesses.size} of ${Relationships.SLOTS} added",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = if (enough) AsrColors.Accent else AsrColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!enough) {
                Text(
                    "Add at least ${Relationships.REQUIRED} witness",
                    style = AsrType.Legal.copy(fontSize = 12.sp),
                    color = AsrColors.TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        AsrPrimaryButton(text = "Continue", onClick = onContinue, enabled = enough)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RuleCard() {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Text(
            "${Relationships.REQUIRED} required · ${Relationships.SLOTS} recommended",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Relationship personalises the invite and the notifications.",
            style = AsrType.Label.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun WitnessSlot(
    number: Int,
    invited: Witness?,
    selected: Relationship?,
    onSelect: (Relationship) -> Unit,
    onShare: () -> Unit,
    busy: Boolean,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(
                1.dp,
                if (invited != null) AsrColors.Accent else AsrColors.FieldBorder,
                shape,
            )
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AsrColors.Background)
                .border(1.dp, AsrColors.FieldBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextPrimary,
            )
        }

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (invited != null) invited.label else "Witness $number",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    invited != null -> "Invited · waiting for them to accept"
                    number <= Relationships.REQUIRED -> "Required"
                    else -> "Recommended"
                },
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = when {
                    invited != null -> AsrColors.Accent
                    number <= Relationships.REQUIRED -> AsrColors.Accent
                    else -> AsrColors.TextSecondary
                },
            )

            if (invited == null) {
                Spacer(Modifier.height(10.dp))
                AsrSelectField(
                    label = "",
                    selected = selected,
                    placeholder = "Select relationship",
                    options = Relationships.all,
                    optionLabel = Relationship::label,
                    onSelect = onSelect,
                )
            }
        }

        if (invited == null) {
            Spacer(Modifier.width(12.dp))
            ShareButton(enabled = selected != null && !busy, busy = busy, onClick = onShare)
        }
    }
}

@Composable
private fun ShareButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (enabled) AsrColors.AccentMuted else AsrColors.Field)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (busy) "…" else "Share",
            style = AsrType.Field.copy(fontSize = 13.sp),
            color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun AddWitnessesPreview() {
    AsrTheme {
        AddWitnessesScreen(
            fromName = "Ariyan",
            challengeDays = 14,
            witnesses = listOf(Witness("1", "mother", 0)),
            onBack = {},
            onInvite = {},
            onContinue = {},
            pendingShare = null,
            onShared = {},
            inviting = false,
            errorMessage = null,
        )
    }
}
