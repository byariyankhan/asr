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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.data.InvitePeek
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Relationships

/**
 * Figma 18 — Witness Invite / Incoming (node 164:2).
 *
 * Opened by the link in the invitation, from a phone that may have no
 * account at all — which is the whole reason the lookup takes no token.
 *
 * The frame's "What you'll witness" card lists the inviter's apps, days and
 * limits. That data is deliberately not on the endpoint: it answers to
 * anybody holding a code, and returning somebody's app list to an
 * unauthenticated caller would be handing out the most personal thing in
 * this product to whoever forwarded the message. So the card says what a
 * witness will be able to see once they accept, which is true, instead of
 * three app names that would have to be invented.
 */
@Composable
fun WitnessInviteScreen(
    invite: InvitePeek?,
    /** Null while it loads; a sentence when the link is dead or answered. */
    errorMessage: String?,
    signedIn: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = invite?.inviterName?.trim().orEmpty().ifBlank { "Someone" }

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
        Text("ACCOUNTABILITY INVITE", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                errorMessage != null -> "This invite is closed."
                invite == null -> "Opening the invite…"
                else -> "$name invited you"
            },
            style = AsrType.display(34),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                errorMessage != null -> errorMessage
                invite == null -> "One moment."
                else -> "Become a witness for their challenge."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        if (invite != null) {
            Spacer(Modifier.height(22.dp))
            InviterCard(name = name, relationship = invite.relationship)

            Spacer(Modifier.height(24.dp))
            Text(
                "What you'll witness",
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            WhatYouSee(name = name)

            Spacer(Modifier.height(18.dp))
            PrivacyCard(name = name)

            Spacer(Modifier.height(14.dp))
            Note("◎", "You'll get updates when they keep or break the pact.")

            Spacer(Modifier.height(24.dp))
            AsrPrimaryButton(
                text = when {
                    busy -> "Accepting…"
                    signedIn -> "Accept invitation"
                    else -> "Sign in to accept"
                },
                onClick = onAccept,
                enabled = !busy,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Decline invitation",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy && signedIn, role = Role.Button, onClick = onDecline)
                    .padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun InviterCard(name: String, relationship: String) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Initial(name)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Invited you as · ${Relationships.labelFor(relationship)}",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "They want you to help keep this pact honest.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextTertiary,
            )
        }
        Spacer(Modifier.width(10.dp))
        SmallPill("INVITED", AsrColors.Accent, AsrColors.AccentMuted)
    }
}

@Composable
private fun WhatYouSee(name: String) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(16.dp),
    ) {
        for (line in listOf(
            "Which apps they limited, and for how long each day",
            "How far into the challenge they are, and their streak",
            "The moment a limit stops holding, as it happens",
        )) {
            Row(verticalAlignment = Alignment.Top) {
                Text("·", style = AsrType.display(16), color = AsrColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    line,
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "The numbers appear once you accept. Until then $name's limits are theirs.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )
    }
}

@Composable
private fun PrivacyCard(name: String) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.AccentMuted, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✓", style = AsrType.display(20), color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Your privacy is respected",
                style = AsrType.Field.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "You'll see limits, streaks and breach events. Never messages, " +
                    "browsing history, or what $name looks at.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Note(glyph: String, text: String) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, style = AsrType.display(18), color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextPrimary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun WitnessInvitePreview() {
    AsrTheme {
        WitnessInviteScreen(
            invite = InvitePeek(inviterName = "Ariyan", relationship = "mother"),
            errorMessage = null,
            signedIn = true,
            busy = false,
            onBack = {},
            onAccept = {},
            onDecline = {},
        )
    }
}
