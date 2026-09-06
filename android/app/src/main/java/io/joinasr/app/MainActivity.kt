package io.joinasr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.joinasr.app.ui.AsrApp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme

class MainActivity : ComponentActivity() {

    /**
     * The link this activity was opened by, if any.
     *
     * Held here rather than read inside the composition because a link can
     * also arrive at an activity that is already running, through
     * onNewIntent, and a composable reading `intent` would never see it.
     */
    private var link by mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start. The intent that opened the activity is
        // still attached when Android rebuilds it for a rotation, and
        // reading it again re-fired the link every time: the reset form
        // reopened with a token already spent, the invitation reopened
        // after being answered.
        if (savedInstanceState == null) link = DeepLink.from(intent)
        // Edge to edge, then the app's own background painted behind the
        // status and navigation bars — without it they stay the system's
        // default and the black screen ends in two grey stripes.
        enableEdgeToEdge()
        setContent {
            AsrTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AsrColors.Background)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AsrApp(
                        link = link,
                        // Consumed once the screen has it; what it opened
                        // is the screen's own state from then on.
                        onLinkHandled = { link = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.from(intent)?.let { link = it }
    }
}
