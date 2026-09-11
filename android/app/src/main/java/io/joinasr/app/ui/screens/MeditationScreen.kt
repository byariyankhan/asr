package io.joinasr.app.ui.screens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.joinasr.app.earn.EarnActivity
import io.joinasr.app.earn.StillnessJudge
import io.joinasr.app.earn.rememberRepFeedback
import io.joinasr.app.ui.components.AsrBackChevron
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * A meditation in progress: a breathing guide, and a clock that runs
 * while the phone lies still under it.
 *
 * The guide is a circle that swells for four seconds and settles for six,
 * with the word for it, and a small tap at each turn ([io.joinasr.app.earn.RepFeedback])
 * so the eyes can close. The proof is the motion sensor ([StillnessJudge]):
 * the clock runs while the phone is still and stops while it is held or
 * carried, and the seconds go to the activity through [onCounted] as they
 * complete, so leaving and coming back resumes at the same number. Compose
 * never awards the minutes; the view model does, on the last second.
 *
 * The sensor is listened to only while this screen is resumed, so the
 * lock screen and other apps stop the clock, and a gap in the samples is
 * worth nothing. The screen stays on for the length of it.
 */
@Composable
fun MeditationScreen(
    activity: EarnActivity,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onCounted: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val judge = remember(activity.id) { StillnessJudge() }
    var still by remember(activity.id) { mutableStateOf(false) }
    var sensing by remember(activity.id) { mutableStateOf(false) }
    var confirmingGiveUp by remember(activity.id) { mutableStateOf(false) }
    val feedback = rememberRepFeedback()
    val latestOnCounted by rememberUpdatedState(onCounted)

    // The clock, from the accelerometer, while the screen is up.
    LifecycleResumeEffect(activity.id) {
        val manager = context.getSystemService<SensorManager>()
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val at = event.timestamp / 1_000_000L
                val earned = judge.observe(event.values[0], event.values[1], event.values[2], at)
                still = judge.still
                if (earned > 0) latestOnCounted(earned)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensing = sensor != null &&
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        view.keepScreenOn = true
        onPauseOrDispose {
            manager?.unregisterListener(listener)
            view.keepScreenOn = false
            sensing = false
            // No samples means no stillness: the breathing loop below is
            // keyed on it, and must not keep tapping from behind the lock
            // screen or another app.
            still = false
        }
    }

    // The breath: in for four, out for six, round and round while still.
    var breathingIn by remember(activity.id) { mutableStateOf(true) }
    var cycles by remember(activity.id) { mutableIntStateOf(0) }
    LaunchedEffect(activity.id, still) {
        if (!still) return@LaunchedEffect
        while (true) {
            breathingIn = true
            feedback.breath()
            delay(BREATH_IN_MILLIS)
            breathingIn = false
            feedback.breath()
            delay(BREATH_OUT_MILLIS)
            cycles++
        }
    }
    val swell by animateFloatAsState(
        targetValue = if (still && breathingIn) 1f else 0f,
        animationSpec = tween(if (breathingIn) BREATH_IN_MILLIS.toInt() else BREATH_OUT_MILLIS.toInt()),
        label = "breath",
    )
    val remaining = activity.remaining

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AsrColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth()) { AsrBackChevron(onBack) }

        Spacer(Modifier.height(28.dp))
        // The guide. A ring that swells and settles, the word inside it,
        // and the time left under it; grey and still when the phone is
        // not lying still, so the reason the clock stopped is the picture.
        Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val base = size.minDimension * 0.30f
                val radius = base + size.minDimension * 0.18f * swell
                drawCircle(
                    color = (if (still) AsrColors.Accent else AsrColors.TextTertiary).copy(alpha = 0.10f + 0.10f * swell),
                    radius = radius,
                )
                drawCircle(
                    color = if (still) AsrColors.Accent else AsrColors.TextTertiary,
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        !sensing -> "No motion sensor"
                        !still -> "Put the phone down"
                        breathingIn -> "Breathe in"
                        else -> "Breathe out"
                    },
                    style = AsrType.display(24),
                    color = AsrColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    String.format(Locale.US, "%d:%02d", remaining / 60, remaining % 60),
                    style = AsrType.Label.copy(fontSize = 15.sp),
                    color = if (still) AsrColors.Accent else AsrColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            when {
                !sensing -> "This phone has no motion sensor, so the clock cannot run."
                !still -> "The clock runs while the phone lies still. Put it down where you can see it and leave it."
                else -> "Eyes can close. A tap marks each turn of the breath."
            },
            style = AsrType.Field,
            color = AsrColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        RewardContext(activity)

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AsrColors.Surface, RoundedCornerShape(22.dp))
                .border(1.dp, AsrColors.FieldBorder, RoundedCornerShape(22.dp))
                .padding(17.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LIVE PROGRESS", style = AsrType.Eyebrow.copy(fontSize = 10.sp), color = AsrColors.Accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        String.format(Locale.US, "%d:%02d", activity.progress / 60, activity.progress % 60),
                        style = AsrType.display(48),
                        color = AsrColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "of ${activity.target / 60} minutes, still",
                        style = AsrType.Label.copy(fontSize = 14.sp),
                        color = AsrColors.TextSecondary,
                    )
                }
                SmallPill(if (still) "STILL" else "PAUSED", AsrColors.Accent, AsrColors.AccentMuted)
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
            title = "${(remaining + 59) / 60} minutes to go",
            body = "Finish and ${activity.appLabel} gets +${activity.rewardMinutes} minutes today.",
        )

        // The same two exits as the camera screen: back keeps the time,
        // giving up throws it away and asks once before it does.
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
            Text("Finish later", style = AsrType.Label.copy(fontSize = 14.sp), color = AsrColors.TextPrimary)
            if (activity.progress > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "Your ${activity.progress / 60} minutes are saved.",
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
                    "Give up this session? Your ${activity.progress / 60} minutes will not count.",
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
                "Give up this session",
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

private const val BREATH_IN_MILLIS = 4_000L
private const val BREATH_OUT_MILLIS = 6_000L
