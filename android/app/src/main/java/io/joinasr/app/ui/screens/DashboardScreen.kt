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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import io.joinasr.app.daysLabel
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.challenge.ChallengeProgress
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.permissions.PermissionState
import io.joinasr.app.ui.DashboardViewModel
import io.joinasr.app.ui.components.AsrIcons
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.components.AsrTextLink
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
    /**
     * Null when no challenge is running, which is now an ordinary state
     * rather than a reason to hide the app. Somebody who only ever agreed to
     * witness a friend has no pact and never will, and sending them through
     * a setup flow to reach the one screen they came for was this app
     * asking the wrong person for six answers.
     */
    pact: Pact?,
    /** Starts the setup flow, on purpose and from here. */
    onStartChallenge: () -> Unit,
    /** Opens Figma 27. Only reachable while a grant is actually missing. */
    onProtectionLost: () -> Unit,
    /** Opens the battery and manufacturer settings that keep the loop alive. */
    onFixProtection: () -> Unit,
    /** Opens Figma 19. */
    onNotifications: () -> Unit,
    unreadNotifications: Int,
    /** Bonus minutes won today, per package. Raises the row's allowance. */
    earnedMinutes: Map<String, Int>,
    /** Opens Figma 21 for one app, from the "Earn +10m" button on its row. */
    onEarnTime: (PactApp) -> Unit,
    /** Opens the picker that brings one more app under a limit, mid-challenge. */
    onAddApp: () -> Unit,
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
        for (app in pact?.apps.orEmpty()) {
            InstalledApps.icon(context, app.packageName)?.let {
                loaded[app.packageName] = it
                value = loaded.toMap()
            }
        }
    }

    val now = System.currentTimeMillis()
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greetingFor(hour),
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (pact != null) "Stay in control." else "Ready when you are.",
                    style = AsrType.display(28),
                    color = AsrColors.TextPrimary,
                )
            }
            // Figma 19 has a back chevron, so it is pushed from somewhere,
            // and the file never draws the somewhere. This is it: the one
            // place a person is already looking every day.
            Bell(unread = unreadNotifications, onClick = onNotifications)
        }

        Spacer(Modifier.height(22.dp))
        // Protected means all three of these, not one: the permissions are
        // there, the loop is running, and the system is not dropping the
        // block screen. Anything less and this app is not doing its job,
        // which is a thing to say out loud rather than paper over.
        //
        // With no pact there is nothing to protect, and a NOT PROTECTED pill
        // over an empty dashboard would be an alarm about nothing.
        val working = pact == null ||
            (permissions.requiredGranted && state.loopLive && !state.blockDropped)

        if (pact == null) {
            NoChallengeCard(onStart = onStartChallenge)
            Spacer(Modifier.height(20.dp))
            WhatAChallengeIs()
            Spacer(Modifier.height(28.dp))
            return@Column
        }

        val progress = ChallengeProgress.of(pact.startedAtMillis, pact.durationDays, now)
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
                earnedMinutes = earnedMinutes[app.packageName] ?: 0,
                onEarnTime = { onEarnTime(app) },
            )
            Spacer(Modifier.height(10.dp))
        }
        // The one thing a running challenge can still take: one more app.
        // Not in the Figma frame, which drew the limits as fixed for the
        // duration; they still are, and so is the list, except in this
        // direction. Quiet on purpose -- an outline where the rows are
        // filled -- because it is an option, not the next step.
        AddAppRow(onClick = onAddApp)
        Spacer(Modifier.height(10.dp))

        if (!working) {
            Spacer(Modifier.height(10.dp))
            Column(
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
                            "Android refused to show the block screen just now, so the " +
                                "block was drawn over the app instead. Some phones need one " +
                                "more switch for it to work properly: on Xiaomi, allow " +
                                "\"Display pop-up windows while running in the background\"."

                        else ->
                            "Protection is not running. The phone stopped the background " +
                                "service; opening this screen starts it again, and the " +
                                "settings below stop it from happening."
                    },
                    style = AsrType.Legal,
                    color = AsrColors.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                AsrTextLink(text = "Keep protection running", onClick = onFixProtection)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Figma 13's notification button: a 40dp circle with the bell in it, and the
 * unread count in a badge over its top corner.
 *
 * The count rather than a dot, because the frame draws a number and a number
 * is worth more: two is a glance, a dot is a trip. Above nine it says 9+,
 * which is the point at which the exact figure stops changing what anybody
 * does about it.
 */
/**
 * The dashboard with no challenge on it.
 *
 * Not an error and not a nag. Somebody who signed up to witness a friend
 * lives here permanently and is not doing anything wrong; the button is an
 * offer, and the rest of the app — witnesses, notifications, profile —
 * works around it without one.
 */
@Composable
private fun NoChallengeCard(onStart: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Text(
            "NO CHALLENGE RUNNING",
            style = AsrType.Eyebrow.copy(fontSize = 11.sp),
            color = AsrColors.TextTertiary,
        )
        Spacer(Modifier.height(14.dp))
        Text("Nothing is limited.", style = AsrType.display(28), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Start a challenge to pick the apps, set a daily limit for each, and " +
                "name who gets told if you break it.",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        AsrPrimaryButton(text = "Start a challenge", onClick = onStart)
    }
}

/** What starting one actually involves, before anybody commits to six screens. */
@Composable
private fun WhatAChallengeIs() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(17.dp),
    ) {
        Text(
            "What it takes",
            style = AsrType.Field.copy(fontSize = 15.sp),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        for (line in listOf(
            "Two permissions, so the app can see and block",
            "The apps you want limited, and how long each gets",
            "At least one witness, who is told if it breaks",
        )) {
            Row(verticalAlignment = Alignment.Top) {
                Text("·", style = AsrType.display(16), color = AsrColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    line,
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(9.dp))
        }
        Text(
            "Nothing is limited until you finish, and the limits lock once it starts.",
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )
    }
}

@Composable
private fun Bell(unread: Int, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(AsrColors.Surface)
                .border(1.dp, AsrColors.FieldBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AsrIcons.Bell(
                colour = if (unread > 0) AsrColors.Accent else AsrColors.TextSecondary,
                size = 22.dp,
            )
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AsrColors.Accent)
                    .border(2.dp, AsrColors.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unread > 9) "9+" else unread.toString(),
                    style = AsrType.Label.copy(
                        fontSize = 9.sp,
                        fontWeight = AsrType.RowTitle.fontWeight,
                    ),
                    color = AsrColors.OnAccent,
                )
            }
        }
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
                    progress.isComplete -> "${daysLabel(progress.totalDays)} done"
                    progress.daysLeft == 1 -> "1 day left"
                    else -> "${daysLabel(progress.daysLeft)} left"
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
private fun UsageRow(
    app: PactApp,
    icon: ImageBitmap?,
    usedMinutes: Int,
    earnedMinutes: Int,
    onEarnTime: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    // Today's allowance, not the pact's number. Bonus minutes are already
    // raising the real limit inside the enforcement loop, and a row still
    // reading LOCKED while the app opens fine would make the dashboard the
    // liar of the two.
    val allowance = app.limitMinutes + earnedMinutes
    val locked = usedMinutes >= allowance
    val capped = earnedMinutes >= EarnRules.DAILY_CAP_MINUTES
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
        RowIcon(app = app, icon = icon, locked = locked)
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
                "$usedMinutes of $allowance min",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(12.dp))
        when {
            // The design's own answer to a spent limit: not a label saying
            // so -- the lock on the icon already says it -- but the one
            // thing there is left to do about it.
            locked && !capped -> EarnButton(onClick = onEarnTime)

            // Nothing left to offer. All of today's bonus is already spent,
            // and a button that refused every press would be worse than a
            // row that says the day is done.
            locked -> AsrPill("LOCKED")

            else -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${allowance - usedMinutes}m left",
                    style = AsrType.Label.copy(fontWeight = AsrType.RowTitle.fontWeight),
                    color = AsrColors.TextPrimary,
                )
                Spacer(Modifier.height(7.dp))
                Box(modifier = Modifier.width(90.dp)) {
                    ProgressBar(
                        fraction = usedMinutes.toFloat() / allowance.coerceAtLeast(1),
                        height = 6.dp,
                    )
                }
            }
        }
    }
}

