package io.joinasr.app.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.joinasr.app.earn.EarnActivity
import io.joinasr.app.earn.EarnRules
import io.joinasr.app.earn.PushUpCameraView
import io.joinasr.app.earn.PushUpCounter
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.ui.components.AsrAppIcon
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.components.AsrPrimaryButton
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import io.joinasr.app.ui.theme.AsrType
import java.util.Locale

/**
 * Figma 21 — Earn Time / Choose Activity (node 131:2).
 *
 * Reached from the block screen, which is the only place it makes sense: the
 * moment somebody wants more time is the moment they have run out.
 *
 * Walking is offered only when the phone has a step counter and only when
 * the permission is there — Figma 22 is what asks for it, and a row that
 * opened a tracker which could never count would be worse than one that says
 * what it needs first.
 */
@Composable
fun ChooseActivityScreen(
    app: PactApp,
    earnedSoFar: Int,
    stepsAvailable: Boolean,
    cameraAvailable: Boolean,
    onBack: () -> Unit,
    onWalk: () -> Unit,
    onFocus: () -> Unit,
    onPushUps: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val capped = earnedSoFar >= EarnRules.DAILY_CAP_MINUTES

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
            "Only completed activities earn time.",
            style = AsrType.Legal.copy(fontSize = 13.sp),
            color = AsrColors.TextTertiary,
        )

        Spacer(Modifier.height(14.dp))
        ActivityCard(
            glyph = "↗",
            title = "Walk ${"%.1f".format(Locale.US, EarnRules.kilometresFor(EarnRules.WALK_STEPS))} km",
            subtitle = "≈ ${format(EarnRules.WALK_STEPS)} steps",
            detail = if (stepsAvailable) {
                "Track steps until the goal is complete."
            } else {
                "This phone has no step counter, so a walk cannot be measured."
            },
            reward = "Earn +${EarnRules.REWARD_MINUTES}m for ${app.label}",
            badge = if (stepsAvailable) "RECOMMENDED" else null,
            enabled = stepsAvailable && !capped,
            onClick = onWalk,
        )

        Spacer(Modifier.height(14.dp))
        ActivityCard(
            glyph = "◎",
            title = "Put your phone down for ${EarnRules.FOCUS_MINUTES} minutes.",
            subtitle = "Phone-free session",
            detail = "Keep your phone locked and spend some time in the real world.",
            reward = "Earn +${EarnRules.REWARD_MINUTES}m for ${app.label}",
            badge = null,
            enabled = !capped,
            onClick = onFocus,
        )

        // Not in Figma: added after 21-24 were built, laid out from the
        // same card. Offered only where there is a camera to count with.
        Spacer(Modifier.height(14.dp))
        ActivityCard(
            glyph = "⇅",
            title = "Do ${EarnRules.PUSHUP_REPS} push-ups",
            subtitle = "Counted by your camera",
            detail = if (cameraAvailable) {
                "Prop your phone up sideways and do them on camera. Nothing is recorded."
            } else {
                "This phone has no camera, so push-ups cannot be counted."
            },
            reward = "Earn +${EarnRules.REWARD_MINUTES}m for ${app.label}",
            badge = null,
            enabled = cameraAvailable && !capped,
            onClick = onPushUps,
        )

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
}

/**
 * Figma 22 — Permission / Activity Tracking — First Walk (node 119:73).
 *
 * Asked the first time somebody chooses a walk and never at launch, which is
 * the difference between a permission a person understands and one they
 * refuse on principle.
 */
