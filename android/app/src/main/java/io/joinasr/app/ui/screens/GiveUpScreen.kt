package io.joinasr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrProfilePhoto
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import io.joinasr.app.witness.Witness

/**
 * The way out.
 *
 * Every challenge needs one. Without it the only exit is to uninstall, and
 * that ending is worse for everyone standing in it: the person loses their
 * history and does not come back, and their witnesses are told the app was
 * removed — the harshest message this product has — about somebody who was
 * only tired.
 *
 * So this screen is not here to talk anybody out of it. It is here to make
 * sure nobody arrives at the other side surprised, which means saying the
 * one thing that is actually true and actually costly: these people, by
 * name and by face, are about to be told. Not "are you sure?", which asks
 * nothing and is answered by everybody the same way.
 *
 * Keeping going is the primary button because it is the better outcome and
 * the accidental tap should land there. Giving up is a plain line of text,
 * reachable in one press, not hidden and not confirmed twice. A door that
 * takes three taps to open is a door people go through the wall instead.
 */
@Composable
fun GiveUpScreen(
    dayNumber: Int,
    totalDays: Int,
    /** Everyone invited; only those who accepted will hear about this. */
    witnesses: List<Witness>,
    onKeepGoing: () -> Unit,
    onGiveUp: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val watching = witnesses.filter { it.accepted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onKeepGoing)

        Spacer(Modifier.height(22.dp))
        Text("GIVE UP", style = AsrType.Eyebrow, color = AsrColors.Breach)
        Spacer(Modifier.height(14.dp))
        Text("End this challenge?", style = AsrType.display(32), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "You are on day $dayNumber of $totalDays. Ending it now counts as a " +
                "failed challenge, and it stays in your history that way.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        if (watching.isEmpty()) {
            NobodyToTell()
        } else {
            WhoGetsTold(watching)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            // Said plainly, because it is the reason this screen exists at
            // all. Somebody who would rather uninstall than have this
            // message sent should find that out here, where their history
            // survives it, and not by deleting the app.
            "Your limits stop straight away. You can start a new challenge " +
                "whenever you want.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(text = "Keep going", onClick = onKeepGoing, enabled = !busy)

        Spacer(Modifier.height(16.dp))
        Text(
            if (busy) "Ending…" else "Give up anyway",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = if (busy) AsrColors.TextTertiary else AsrColors.Breach,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !busy, role = Role.Button, onClick = onGiveUp)
                .padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The people, by name and by face.
 *
 * A count would not do this work. "2 witnesses will be notified" is a fact
 * about a number; a photograph of the person's mother is a fact about their
 * mother, and it is the second one that is actually the price of the button
 * underneath it.
 */
@Composable
private fun WhoGetsTold(witnesses: List<Witness>) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(16.dp),
    ) {
        Text(
            "They will be told",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
        )
        for (witness in witnesses) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsrProfilePhoto(
                    imagePath = witness.image,
                    fallback = witness.label,
                    size = 40.dp,
                    initialSize = 15,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        witness.label,
                        style = AsrType.CardTitle,
                        color = AsrColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        witness.relationshipLabel,
                        style = AsrType.Legal.copy(fontSize = 12.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Nobody accepted, so nobody hears.
 *
 * Reachable: invitations go out before a challenge starts, and an invitation
 * is not an acceptance. Saying so is better than an empty card, and better
 * than implying a message will be sent that will not be.
 */
@Composable
private fun NobodyToTell() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(16.dp),
    ) {
        Text(
            "Nobody has accepted yet",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "So nobody will be told. The challenge still ends as a failure.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun GiveUpPreview() {
    AsrTheme {
        GiveUpScreen(
            dayNumber = 4,
            totalDays = 14,
            witnesses = listOf(
                Witness("1", "mother", 0, accepted = true, name = "Rehana Khan"),
                Witness("2", "friend", 0, accepted = true, name = "Sabbir"),
            ),
            onKeepGoing = {},
            onGiveUp = {},
            busy = false,
        )
    }
}
