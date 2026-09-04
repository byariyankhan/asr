package io.joinasr.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.ui.DashboardViewModel
import io.joinasr.app.ui.components.AsrPill
import io.joinasr.app.ui.greetingFor
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.time.Instant
import java.time.ZoneId

/**
 * Figma 13 — Dashboard / Home (node 76:2).
 *
 * The screen a person actually lives in. Everything on it is real: the day
 * count comes from when the pact was committed, and the minutes come from
 * the same measurement the enforcement service blocks on, so the row can
 * never say eight minutes while the block screen says twenty.
 *
 * Three things in the frame are not drawn, all for the same reason -- they
 * lead somewhere that does not exist, and a dashboard whose controls do
 * nothing teaches a person to stop trusting the screen:
 *
 *  - the notification bell and its unread badge (Figma 19)
 *  - the witness summary row (Figma 15)
 *  - the bottom navigation bar, three of whose four tabs have no screen
 *
 * The "PROTECTED" pill is drawn, but it tells the truth rather than always
 * reading PROTECTED: it is the live permission state, and a person whose
 * usage access has been switched off needs to learn it here rather than by
 * noticing that nothing was ever blocked.
 *
 * The bottom navigation bar is drawn by the shell around this screen, not
 * here, so the four tabs share one bar rather than four that have to agree.
 */
@Composable
fun DashboardScreen(
    pact: Pact,
    /** Opens Figma 27. Only reachable while a grant is actually missing. */
    onProtectionLost: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read on every return to the screen rather than once: these can be
    // revoked in Settings while the app sits in the background, and the pill
    // has to be right the moment somebody looks at it.
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = PermissionState.read(context)
        onPauseOrDispose {}
    }

    val icons by produceState(initialValue = emptyMap<String, ImageBitmap>(), pact) {
        val loaded = mutableMapOf<String, ImageBitmap>()
        for (app in pact.apps) {
            InstalledApps.icon(context, app.packageName)?.let {
                loaded[app.packageName] = it
                value = loaded.toMap()
            }
        }
    }

    val now = System.currentTimeMillis()
    val progress = ChallengeProgress.of(pact.startedAtMillis, pact.durationDays, now)
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            greetingFor(hour),
            style = AsrType.Eyebrow.copy(fontSize = 11.sp),
            color = AsrColors.Accent,
        )
        Spacer(Modifier.height(10.dp))
        Text("Stay in control.", style = AsrType.display(28), color = AsrColors.TextPrimary)

        Spacer(Modifier.height(22.dp))
        // Protected means all three of these, not one: the permissions are
        // there, the loop is running, and the system is not dropping the
        // block screen. Anything less and this app is not doing its job,
        // which is a thing to say out loud rather than paper over.
        val working = permissions.requiredGranted && state.loopLive && !state.blockDropped
        ChallengeCard(
            progress = progress,
            protected = working,
            onProtectionLost = onProtectionLost,
        )

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Today's limits",
                style = AsrType.display(20),
                color = AsrColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Live usage",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))
        for (app in pact.apps) {
            UsageRow(
                app = app,
                icon = icons[app.packageName],
                usedMinutes = state.minutesByPackage[app.packageName] ?: 0,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (!working) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsrColors.SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    when {
                        !permissions.usageAccess ->
                            "Nothing is being blocked. Usage access is off in Settings, " +
                                "so this app cannot see how long anything has been used."

                        !permissions.overlay ->
                            "Nothing is being blocked. \"Display over other apps\" is off " +
                                "in Settings. Android needs it to let this app put the " +
                                "block screen in front of you, and without it the block " +
                                "is dropped without a word."

                        state.blockDropped ->
                            "A block screen was refused by Android just now. That happens " +
                                "when \"display over other apps\" is off or has been " +
                                "revoked. Turn it back on in Settings and try again."

                        else ->
                            "Protection is not running. Android may have stopped the " +
                                "background service; opening this screen starts it again, " +
                                "and turning off battery optimisation for Asr keeps it " +
                                "running."
                    },
                    style = AsrType.Legal,
                    color = AsrColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChallengeCard(
    progress: ChallengeProgress,
    protected: Boolean,
    onProtectionLost: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${progress.totalDays}-DAY CHALLENGE",
                style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                color = AsrColors.Accent,
                modifier = Modifier.weight(1f),
            )
            // The pill is the way in to Figma 27, and only when it has
            // something to say: a person reading PROTECTED has nothing to
            // fix, and a tappable pill that opens a screen listing three
            // green rows would be a dead end dressed as a warning.
            Box(
                modifier = if (protected) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onProtectionLost)
                },
            ) {
                AsrPill(
                    text = if (protected) "PROTECTED" else "NOT PROTECTED",
                    highlighted = protected,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (progress.isComplete) "Complete" else "Day ${progress.dayNumber}",
            style = AsrType.display(42),
            color = AsrColors.TextPrimary,
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    progress.isComplete -> "${progress.totalDays} days done"
                    progress.daysLeft == 1 -> "1 day left"
                    else -> "${progress.daysLeft} days left"
                },
                style = AsrType.Label,
                color = AsrColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${progress.percent}%",
                style = AsrType.Label.copy(fontWeight = AsrType.CardTitle.fontWeight),
                color = AsrColors.Accent,
            )
        }

        Spacer(Modifier.height(14.dp))
        ProgressBar(fraction = progress.percent / 100f, height = 8.dp)
    }
}

@Composable
private fun UsageRow(app: PactApp, icon: ImageBitmap?, usedMinutes: Int) {
    val shape = RoundedCornerShape(16.dp)
    val locked = usedMinutes >= app.limitMinutes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(AsrColors.SurfaceRaised)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(app = app, icon = icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = AsrType.Field.copy(fontWeight = AsrType.RowTitle.fontWeight),
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "$usedMinutes of ${app.limitMinutes} min",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(12.dp))
        if (locked) {
            // The design puts "Earn +10m" here. That flow is Figma 21 to 24
            // and does not exist, so the row says what is true instead.
            AsrPill("LOCKED")
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${app.limitMinutes - usedMinutes}m left",
                    style = AsrType.Label.copy(fontWeight = AsrType.RowTitle.fontWeight),
                    color = AsrColors.TextPrimary,
                )
                Spacer(Modifier.height(7.dp))
                Box(modifier = Modifier.width(90.dp)) {
                    ProgressBar(
                        fraction = usedMinutes.toFloat() / app.limitMinutes,
                        height = 6.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowIcon(app: PactApp, icon: ImageBitmap?) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(AsrColors.Background)
            .border(1.dp, AsrColors.FieldBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                app.label.take(1).uppercase(),
                style = AsrType.Button.copy(fontSize = 14.sp),
                color = AsrColors.Accent,
            )
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, height: Dp) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(AsrColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(shape)
                .background(AsrColors.Accent),
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DashboardPreview() {
    AsrTheme {
        // Composed from its parts: the screen itself owns a ViewModel that
        // would read the preview host's usage statistics.
        Column(
            Modifier.fillMaxSize().background(AsrColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChallengeCard(
                ChallengeProgress(4, 14, 10, 29, false),
                protected = true,
                onProtectionLost = {},
            )
            UsageRow(PactApp("com.instagram.android", "Instagram", 15), null, 8)
            UsageRow(PactApp("com.zhiliaoapp.musically", "TikTok", 20), null, 20)
        }
    }
}
