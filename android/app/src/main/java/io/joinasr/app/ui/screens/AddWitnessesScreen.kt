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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 *
 * The frame draws three numbered slots and this screen used to build exactly
 * that: three rows, each either a picker or an invite already sent, reading
 * "Invited · waiting for them to accept". Both halves of that were wrong.
 *
 * Three was the layout mistaken for a rule — nothing about being watched
 * gets worse with more people watching, and somebody who wants four people
 * told was being refused the fourth by a number that came from how many
 * rectangles fit on a phone.
 *
 * And a sent invitation is not something to display back. It is a message in
 * somebody else's inbox that may never be opened; the person sending it
 * already knows who they sent it to, cannot do anything about the waiting,
 * and three rows saying "waiting" read as three witnesses when the honest
 * number is zero.
 *
 * So: one picker, used as many times as they like, and nothing pretending to
 * be a witness until somebody has actually accepted. That happens on the
 * circle screen, by name.
 */
@Composable
fun AddWitnessesScreen(
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
    /**
     * False while a challenge is running with nobody invited to it.
     *
     * There is nothing behind this screen then: the pact is committed, and
     * a back chevron would offer to leave a challenge nobody is watching --
     * which is a challenge in name only. Continue is already gated on
     * having invited somebody; this closes the other way out.
     */
    showBack: Boolean = true,
) {
    val context = LocalContext.current

    // The sheet opens when the invite comes back, not when Share is pressed:
    // there is nothing to share until the server has issued the link.
    LaunchedEffect(pendingShare) {
        val invite = pendingShare ?: return@LaunchedEffect
        val text = Relationships.inviteText(
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

    // What the picker has selected but not yet shared. Cleared on share, so
    // the next invitation starts from an empty field rather than from the
    // last relationship chosen.
    var chosen by remember { mutableStateOf<Relationship?>(null) }

    val enough = witnesses.size >= Relationships.REQUIRED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        if (showBack) AsrBackChevron(onBack) else Spacer(Modifier.height(48.dp))

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

        // What is still choosable. A relationship only one person can hold
        // disappears once somebody has accepted it: being refused after
        // choosing and sharing is a worse way to learn the rule than never
        // being offered it.
        val options = Relationships.available(witnesses)

        Spacer(Modifier.height(18.dp))
        InvitePicker(
            options = options,
            selected = chosen,
            onSelect = { chosen = it },
            busy = inviting,
            onShare = {
                val relationship = chosen?.value ?: return@InvitePicker
                onInvite(relationship)
                chosen = null
            },
        )

        errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            // Said once and without a number. That an invitation went out is
            // worth confirming; how many are outstanding is not, because
            // waiting is not a state anybody can act on.
            if (enough) {
                "Invitation shared. Invite as many people as you like — they " +
                    "appear in your circle once they accept."
            } else {
                "A challenge nobody is watching is a challenge in name only, so " +
                    "at least one invitation goes out before this one starts. Pick " +
                    "who they are to you, and Share hands it to whatever you " +
                    "already use to talk to them."
            },
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = if (enough) AsrColors.Accent else AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(18.dp))
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
            "At least ${Relationships.REQUIRED} · no limit",
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

/**
 * One relationship and a Share button, used as often as somebody likes.
 *
 * It keeps no memory of what has been shared. Whether an invitation is
 * outstanding belongs to the person holding the other phone, and the only
 * thing this screen can honestly offer is the chance to send another.
 */
@Composable
private fun InvitePicker(
    options: List<Relationship>,
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
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Who is this person to you?",
                style = AsrType.Field.copy(fontSize = 15.sp),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            AsrSelectField(
                label = "",
                selected = selected,
                placeholder = "Select relationship",
                options = options,
                optionLabel = Relationship::label,
                onSelect = onSelect,
            )
        }
        Spacer(Modifier.width(12.dp))
        ShareButton(enabled = selected != null && !busy, busy = busy, onClick = onShare)
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