/**
 * The row that adds an app, drawn to the same measure as the rows above it
 * so the list reads as one list with one open slot at the end.
 */
@Composable
private fun AddAppRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = AsrType.display(22), color = AsrColors.Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Add an app",
                style = AsrType.Field.copy(fontWeight = AsrType.RowTitle.fontWeight),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Counts from today. Stays until the challenge ends.",
                style = AsrType.Label.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The green pill from Figma 13's TikTok row. */
@Composable
private fun EarnButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(96.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(AsrColors.Accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Earn +${EarnRules.REWARD_MINUTES}m",
            style = AsrType.Label.copy(
                fontSize = 12.sp,
                fontWeight = AsrType.RowTitle.fontWeight,
            ),
            color = AsrColors.OnAccent,
        )
    }
}

@Composable
private fun RowIcon(app: PactApp, icon: ImageBitmap?, locked: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(contentAlignment = Alignment.BottomEnd) {
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
        if (locked) LockBadge(Modifier.offset(x = 3.dp, y = 3.dp))
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(AsrColors.Background)
            .border(1.dp, AsrColors.Accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AsrIcons.Lock(colour = AsrColors.Accent, size = 10.dp)
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
            UsageRow(
                app = PactApp("com.instagram.android", "Instagram", 15),
                icon = null,
                usedMinutes = 8,
                earnedMinutes = 0,
                onEarnTime = {},
            )
            UsageRow(
                app = PactApp("com.zhiliaoapp.musically", "TikTok", 20),
                icon = null,
                usedMinutes = 20,
                earnedMinutes = 0,
                onEarnTime = {},
            )
            AddAppRow(onClick = {})
        }
    }
}
