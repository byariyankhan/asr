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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.data.Me
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType

/** Where a row on the profile screen goes. */
enum class ProfileDestination {
    PersonalDetails,
    EmailAndPassword,
    Permissions,
    HelpAndSupport,
    PrivacyPolicy,
    TermsOfService,
}

/**
 * Figma 28 — Profile / Overview (node 107:2).
 *
 * The permissions row is live: it counts the three grants the app actually
 * holds and says so, rather than always reading 3/3. Everything else is a
 * row leading to a screen; the ones whose screen does not exist yet are
 * drawn but not tappable, which is visibly different from a row that does
 * nothing when pressed.
 */
@Composable
fun ProfileScreen(
    me: Me,
    onOpen: (ProfileDestination) -> Unit,
    available: Set<ProfileDestination>,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = PermissionState.read(context)
        onPauseOrDispose {}
    }
    val granted = listOf(
        permissions.usageAccess,
        permissions.overlay,
        permissions.notifications,
    ).count { it }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("ACCOUNT", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Profile", style = AsrType.display(34), color = AsrColors.TextPrimary)

        Spacer(Modifier.height(22.dp))
        Summary(
            me = me,
            onEdit = { onOpen(ProfileDestination.PersonalDetails) },
            editEnabled = ProfileDestination.PersonalDetails in available,
        )

        Spacer(Modifier.height(26.dp))
        Text("Account", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        LinkRow("Personal details", ProfileDestination.PersonalDetails, available, onOpen)
        Spacer(Modifier.height(8.dp))
        LinkRow("Email & password", ProfileDestination.EmailAndPassword, available, onOpen)
        Spacer(Modifier.height(8.dp))
        LinkRow(
            title = "App permissions",
            destination = ProfileDestination.Permissions,
            available = available,
            onOpen = onOpen,
            trailing = { PermissionPill(granted = granted) },
        )
        Spacer(Modifier.height(8.dp))
        LinkRow("Help & support", ProfileDestination.HelpAndSupport, available, onOpen)

        Spacer(Modifier.height(26.dp))
        Text("Privacy & legal", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        LinkRow("Privacy Policy", ProfileDestination.PrivacyPolicy, available, onOpen)
        Spacer(Modifier.height(8.dp))
        LinkRow("Terms of Service", ProfileDestination.TermsOfService, available, onOpen)

        Spacer(Modifier.height(30.dp))
        LogOutButton(onSignOut)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Summary(me: Me, onEdit: () -> Unit, editEnabled: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(AsrColors.Background)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The photo is on the server and is fetched over the network,
            // which this app has no image loader for yet. The initial is
            // what the design falls back to anyway when there is no photo.
            Text(
                me.name.trim().take(1).uppercase().ifBlank { "?" },
                style = AsrType.display(20),
                color = AsrColors.Accent,
            )
        }

        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                me.name.ifBlank { "Your account" },
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                me.email,
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (editEnabled) AsrColors.AccentMuted else AsrColors.Background)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = editEnabled, role = Role.Button, onClick = onEdit)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "EDIT",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = if (editEnabled) AsrColors.Accent else AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    destination: ProfileDestination,
    available: Set<ProfileDestination>,
    onOpen: (ProfileDestination) -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val enabled = destination in available
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(enabled = enabled, role = Role.Button) { onOpen(destination) }
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = if (enabled) AsrColors.TextPrimary else AsrColors.TextTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            trailing()
        } else {
            Text(
                "›",
                style = AsrType.display(22),
                color = if (enabled) AsrColors.TextSecondary else AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun PermissionPill(granted: Int) {
    val all = granted == 3
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (all) AsrColors.AccentMuted else AsrColors.Background)
            .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$granted/3 ON",
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = if (all) AsrColors.Accent else AsrColors.TextSecondary,
        )
    }
}

@Composable
private fun LogOutButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(AsrColors.SurfaceSunken)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Log out",
            style = AsrType.Field.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ProfilePreview() {
    AsrTheme {
        ProfileScreen(
            me = Me(id = "1", name = "Ariyan Khan", email = "ariyan@example.com"),
            onOpen = {},
            available = setOf(ProfileDestination.Permissions),
            onSignOut = {},
        )
    }
}