@Composable
fun ActivityTrackingScreen(
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
        Text("Track your walk.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Only requested when you choose a walking activity to earn extra app time.",
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
 * Asked the first time somebody chooses push-ups and never at launch. The
 * promises on it are the ones enforced in earn/PoseCamera.kt: frames go to
 * a model on the phone and are dropped, nothing is written, nothing is
 * sent, and the camera is released with the screen.
 */
@Composable
fun CameraAccessScreen(
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
        Text("Count your push-ups.", style = AsrType.display(38), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Only requested when you choose push-ups to earn extra app time.",
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
                    "${EarnRules.PUSHUP_REPS}",
                    style = AsrType.display(38),
                    color = AsrColors.Accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "PUSH-UPS",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.Accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Counted from a side view, shoulders to hips",
                style = AsrType.Label.copy(fontSize = 14.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your phone finds the shape of your body in each frame and counts " +
                    "each time your arms bend and straighten. The frame is then dropped.",
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
                "Used only while the push-up screen is open. The camera switches " +
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
        AsrPrimaryButton(text = "Allow camera access", onClick = onAllow)
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
    // Display only. No UI clock can complete an activity or award minutes.
    val focusRemaining by produceState(
        initialValue = activity.target * 60,
        key1 = activity.id,
        key2 = activity.focusLockedSinceElapsed,
    ) {
        while (!walk) {
            val elapsed = activity.focusLockedSinceElapsed?.let {
                (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)
            } ?: 0L
            value = ((activity.target * 60_000L - elapsed).coerceAtLeast(0L) + 999L)
                .div(1_000L).toInt()
            delay(250)
        }
    }
    val fraction = if (walk) activity.fraction else
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
            if (walk) "Keep walking." else "Put your phone down for ${activity.target} minutes.",
            style = AsrType.display(36),
            color = AsrColors.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (walk) {
                "Reach ${"%.0f".format(Locale.US, EarnRules.kilometresFor(activity.target))} km " +
                    "to earn ${activity.rewardMinutes} more minutes for ${activity.appLabel}."
            } else {
                "Keep your phone locked and spend some time in the real world."
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
                        } else if (!walk) {
                            String.format(Locale.US, "%02d:%02d", focusRemaining / 60, focusRemaining % 60)
                        } else {
                            format(activity.progress)
                        },
                        style = AsrType.display(48),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (walk) {
                            "of ${format(activity.target)} steps"
                        } else {
                            if (activity.focusLockedSinceElapsed == null) {
                                "Lock your phone to start the timer."
                            } else {
                                "remaining with your phone locked"
                            }
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
        TrackingStatus(walk = walk)

        Spacer(Modifier.height(16.dp))
        RewardNote(
            title = if (walk) {
                "${format(activity.remaining)} steps to go"
            } else {
                "If you unlock your phone before the ${activity.target} minutes are complete, the timer will reset."
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
 * Push-ups in progress: Figma 23's parts around a camera.
 *
 * The counter belongs to this screen and starts from nothing each time it
 * opens; the activity carries the number, through [onPushUp], so stepping
 * away and coming back resumes at the same count. Compose never awards the
 * minutes: the view model does, on the seventh, the same way a walk ends.
 */
@Composable
fun PushUpProgressScreen(
    activity: EarnActivity,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onPushUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counter = remember(activity.id) { PushUpCounter() }
    var phase by remember(activity.id) { mutableStateOf(PushUpCounter.Phase.NO_BODY) }
    var cameraProblem by remember(activity.id) { mutableStateOf<String?>(null) }
    val percent = (activity.fraction * 100).toInt()

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
        Text("Keep going.", style = AsrType.display(36), color = AsrColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Do ${activity.target} push-ups on camera to earn ${activity.rewardMinutes} " +
                "more minutes for ${activity.appLabel}.",
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        RewardContext(activity)

        Spacer(Modifier.height(18.dp))
        val frame = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(frame)
                .background(AsrColors.Surface)
                .border(1.dp, AsrColors.FieldBorder, frame),
        ) {
            val problem = cameraProblem
            if (problem == null) {
                PushUpCameraView(
                    onPose = { pose, at ->
                        if (counter.observe(pose, at)) onPushUp()
                        phase = counter.phase
                    },
                    onError = { cameraProblem = it },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    problem,
                    style = AsrType.Field,
                    color = AsrColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            Box(modifier = Modifier.align(Alignment.TopStart).padding(14.dp)) {
                SmallPill("${activity.progress} / ${activity.target}", AsrColors.Accent, AsrColors.AccentMuted)
            }
        }

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
                        "${activity.progress}",
                        style = AsrType.display(48),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "of ${activity.target} push-ups",
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
                        .fillMaxWidth(activity.fraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AsrColors.Accent),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        PushUpCoaching(phase)

        Spacer(Modifier.height(16.dp))
        RewardNote(
            title = "${activity.remaining} to go",
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

/** What the counter can see, said as the next thing to do. */
@Composable
private fun PushUpCoaching(phase: PushUpCounter.Phase) {
    val (title, body) = when (phase) {
        PushUpCounter.Phase.NO_BODY ->
            "Get your whole body in view" to
                "Prop your phone on its side a few steps away, so it sees you from the side, " +
                "shoulders to hips."
        PushUpCounter.Phase.NOT_IN_POSITION ->
            "Get into a plank" to "Hands under your shoulders, body straight, facing the phone side-on."
        PushUpCounter.Phase.UP ->
            "Now go down" to "Bend your elbows until your chest is near the floor."
        PushUpCounter.Phase.DOWN ->
            "Push up" to "Straighten your arms all the way. That is one."
    }
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
            Text(title, style = AsrType.Field.copy(fontSize = 16.sp), color = AsrColors.TextPrimary)
            Spacer(Modifier.height(7.dp))
            Text(body, style = AsrType.Label.copy(fontSize = 13.sp), color = AsrColors.TextSecondary)
        }
    }
}

/** Figma 24 — Earn Time / Completed (node 135:2). */
@Composable
fun EarnedScreen(
    activity: EarnActivity,
    availableNow: Int,
    onUseNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            when {
                activity.isWalk ->
                    "Your walk is complete. ${activity.appLabel} now has " +
                        "${activity.rewardMinutes} extra minutes available today."
                activity.isPushUps ->
                    "Your push-ups are done. ${activity.appLabel} now has " +
                        "${activity.rewardMinutes} extra minutes available today."
                else ->
                    "Your phone-free session is complete. ${activity.appLabel} now has " +
                        "${activity.rewardMinutes} extra minutes available today."
            },
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
                    "AVAILABLE NOW",
                    style = AsrType.Eyebrow.copy(fontSize = 11.sp),
                    color = AsrColors.TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text("$availableNow min", style = AsrType.display(26), color = AsrColors.TextPrimary)
            }
            Text(
                "for ${activity.appLabel} today",
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
private fun RewardContext(activity: EarnActivity) {
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
private fun TrackingStatus(walk: Boolean) {
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
                if (walk) "Counted by your phone" else "Time for the real world",
                style = AsrType.Field.copy(fontSize = 16.sp),
                color = AsrColors.TextPrimary,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                if (walk) {
                    // True, and worth saying plainly: the step counter is a
                    // running total the sensor hub keeps whether or not this
                    // app is running, so nothing is lost by leaving.
                    "You can lock your phone or leave this screen. Steps keep counting."
                } else {
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
private fun ActivityCard(
    glyph: String,
    title: String,
    subtitle: String,
    detail: String,
    reward: String,
    badge: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AsrColors.Surface)
            .border(1.dp, AsrColors.FieldBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (enabled) AsrColors.AccentMuted else AsrColors.Field),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                style = AsrType.display(24),
                color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = AsrType.display(19),
                    color = if (enabled) AsrColors.TextPrimary else AsrColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null && enabled) {
                    SmallPill(badge, AsrColors.Accent, AsrColors.AccentMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = AsrColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = AsrType.Legal.copy(fontSize = 13.sp),
                color = AsrColors.TextTertiary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                reward,
                style = AsrType.Label.copy(fontSize = 13.sp),
                color = if (enabled) AsrColors.Accent else AsrColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun RewardNote(title: String, body: String) {
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
            stepsAvailable = true,
            cameraAvailable = true,
            onBack = {},
            onWalk = {},
            onFocus = {},
            onPushUps = {},
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
