package com.joinasr.app.earn

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * What a counted push-up sounds and feels like.
 *
 * At the bottom of a push-up nobody is reading a number, so the moment the
 * counter agrees has to be heard: a short tick per rep, a two-note rise on
 * the last one, a smaller tap when the phone first sees a body. The tones
 * are the platform's own ([ToneGenerator]), generated, not recorded: no
 * asset, no library, no permission. A phone on silent or vibrate stays
 * silent; the frame's colour and the number's pulse carry it then.
 *
 * The pulse goes through the view's own haptic feedback, which needs no
 * VIBRATE permission and does nothing on a phone that has haptics off. On
 * a hard floor the buzz is audible too.
 *
 * Signals only. The view model awards the minutes; this says so.
 */
class RepFeedback(context: Context, private val view: View?) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // Constructing one can throw on a phone with no audio output at all;
    // then there is nothing to play and nothing to release.
    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()

    private val audible: Boolean
        get() = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    /** The phone can see a body: a small tap, felt rather than read. */
    fun found() {
        pulse(light = true)
    }

    /** One push-up counted. */
    fun rep() {
        if (audible) tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        pulse(light = false)
    }

    /** The set is done and the minutes are written. */
    fun finished() {
        if (audible) tones?.startTone(ToneGenerator.TONE_PROP_ACK, 320)
        pulse(light = false)
        handler.postDelayed({ pulse(light = false) }, 140)
    }

    private fun pulse(light: Boolean) {
        val constant = when {
            light -> HapticFeedbackConstants.CONTEXT_CLICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        runCatching { view?.performHapticFeedback(constant) }
    }

    /**
     * Let go of the generator, a moment later rather than now: the screen
     * that owned this is often replaced in the same frame as the last
     * tone starts, and releasing at once would cut it off.
     */
    fun close() {
        handler.postDelayed({ runCatching { tones?.release() } }, 600)
    }

    private companion object {
        /** Out of 100. Loud enough over a floor, not a phone-call alarm. */
        const val VOLUME = 80
    }
}

/** One [RepFeedback] for as long as the calling composable is on screen. */
@Composable
fun rememberRepFeedback(): RepFeedback {
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember { RepFeedback(context, view) }
    DisposableEffect(feedback) {
        onDispose { feedback.close() }
    }
    return feedback
}
