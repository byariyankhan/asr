package io.joinasr.app.enforcement

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.joinasr.app.R
import io.joinasr.app.ui.screens.BlockedScreen
import io.joinasr.app.ui.theme.AsrTheme

/** What the overlay is currently showing. */
data class BlockedState(
    val app: PactApp,
    val usedMinutes: Int,
    val icon: ImageBitmap?,
    val availableAgain: String,
)

/**
 * The block screen, drawn on top of whatever app ran out of time.
 *
 * A window rather than an activity. An activity would enter the recents
 * list, could be swiped away, and would fight the blocked app for the task
 * stack every time it came back to the front; a TYPE_APPLICATION_OVERLAY
 * window simply sits above everything until it is taken down.
 *
 * Compose in a window the system owns needs three things set on the view or
 * it throws on first composition, because none of them come from an activity
 * here: a lifecycle, a ViewModel store, and a saved-state registry. That is
 * what [OverlayOwner] is for, and it is the part of this file most likely to
 * look like ceremony and least safe to remove.
 *
 * Every window operation is wrapped. The overlay permission can be revoked
 * while the service is running, and a service that dies for it takes every
 * limit with it -- so a failure to draw is a limit not enforced this once,
 * not a crash.
 */
class BlockOverlay(private val context: Context) {

    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val state = mutableStateOf<BlockedState?>(null)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = view != null

    /**
     * Shows the block screen, or updates it in place if it is already up.
     * Updating rather than re-adding matters: re-adding the window on every
     * poll would flicker once a second.
     */
    fun show(blocked: BlockedState) {
        state.value = blocked
        if (view != null) return
        if (!canDraw()) return

        val overlayOwner = OverlayOwner().also { it.create() }
        // Themed rather than the bare service context: Compose resolves
        // theme attributes as it attaches, and a context with no theme
        // throws on some manufacturers' framework builds.
        val themed = ContextThemeWrapper(context, R.style.Theme_Asr)
        val composeView = ComposeView(themed).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setContent {
                AsrTheme {
                    state.value?.let { current ->
                        BlockedScreen(
                            appLabel = current.app.label,
                            icon = current.icon,
                            usedMinutes = current.usedMinutes,
                            limitMinutes = current.app.limitMinutes,
                            availableAgain = current.availableAgain,
                            onLeave = ::leave,
                        )
                    }
                }
            }
            // Back must not simply dismiss the overlay: that would make the
            // block a single keypress deep. It does what the button does,
            // which is take the person out of the app they ran out of time
            // in -- the one action that both respects the limit and does not
            // trap them.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    leave()
                    true
                } else {
                    false
                }
            }
        }

        val added = runCatching { windows?.addView(composeView, layoutParams()) }.isSuccess
        if (!added) {
            overlayOwner.destroy()
            return
        }
        view = composeView
        owner = overlayOwner
        composeView.requestFocus()
    }

    fun hide() {
        val current = view ?: return
        runCatching { windows?.removeView(current) }
        owner?.destroy()
        view = null
        owner = null
        state.value = null
    }

    /**
     * Sends the person to their launcher.
     *
     * Not `finish()`, which there is nothing here to finish, and not a jump
     * into Asr, which would replace one app they did not ask for with
     * another. Home is where somebody who has just been stopped wants to be.
     */
    private fun leave() {
        hide()
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(home) }
    }

    private fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Focusable, because the screen has a button and a back key to
        // handle. NOT_TOUCH_MODAL is deliberately absent: touches must not
        // pass through to the app underneath, or the block would be
        // decorative.
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

/**
 * The lifecycle, ViewModel store and saved-state registry a ComposeView
 * expects to inherit from an activity, for a window that has no activity.
 *
 * Held for exactly as long as the overlay is on screen and destroyed with
 * it, so a block screen shown and dismissed a hundred times in a day leaves
 * nothing behind.
 */
private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun create() {
        // Restore first: the registry refuses to be read once the lifecycle
        // has moved past INITIALIZED, and Compose reads it as it attaches.
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
