package io.joinasr.app.enforcement

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.ui.screens.BlockedScreen
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme

/**
 * The block screen, as a full-screen activity launched over whatever app ran
 * out of time.
 *
 * An activity, not a `TYPE_APPLICATION_OVERLAY` window. The overlay was the
 * first attempt and it did not work: the service decided correctly, the
 * dashboard said LOCKED, and Instagram carried on scrolling underneath.
 * Every screen-time app on the store does it this way instead, and the
 * reason is worth writing down, because the permission looks like it is for
 * something else:
 *
 * From Android 10 an app in the background cannot start an activity. There
 * is a short list of exemptions, and one of them is holding
 * SYSTEM_ALERT_WINDOW — "display over other apps". So that permission is not
 * asked for in order to draw a window. It is asked for because it is what
 * lets this service put a screen in front of somebody at the moment they run
 * out of time. Without it the launch is silently dropped, which is exactly
 * what a person would experience as "the app does nothing".
 *
 * The activity owns its own task and stays out of recents, so closing it
 * cannot leave a stray Asr entry behind, and coming back to the blocked app
 * does not resurrect it — the service launches a fresh one within the
 * second.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initial = stateFrom(intent)
        val state = mutableStateOf(initial)

        // Back must not simply dismiss this. A block one keypress deep is not
        // a block, so back does what the button does: takes the person home,
        // which respects the limit without trapping them in an app they
        // cannot use.
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            AsrTheme {
                val current = state.value
                val icon by produceState<ImageBitmap?>(null, current.packageName) {
                    value = InstalledApps.icon(this@BlockActivity, current.packageName)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AsrColors.Background)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    BlockedScreen(
                        appLabel = current.label,
                        icon = icon,
                        usedMinutes = current.usedMinutes,
                        limitMinutes = current.limitMinutes,
                        availableAgain = current.availableAgain,
                        onLeave = ::goHome,
                    )
                }
            }
        }

        // singleTask, so a second block while this one is up arrives here
        // rather than stacking another copy.
        addOnNewIntentListener { state.value = stateFrom(it) }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    private fun stateFrom(intent: Intent?) = Shown(
        packageName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty(),
        label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "This app" },
        usedMinutes = intent?.getIntExtra(EXTRA_USED, 0) ?: 0,
        limitMinutes = intent?.getIntExtra(EXTRA_LIMIT, 0) ?: 0,
        availableAgain = intent?.getStringExtra(EXTRA_RESET).orEmpty(),
    )

    private data class Shown(
        val packageName: String,
        val label: String,
        val usedMinutes: Int,
        val limitMinutes: Int,
        val availableAgain: String,
    )

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USED = "used"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_RESET = "reset"

        fun intent(
            context: Context,
            app: PactApp,
            usedMinutes: Int,
            availableAgain: String,
        ): Intent = Intent(context, BlockActivity::class.java)
            .addFlags(
                // A task of its own, replacing anything already in it, and
                // never animated in -- the block should be there the instant
                // it is there.
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            .putExtra(EXTRA_PACKAGE, app.packageName)
            .putExtra(EXTRA_LABEL, app.label)
            .putExtra(EXTRA_USED, usedMinutes)
            .putExtra(EXTRA_LIMIT, app.limitMinutes)
            .putExtra(EXTRA_RESET, availableAgain)
    }
}
