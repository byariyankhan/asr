package io.joinasr.app.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.earn.EarnActivity
import io.joinasr.app.earn.EarnIcon
import io.joinasr.app.earn.EarnOption
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.earn.CameraSpec
import io.joinasr.app.earn.PoseCameraView
import io.joinasr.app.earn.PoseJudge
import io.joinasr.app.earn.cameraSpec
import io.joinasr.app.earn.earnOptionFor
import io.joinasr.app.earn.earnOptions
import io.joinasr.app.earn.rememberRepFeedback
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.ui.components.AsrAppIcon
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrIcons
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.util.Locale

/**
 * Figma 21 — Earn Time / Choose Activity (node 131:2), relaid as a grid.
 *
 * Reached from the block screen, which is the only place it makes sense: the
 * moment somebody wants more time is the moment they have run out.
 *
 * The three activities sit in one row of tiles, each a glyph, a name and the
 * price; the ask, the rule, and the privacy line are on a sheet that opens
 * when a tile is tapped, so the screen stays the same height when a fourth
 * or fifth activity is added. What is listed comes from [earnOptions]; an
 * activity this phone cannot run (no step counter, no camera) is still
 * shown, dimmed, and its sheet says why, so the row a person heard about is
 * never simply missing.
 */
@Composable
fun ChooseActivityScreen(
    app: PactApp,
    earnedSoFar: Int,
    options: List<EarnOption>,
    onBack: () -> Unit,
    onStart: (EarnOption) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val capped = earnedSoFar >= EarnRules.DAILY_CAP_MINUTES
    var open by remember { mutableStateOf<EarnOption?>(null) }

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
        Text("EARN TIME", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Choose how to earn.", style = AsrType.display(34), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Complete one activity to unlock ${EarnRules.REWARD_MINUTES} more minutes.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        TargetApp(app = app, earnedSoFar = earnedSoFar)

        Spacer(Modifier.height(24.dp))
        Text("Choose an activity", style = AsrType.display(20), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            if (capped) {
                "Today's ${EarnRules.DAILY_CAP_MINUTES} bonus minutes for ${app.label} are used up."
            } else {
                "Tap one to see how it works."
            },
            style = AsrType.Legal.copy(fontSize = 13.sp),
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(14.dp))
        ActivityGrid(options = options, capped = capped, onOpen = { open = it })

        errorMessage?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = AsrType.Legal, color = AsrColors.Error)
        }

        Spacer(Modifier.height(18.dp))
        RewardNote(
            title = "Bonus time does not change your daily limit.",
            body = "It adds ${EarnRules.REWARD_MINUTES} minutes to ${app.label} for today only.",
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Not now",
            style = AsrType.Label.copy(fontSize = 14.sp),
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

    open?.let { option ->
        ActivitySheet(
            option = option,
            app = app,
            capped = capped,
            onDismiss = { open = null },
            onStart = {
                open = null
                onStart(option)
            },
        )
    }
}

/**
 * Three tiles to a row, rows as needed. A short last row is padded with
 * empty weights so its tiles keep the width of the others rather than
 * stretching to fill.
 */
