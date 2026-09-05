package io.joinasr.app.enforcement

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.ui.screens.BlockedScreen
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The block screen as a window this app draws itself, for the phones that
 * drop the activity.
 *
 * The activity is the better block: it takes the blocked app out of the
 * foreground, so its minutes stop counting and Android's own back stack
 * does the rest. It is also a background activity launch, which a handful
 * of manufacturers refuse even with "display over other apps" granted. This
 * window needs only that grant: `TYPE_APPLICATION_OVERLAY` is precisely what
 * the permission is for, and no skin has a second switch for it.
 *
 * The first version of this product tried an overlay first and it did not
 * hold -- touches went through to the app underneath -- which is why the
 * activity became the primary route. This one is full-screen, touch-modal
 * and focusable, so it takes every touch and the back key, and it only ever
 * appears after the activity has demonstrably not.
 *
 * A Compose tree in a window the system owns has to be given the lifecycle,
 * view-model store and saved-state registry it would otherwise inherit from
 * an activity; [OverlayOwner] is that, and nothing more.
 */
class BlockOverlay(context: Context) {

    class Shown(
        val app: PactApp,
        val usedMinutes: Int,
        val limitMinutes: Int,
        val availableAgain: String,
        val onLeave: () -> Unit,
        val onEarnTime: () -> Unit,
    )

    private val app = context.applicationContext
    private val windowManager = app.getSystemService<WindowManager>()
    private val shown = mutableStateOf<Shown?>(null)
    private var root: View? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = root != null

    /**
     * Puts the window up, or updates what it says if it is already up.
     * False when it cannot be shown: no overlay grant, or the window manager
     * refused it.
     */
    suspend fun show(content: Shown): Boolean = withContext(Dispatchers.Main.immediate) {
        val manager = windowManager ?: return@withContext false
        if (!Settings.canDrawOverlays(app)) return@withContext false
        shown.value = content
        if (root != null) return@withContext true
        runCatching {
            val lifecycle = OverlayOwner()
            val view = OverlayRoot(app) { shown.value?.onLeave?.invoke() }
            view.setViewTreeLifecycleOwner(lifecycle)
            view.setViewTreeViewModelStoreOwner(lifecycle)
            view.setViewTreeSavedStateRegistryOwner(lifecycle)
            view.addView(ComposeView(app).apply { setContent { Content() } })
            manager.addView(view, layoutParams())
            lifecycle.resume()
            root = view
            owner = lifecycle
        }.onFailure { shown.value = null }.isSuccess
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) { hideNow() }

    /** [hide] for callers already on the main thread, such as a service's onDestroy. */
    fun hideNow() {
        val view = root ?: return
        root = null
        runCatching { windowManager?.removeViewImmediate(view) }
        owner?.destroy()
        owner = null
        shown.value = null
    }

    @Composable
    private fun Content() {
        val current by shown
        AsrTheme {
            val content = current ?: return@AsrTheme
            val icon by produceState<ImageBitmap?>(null, content.app.packageName) {
                value = InstalledApps.icon(app, content.app.packageName)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AsrColors.Background)
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                BlockedScreen(
                    appLabel = content.app.label,
                    icon = icon,
                    usedMinutes = content.usedMinutes,
                    limitMinutes = content.limitMinutes,
                    availableAgain = content.availableAgain,
                    onLeave = content.onLeave,
                    onEarnTime = content.onEarnTime,
                )
            }
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable (no FLAG_NOT_FOCUSABLE) so the back key arrives
            // here rather than at the app underneath, and touch-modal (no
            // FLAG_NOT_TOUCH_MODAL) so nothing reaches it either.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    /** The window's root: back does what the button does, and nothing else leaks. */
    private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