@Composable
private fun ActivityGrid(
    options: List<EarnOption>,
    capped: Boolean,
    onOpen: (EarnOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(GRID_COLUMNS).forEach { row ->
            // Sized to the tallest tile, so a longer name on one does not
            // leave its neighbours shorter.
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { option ->
                    ActivityTile(
                        option = option,
                        dimmed = capped || !option.available,
                        onClick = { onOpen(option) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val GRID_COLUMNS = 3

/**
 * One activity, at a glance: icon, name, reward. Always tappable: a dimmed
 * tile opens the same sheet, where the reason it cannot be started is
 * written out, instead of a dead square the person has to guess about.
 * Nothing is recommended over anything else; the founder took the badge
 * off, and the tiles got shorter with it.
 */
@Composable
private fun ActivityTile(
    option: EarnOption,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val ink = if (dimmed) AsrColors.TextTertiary else AsrColors.TextPrimary
    Column(
        modifier = modifier
            .clip(shape)
            .background(AsrColors.Surface)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (dimmed) AsrColors.Field else AsrColors.AccentMuted),
            contentAlignment = Alignment.Center,
        ) {
            EarnIconView(
                icon = option.icon,
                colour = if (dimmed) AsrColors.TextTertiary else AsrColors.Accent,
                size = 22.dp,
                moving = !dimmed,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            option.name,
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "+${EarnRules.REWARD_MINUTES}m",
            style = AsrType.Legal.copy(fontSize = 11.sp),
            color = if (dimmed) AsrColors.TextTertiary else AsrColors.Accent,
        )
    }
}

/**
 * The drawing for an [EarnIcon]; the only place the enum meets Compose.
 *
 * [moving] plays the figure through its motion, slowly and forever, while
 * it is on screen: a step, a phone set down, a push-up. It stops for a
 * dimmed tile, and everywhere when the system's animation scale is off,
 * which is the setting people who cannot stand motion use. A still icon
 * is caught at the pose that reads best.
 */
@Composable
private fun EarnIconView(icon: EarnIcon, colour: Color, size: Dp, moving: Boolean) {
    val still = when (icon) {
        EarnIcon.WALK -> 0.9f
        EarnIcon.FOCUS -> 1f
        EarnIcon.PUSH_UPS -> 0f
        EarnIcon.PLANK -> 0f
        EarnIcon.WALL_SIT -> 0f
        EarnIcon.RUN -> 1f
        EarnIcon.STAIRS -> 0.8f
        EarnIcon.MEDITATION -> 0.4f
        EarnIcon.RIDE -> 0.5f
    }
    val period = when (icon) {
        EarnIcon.WALK -> 620
        EarnIcon.FOCUS -> 1500
        EarnIcon.PUSH_UPS -> 1100
        EarnIcon.PLANK -> 1800
        EarnIcon.WALL_SIT -> 260
        EarnIcon.RUN -> 420
        EarnIcon.STAIRS -> 700
        EarnIcon.MEDITATION -> 2400
        EarnIcon.RIDE -> 900
    }
    val context = LocalContext.current
    val animationsOn = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val phase = if (moving && animationsOn) {
        rememberInfiniteTransition(label = "earn-icon").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(period, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase",
        ).value
    } else {
        still
    }
    when (icon) {
        EarnIcon.WALK -> AsrIcons.Walk(colour, phase, size)
        EarnIcon.FOCUS -> AsrIcons.Focus(colour, phase, size)
        EarnIcon.PUSH_UPS -> AsrIcons.PushUps(colour, phase, size)
        EarnIcon.PLANK -> AsrIcons.Plank(colour, phase, size)
        EarnIcon.WALL_SIT -> AsrIcons.WallSit(colour, phase, size)
        EarnIcon.RUN -> AsrIcons.Run(colour, phase, size)
        EarnIcon.STAIRS -> AsrIcons.Stairs(colour, phase, size)
        EarnIcon.MEDITATION -> AsrIcons.Meditation(colour, phase, size)
        EarnIcon.RIDE -> AsrIcons.Ride(colour, phase, size)
    }
}

/**
 * The detail behind a tile: the whole ask, the price, how it is done, how
 * it is counted, what is collected, and the Start button. The button is
 * the only way to start; the sheet closes before the activity opens so the
 * chooser is clean when the person comes back to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(
    option: EarnOption,
    app: PactApp,
    capped: Boolean,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = AsrColors.Surface,
        contentColor = AsrColors.TextPrimary,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AsrColors.AccentMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    EarnIconView(icon = option.icon, colour = AsrColors.Accent, size = 28.dp, moving = option.available)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.title, style = AsrType.display(22), color = AsrColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        option.target,
                        style = AsrType.Label.copy(fontSize = 13.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            val rewardShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsrColors.AccentMuted, rewardShape)
                    .border(1.dp, AsrColors.AccentBorder, rewardShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("REWARD", style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = AsrColors.Accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "+${EarnRules.REWARD_MINUTES} minutes · for ${app.label}, today only",
                        style = AsrType.Field.copy(fontSize = 14.sp),
                        color = AsrColors.TextPrimary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SheetSection("HOW IT WORKS", option.explanation)
            Spacer(Modifier.height(16.dp))
            SheetSection("HOW IT IS COUNTED", option.verification)
            option.privacy?.let {
                Spacer(Modifier.height(16.dp))
                SheetSection("PRIVACY", it)
            }

            val blocker = option.unavailableReason ?: if (capped) {
                "Today's ${EarnRules.DAILY_CAP_MINUTES} bonus minutes for ${app.label} are used up. " +
                    "Tomorrow starts fresh."
            } else {
                null
            }
            blocker?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, style = AsrType.Label.copy(fontSize = 13.sp), color = AsrColors.Warning)
            }

            Spacer(Modifier.height(22.dp))
            AsrPrimaryButton(
                text = "Start",
                onClick = onStart,
                enabled = option.available && !capped,
            )
        }
    }
}

@Composable
private fun SheetSection(heading: String, body: String) {
    Text(heading, style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = AsrColors.TextTertiary)
    Spacer(Modifier.height(6.dp))
    Text(body, style = AsrType.Field.copy(fontSize = 14.sp), color = AsrColors.TextSecondary)
}

/**
 * Figma 22 — Permission / Activity Tracking — First Walk (node 119:73).
 *
 * Asked the first time somebody chooses a walk (or a run, or a climb: the
 * same step counter, the same permission) and never at launch, which is
 * the difference between a permission a person understands and one they
 * refuse on principle.
 */
@Composable
fun ActivityTrackingScreen(
    type: String = EarnRules.WALK,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Text("EARN TIME", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(
            when (type) {
                EarnRules.RUN -> "Track your run."
                EarnRules.STAIRS -> "Track your climb."
                EarnRules.RIDE -> "Tell a ride from a run."
                else -> "Track your walk."
            },
            style = AsrType.display(38),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (type == EarnRules.RIDE) {
                "A ride reads your steps too, so that running at a bicycle's speed is not counted " +
                    "as cycling. Location is asked for next."
            } else {
                "Only requested when you choose an activity on foot to earn extra app time."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.SurfaceRaised, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    format(EarnRules.WALK_STEPS),
                    style = AsrType.display(38),
                    color = AsrColors.Accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "STEPS",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "≈ ${"%.0f".format(Locale.US, EarnRules.kilometresFor(EarnRules.WALK_STEPS))} km walking goal",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Distance is estimated from steps. No GPS is required.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Field, RoundedCornerShape(20.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(17.dp),
        ) {
            Text("Motion & step access", style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Used only while a walking reward is active. Tracking stops when the " +
                    "activity ends.",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "✓  No location or GPS access",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "✓  No all-day movement history required",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
        }

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(text = "Allow activity tracking", onClick = onAllow)
        Spacer(Modifier.height(18.dp))
        Text(
            "Not now",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The camera's counterpart to Figma 22, in its layout.
 *
 * Asked the first time somebody chooses a camera activity and never at launch. The
 * promises on it are the ones enforced in earn/PoseCamera.kt: frames go to
 * a model on the phone and are dropped, nothing is written, nothing is
 * sent, and the camera is released with the screen.
 */
@Composable
fun CameraAccessScreen(
    spec: CameraSpec,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    /**
     * True once Android has stopped showing the dialog: the button then
     * says where it is really going, the app's page in Settings.
     */
    openSettings: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
        Text("EARN TIME", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(spec.permissionTitle, style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (openSettings) {
                "Camera access was refused. Allow it on Asr's page in Settings to count ${spec.noun}."
            } else {
                "Only requested when you choose a camera activity to earn extra app time."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.SurfaceRaised, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    spec.targetShown.first,
                    style = AsrType.display(38),
                    color = AsrColors.Accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    spec.targetShown.second,
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                spec.placementLine,
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                spec.verification,
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Field, RoundedCornerShape(20.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(17.dp),
        ) {
            Text("Camera access", style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Used only while the camera screen is open. The camera switches " +
                    "off the moment you leave it.",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "✓  No photo or video is ever saved",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "✓  Nothing from the camera leaves your phone",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
        }

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(text = if (openSettings) "Open Settings" else "Allow camera access", onClick = onAllow)
        Spacer(Modifier.height(18.dp))
        Text(
            "Not now",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Location, asked the first time somebody chooses cycling and never at
 * launch, in the camera screen's layout. The promises on it are the ones
 * enforced in earn/RideService.kt: each fix is compared with the last and
 * dropped, no route is written, nothing about where the phone was is
 * sent, and GPS is on for the ride only.
 */
@Composable
fun LocationAccessScreen(
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    openSettings: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
        Text("EARN TIME", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text("Measure your ride.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (openSettings) {
                "Precise location was refused. Allow it on Asr's page in Settings to measure a ride; " +
                    "approximate location cannot."
            } else {
                "Only requested when you choose cycling to earn extra app time."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.SurfaceRaised, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.0f".format(Locale.US, EarnRules.RIDE_METRES / 1000.0),
                    style = AsrType.display(38),
                    color = AsrColors.Accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "KM BY BIKE",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Phone in your pocket, GPS on for the ride",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your phone compares each GPS fix with the last for speed and distance, counts " +
                    "the metres at a cycling pace, and drops the fix. No route is kept.",
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Field, RoundedCornerShape(20.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(17.dp),
        ) {
            Text("Location access", style = AsrType.RowTitle, color = AsrColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Used only while a ride is running, with a notification saying so. GPS switches " +
                    "off the moment the ride ends.",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "✓  No route or place is ever saved",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "✓  Nothing about where you went leaves your phone",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.Accent,
            )
        }

        Spacer(Modifier.height(26.dp))
        AsrPrimaryButton(text = if (openSettings) "Open Settings" else "Allow location access", onClick = onAllow)
        Spacer(Modifier.height(18.dp))
        Text(
            "Not now",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Figma 23 — Earn Time / Activity Progress — Walk (node 133:2).
 *
 * The same screen serves a focus session, because the design of the two is
 * the same shape. Focus progress is display-only; the foreground service
 * measures the locked interval and applies the reward.
 */
@Composable
fun ActivityProgressScreen(
    activity: EarnActivity,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val walk = activity.isWalk
    val focus = activity.isFocus
    val run = activity.type == EarnRules.RUN
    val stairs = activity.type == EarnRules.STAIRS
    val ride = activity.isRide
    // Display only. No UI clock can complete an activity or award minutes.
    val focusRemaining by produceState(
        initialValue = activity.target * 60,
        key1 = activity.id,
        key2 = activity.focusLockedSinceElapsed,
    ) {
        while (focus) {
            val elapsed = activity.focusLockedSinceElapsed?.let {
                (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)
            } ?: 0L
            value = ((activity.target * 60_000L - elapsed).coerceAtLeast(0L) + 999L)
                .div(1_000L).toInt()
            delay(250)
        }
    }
    val fraction = if (!focus) activity.fraction else
        (1f - focusRemaining.toFloat() / (activity.target * 60).coerceAtLeast(1)).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()

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
        Text("EARN TIME", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                walk -> "Keep walking."
                run -> "Go for a run."
                stairs -> "Take the stairs."
                ride -> "Go for a ride."
                else -> "Put your phone down for ${activity.target} minutes."
            },
            style = AsrType.display(36),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                walk ->
                    "Reach ${"%.0f".format(Locale.US, EarnRules.kilometresFor(activity.target))} km " +
                        "to earn ${activity.rewardMinutes} more minutes for ${activity.appLabel}."
                run ->
                    "${format(activity.target)} steps at a running pace earn " +
                        "${activity.rewardMinutes} more minutes for ${activity.appLabel}. Walking does not count."
                stairs ->
                    "${activity.target} floors on foot earn ${activity.rewardMinutes} more minutes " +
                        "for ${activity.appLabel}. The lift does not count."
                ride ->
                    "${"%.0f".format(Locale.US, activity.target / 1000.0)} km at a cycling speed earn " +
                        "${activity.rewardMinutes} more minutes for ${activity.appLabel}. Walking the bike " +
                        "and a car do not count."
                else -> "Keep your phone locked and spend some time in the real world."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        RewardContext(activity)

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "LIVE PROGRESS",
                        style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                        color = AsrColors.Accent,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (activity.baselineSteps < 0 && walk) {
                            "—"
                        } else if (focus) {
                            String.format(Locale.US, "%02d:%02d", focusRemaining / 60, focusRemaining % 60)
                        } else if (ride) {
                            String.format(Locale.US, "%.2f", activity.progress / 1000.0)
                        } else {
                            format(activity.progress)
                        },
                        style = AsrType.display(48),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            walk -> "of ${format(activity.target)} steps"
                            run -> "of ${format(activity.target)} running steps"
                            stairs -> "of ${activity.target} floors"
                            ride -> "of ${"%.1f".format(Locale.US, activity.target / 1000.0)} km by bike"
                            activity.focusLockedSinceElapsed == null -> "Lock your phone to start the timer."
                            else -> "remaining with your phone locked"
                        },
                        style = AsrType.Label.copy(fontSize = 14.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
                SmallPill("$percent%", AsrColors.Accent, AsrColors.AccentMuted)
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AsrColors.Track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AsrColors.Accent),
                )
            }

            if (walk) {
                Spacer(Modifier.height(16.dp))
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Estimated distance",
                            style = AsrType.Legal.copy(fontSize = 12.sp),
                            color = AsrColors.TextTertiary,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "~%.1f / %.1f km".format(
                                Locale.US,
                                EarnRules.kilometresFor(activity.progress),
                                EarnRules.kilometresFor(activity.target),
                            ),
                            style = AsrType.CardTitle,
                            color = AsrColors.TextPrimary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        TrackingStatus(type = activity.type)

        Spacer(Modifier.height(16.dp))
        RewardNote(
            title = when {
                walk -> "${format(activity.remaining)} steps to go"
                run -> "${format(activity.remaining)} running steps to go"
                stairs -> "${activity.remaining} floors to go"
                ride -> "${"%.1f".format(Locale.US, activity.remaining / 1000.0)} km to go"
                else -> "If you unlock your phone before the ${activity.target} minutes are complete, the timer will reset."
            },
            body = "Finish and ${activity.appLabel} gets +${activity.rewardMinutes} minutes today.",
        )

        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AsrColors.SurfaceSunken)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onEnd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "End activity",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * A camera activity in progress: the camera with the count on it, and the
 * rest below. Push-ups, a plank, a wall sit: the same screen, told what it
 * is running by a [CameraSpec], which brings the judge that watches the
 * poses and the words for what it counts.
 *
 * The picture comes first because the person is on the floor looking up at
 * it from half a metre, or across the room from a plank. The number sits
 * on the picture at a size that reads from there, the next thing to do
 * runs along its bottom edge, and the frame's colour says what the judge
 * sees: grey for nobody, green while counting, a green wash while working
 * (the bottom of a push-up, a plank being held), amber for a body that is
 * not in position. Counted units tick and pulse ([RepFeedback]), every rep
 * or every few seconds, because at the bottom of a push-up nobody is
 * reading.
 *
 * The judge belongs to this screen and starts from nothing each time it
 * opens; the activity carries the number, through [onCounted], so stepping
 * away and coming back resumes at the same count. Compose never awards the
 * minutes: the view model does, on the last unit, the same way a walk ends.
 *
 * A minute without a body and the camera is put down. The permission
 * screen promises the lens is open only while the set is on, and a phone
 * forgotten on the floor must not be a lit, watching one.
 */
@Composable
fun CameraActivityScreen(
    activity: EarnActivity,
    spec: CameraSpec,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onCounted: (Int) -> Unit,
    /** A run that has to be done in one go was broken off, or this screen was opened on one: the count goes to zero. */
    onStartedOver: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Bumped to start the camera and the judge over: after a failure the
    // person asked to retry, or after the pause for absence.
    var attempt by remember(activity.id) { mutableIntStateOf(0) }
    val judge = remember(activity.id, attempt) { spec.newJudge() }
    // A run that must be continuous ends with the screen: leaving it, or
    // the camera stopping and being started again, interrupted the
    // sitting, and the count goes to zero on the way out as well as on
    // the way back in, so the dashboard never offers to continue one.
    LaunchedEffect(judge) {
        if (spec.continuous && activity.progress > 0) onStartedOver()
    }
    val latestStartedOver by rememberUpdatedState(onStartedOver)
    DisposableEffect(activity.id, spec.continuous) {
        onDispose { if (spec.continuous) latestStartedOver() }
    }
    var phase by remember(activity.id, attempt) { mutableStateOf(PoseJudge.Phase.NO_BODY) }
    /** True once the first frame has been judged: before that nothing is looking. */
    var started by remember(activity.id, attempt) { mutableStateOf(false) }
    var cameraProblem by remember(activity.id, attempt) { mutableStateOf<String?>(null) }
    var paused by remember(activity.id) { mutableStateOf(false) }
    var confirmingGiveUp by remember(activity.id) { mutableStateOf(false) }
    val feedback = rememberRepFeedback()
    val scope = rememberCoroutineScope()
    val flash = remember(activity.id) { Animatable(0f) }
    val pop = remember(activity.id) { Animatable(1f) }
    // Re-read on every phase change: the judge's coaching is a function of
    // what it last saw, and the phase is what it last settled on.
    val coaching = remember(judge, phase, started) { judge.coaching(started) }

    // A minute of nobody, and the camera goes. The count is the activity's,
    // so nothing is lost; the tap that resumes starts a fresh attempt,
    // camera and judge both.
    LaunchedEffect(activity.id, attempt, started, phase, paused) {
        if (paused || !started || phase != PoseJudge.Phase.NO_BODY) return@LaunchedEffect
        delay(ABSENCE_PAUSE_MILLIS)
        paused = true
    }

    val counting = started && !paused && cameraProblem == null &&
        (phase == PoseJudge.Phase.READY || phase == PoseJudge.Phase.WORKING)
    val frameColour by animateColorAsState(
        targetValue = when {
            paused || cameraProblem != null || !started -> AsrColors.FieldBorder
            phase == PoseJudge.Phase.NOT_IN_POSITION -> AsrColors.Warning
            counting -> AsrColors.Accent
            else -> AsrColors.FieldBorder
        },
        label = "frame",
    )
    val downWash by animateFloatAsState(
        targetValue = if (counting && phase == PoseJudge.Phase.WORKING) 0.22f else 0f,
        label = "down",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AsrBackChevron(onBack)

        Spacer(Modifier.height(14.dp))
        val frame = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Portrait, like the sensor: a landscape crop of a portrait
                // camera cut the face off at the bottom of every rep.
                .aspectRatio(3f / 4f)
                .clip(frame)
                .background(AsrColors.Surface)
                .border(if (counting) 3.dp else 1.dp, frameColour, frame),
        ) {
            val problem = cameraProblem
            when {
                paused -> PausedPanel(
                    onResume = {
                        paused = false
                        attempt++
                    },
                )
                problem != null -> CameraProblemPanel(problem, onRetry = { attempt++ })
                else -> {
                    key(attempt) {
                        PoseCameraView(
                            onPose = { pose, at ->
                                if (!started) started = true
                                val before = phase
                                val counted = judge.observe(pose, at)
                                val now = judge.phase
                                if (before == PoseJudge.Phase.NO_BODY && now != PoseJudge.Phase.NO_BODY) {
                                    feedback.found()
                                }
                                if (spec.continuous && judge.brokeOff) {
                                    feedback.found()
                                    onStartedOver()
                                }
                                if (counted > 0) {
                                    // Every rep; every few seconds of a hold.
                                    val reached = activity.progress + counted
                                    if (reached % spec.tickEvery == 0 || reached >= activity.target) {
                                        feedback.rep()
                                        scope.launch {
                                            flash.snapTo(1f)
                                            flash.animateTo(0f, tween(320))
                                        }
                                        scope.launch {
                                            pop.snapTo(1.3f)
                                            pop.animateTo(1f, tween(260))
                                        }
                                    }
                                    onCounted(counted)
                                }
                                phase = now
                            },
                            onError = { cameraProblem = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // The bottom of every rep, acknowledged as it happens,
                    // and a flash for the rep itself.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsrColors.Accent.copy(alpha = downWash)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsrColors.Accent.copy(alpha = flash.value * 0.55f)),
                    )
                    // How far along, at a glance: dots for a handful of
                    // reps, a bar for a count too long to dot.
                    if (activity.target <= 12) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(activity.target) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index < activity.progress) AsrColors.Accent
                                            else Color.White.copy(alpha = 0.35f),
                                        ),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.3f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(activity.fraction)
                                    .height(6.dp)
                                    .background(AsrColors.Accent),
                            )
                        }
                    }
                    // The count, at a size that reads from a plank. On its
                    // own dark panel so it holds over a bright face.
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 30.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            spec.format(activity.progress),
                            style = AsrType.display(112),
                            color = AsrColors.TextPrimary,
                            modifier = Modifier.graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            },
                        )
                        Text(
                            "OF ${spec.format(activity.target)}",
                            style = AsrType.Eyebrow,
                            color = AsrColors.TextPrimary.copy(alpha = 0.85f),
                        )
                    }
                    // The next thing to do, on the picture itself.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "●",
                                style = AsrType.Field.copy(fontSize = 14.sp),
                                color = if (started) AsrColors.Accent else AsrColors.TextTertiary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(coaching.first, style = AsrType.RowTitle, color = AsrColors.TextPrimary)
                        }
                        if (coaching.second.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                coaching.second,
                                style = AsrType.Label.copy(fontSize = 13.sp),
                                color = AsrColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        // For the person still standing, phone in hand. No heading: the
        // strip on the picture says what to do next, the reward card says
        // what it is for, and the placement card says where the phone goes.
        // A title over all three said the same thing a third time.
        Spacer(Modifier.height(18.dp))
        RewardContext(activity)

        Spacer(Modifier.height(14.dp))
        PhonePlacement(spec)

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "LIVE PROGRESS",
                        style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                        color = AsrColors.Accent,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        spec.format(activity.progress),
                        style = AsrType.display(48),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        spec.ofTarget,
                        style = AsrType.Label.copy(fontSize = 14.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
                SmallPill("${(activity.fraction * 100).toInt()}%", AsrColors.Accent, AsrColors.AccentMuted)
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AsrColors.Track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(activity.fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AsrColors.Accent),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        RewardNote(
            title = spec.toGo(activity.remaining),
            body = "Finish and ${activity.appLabel} gets +${activity.rewardMinutes} minutes today.",
        )

        // Two exits that say what they do. Back keeps the count; giving
        // up throws it away, and asks once before it does.
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AsrColors.SurfaceSunken)
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Finish later",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            if (activity.progress > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    if (spec.continuous) "Leaving starts the ${spec.session} over." else "Your ${activity.progress} ${spec.noun} are saved.",
                    style = AsrType.Legal.copy(fontSize = 12.sp),
                    color = AsrColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (confirmingGiveUp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AsrColors.DangerMuted, RoundedCornerShape(16.dp))
                    .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(16.dp))
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Give up this ${spec.session}? Your ${spec.format(activity.progress)} ${if (spec.continuous) "" else spec.noun + " "}will not count.",
                    style = AsrType.Label.copy(fontSize = 13.sp),
                    color = AsrColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        "Keep going",
                        style = AsrType.Label.copy(fontSize = 14.sp),
                        color = AsrColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { confirmingGiveUp = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Yes, give up",
                        style = AsrType.Label.copy(fontSize = 14.sp),
                        color = AsrColors.Danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onEnd)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            Text(
                "Give up this ${spec.session}",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {
                        if (activity.progress > 0) confirmingGiveUp = true else onEnd()
                    }
                    .padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** How long the camera waits for a body before putting itself down. */
private const val ABSENCE_PAUSE_MILLIS = 60_000L

/** The frame after a minute of nobody: the lens is off, the count is kept. */
@Composable
private fun PausedPanel(onResume: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onResume)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Paused", style = AsrType.display(28), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Nobody was in the picture for a minute, so the camera was switched off. " +
                "What you have done is saved.",
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text("Tap to continue", style = AsrType.RowTitle, color = AsrColors.Accent)
    }
}

/** The frame when the camera or the model could not start: one plain line, the cause under it, a way back. */
@Composable
private fun CameraProblemPanel(problem: String, onRetry: () -> Unit) {
    val headline = problem.substringBefore(". ").let { if (it == problem) problem else "$it." }
    val detail = problem.removePrefix(headline).trim()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            headline,
            style = AsrType.Field.copy(fontSize = 16.sp),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = AsrType.Legal,
                color = AsrColors.TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Try again",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.Accent,
            modifier = Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(AsrColors.AccentMuted)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/**
 * Where the phone goes, for the person reading with it in their hand. The
 * steps are the spec's: the floor under the face for push-ups, propped
 * across the room for a plank.
 */
@Composable
private fun PhonePlacement(spec: CameraSpec) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceRaised, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
    ) {
        Text(
            "WHERE TO PUT YOUR PHONE",
            style = AsrType.Eyebrow.copy(fontSize = 10.sp),
            color = AsrColors.Accent,
        )
        Spacer(Modifier.height(10.dp))
        spec.placement.forEachIndexed { index, step ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            PlacementStep("${index + 1}", step)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            spec.placementNote,
            style = AsrType.Legal.copy(fontSize = 12.sp),
            color = AsrColors.TextTertiary,
        )
    }
}

@Composable
private fun PlacementStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AsrColors.AccentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = AsrType.Label.copy(fontSize = 12.sp), color = AsrColors.Accent)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = AsrType.Label.copy(fontSize = 13.sp),
            color = AsrColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Figma 24 — Earn Time / Completed (node 135:2). */
@Composable
fun EarnedScreen(
    activity: EarnActivity,
    /** Bonus minutes this app has won today, this activity included. */
    earnedToday: Int,
    onUseNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The last rep, or second, replaces the camera with this screen in the
    // same frame, while the person is still on the floor. So for a moment
    // it is the number they were working towards, at floor size, with the
    // finish chime, and only then the receipt for the person who has
    // stood up.
    var moment by remember(activity.id) { mutableStateOf(activity.isCamera) }
    val feedback = rememberRepFeedback()
    LaunchedEffect(activity.id) {
        if (!activity.isCamera) return@LaunchedEffect
        feedback.finished()
        delay(1_400)
        moment = false
    }
    if (moment) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AsrColors.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                cameraSpec(activity.type)?.format(activity.target) ?: "${activity.target}",
                style = AsrType.display(140),
                color = AsrColors.Accent,
            )
            Text("✓  DONE", style = AsrType.Eyebrow.copy(fontSize = 16.sp), color = AsrColors.Accent)
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))
        Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = AsrColors.Accent,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Text("✓", style = AsrType.display(44), color = AsrColors.Accent)
        }

        Spacer(Modifier.height(26.dp))
        Text("ACTIVITY COMPLETE", style = AsrType.Eyebrow, color = AsrColors.Accent)
        Spacer(Modifier.height(14.dp))
        Text(
            "+${activity.rewardMinutes} minutes earned.",
            style = AsrType.display(34),
            color = AsrColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            (earnOptionFor(activity.type)?.done ?: "Your activity is complete.") +
                " ${activity.appLabel} now has ${activity.rewardMinutes} extra minutes available today.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsrAppIcon(activity.packageName, activity.appLabel)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${activity.appLabel.uppercase()} BONUS",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "+${activity.rewardMinutes} minutes",
                    style = AsrType.display(23),
                    color = AsrColors.TextPrimary,
                )
            }
            SmallPill("READY", AsrColors.Accent, AsrColors.AccentMuted)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.SurfaceSunken, RoundedCornerShape(18.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(18.dp))
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "EARNED TODAY",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text("$earnedToday min", style = AsrType.display(26), color = AsrColors.TextPrimary)
            }
            Text(
                "extra for ${activity.appLabel} today",
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(16.dp))
        RewardNote(
            title = "Your daily limit stays the same.",
            body = "This ${activity.rewardMinutes} minutes is bonus access for " +
                "${activity.appLabel} only.",
        )

        Spacer(Modifier.height(24.dp))
        AsrPrimaryButton(text = "Use now", onClick = onUseNow)
        Spacer(Modifier.height(18.dp))
        Text(
            "Back to dashboard",
            style = AsrType.Label.copy(fontSize = 14.sp),
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TargetApp(app: PactApp, earnedSoFar: Int) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsrAppIcon(app.packageName, app.label)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "FOR ${app.label.uppercase()}",
                style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                color = AsrColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "+${EarnRules.REWARD_MINUTES} minutes",
                style = AsrType.display(22),
                color = AsrColors.TextPrimary,
            )
        }
        SmallPill(
            text = if (earnedSoFar > 0) "+$earnedSoFar TODAY" else "APP-SPECIFIC",
            colour = AsrColors.Accent,
            fill = AsrColors.AccentMuted,
        )
    }
}

@Composable
internal fun RewardContext(activity: EarnActivity) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.Surface, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsrAppIcon(activity.packageName, activity.appLabel)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "REWARD",
                style = AsrType.Eyebrow.copy(fontSize = 10.sp),
                color = AsrColors.Accent,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "+${activity.rewardMinutes} min ${activity.appLabel}",
                style = AsrType.RowTitle,
                color = AsrColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SmallPill("LIVE", AsrColors.Accent, AsrColors.AccentMuted)
    }
}

@Composable
private fun TrackingStatus(type: String) {
    val walk = type == EarnRules.WALK
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AsrColors.SurfaceSunken, shape)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("●", style = AsrType.Field.copy(fontSize = 16.sp), color = AsrColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (type == EarnRules.FOCUS) "Time for the real world" else "Counted by your phone",
                style = AsrType.Field.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                when (type) {
                    // True, and worth saying plainly: the step counter is a
                    // running total the sensor hub keeps whether or not this
                    // app is running, so nothing is lost by leaving.
                    EarnRules.WALK -> "You can lock your phone or leave this screen. Steps keep counting."
                    // These two are measured by the background service as
                    // the readings arrive, in batches: the count can lag a
                    // pocketed phone by a few seconds, never lose anything.
                    EarnRules.RUN ->
                        "Keep the phone on you and lock it. Steps at a running pace keep counting; " +
                            "we’ll notify you when your run is done."
                    EarnRules.STAIRS ->
                        "Keep the phone on you and lock it. Floors climbed on foot keep counting, " +
                            "all day if need be; we’ll notify you when you have them."
                    EarnRules.RIDE ->
                        "Keep the phone on you and lock it. GPS measures the ride and shows a " +
                            "notification while it does; we’ll notify you when it is done."
                    else ->
                        "Incoming calls and notifications won’t affect your session. " +
                            "We’ll notify you when your ${EarnRules.FOCUS_MINUTES} minutes are complete."
                },
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}

@Composable
internal fun RewardNote(title: String, body: String) {
    val shape = RoundedCornerShape(16.dp)
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
                title,
                style = AsrType.Field.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = AsrType.Legal.copy(fontSize = 12.sp),
                color = AsrColors.TextSecondary,
            )
        }
    }
}


/** Thousands separated, because 2500 steps reads as a part number. */
private fun format(value: Int): String = String.format(Locale.US, "%,d", value)

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ChooseActivityPreview() {
    AsrTheme {
        ChooseActivityScreen(
            app = PactApp("com.zhiliaoapp.musically", "TikTok", 20),
            earnedSoFar = 0,
            options = earnOptions(
                stepsAvailable = true,
                cameraAvailable = true,
                barometerAvailable = true,
                accelerometerAvailable = true,
                gpsAvailable = true,
            ),
            onBack = {},
            onStart = {},
            errorMessage = null,
        )
    }
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ActivityProgressPreview() {
    AsrTheme {
        ActivityProgressScreen(
            activity = EarnActivity(
                id = "1",
                type = EarnRules.WALK,
                packageName = "com.zhiliaoapp.musically",
                appLabel = "TikTok",
                target = EarnRules.WALK_STEPS,
                rewardMinutes = 10,
                startedAtMillis = 0,
                deadlineAtMillis = 0,
                baselineSteps = 0,
                progress = 1_240,
            ),
            onBack = {},
            onEnd = {},
        )
    }
}
