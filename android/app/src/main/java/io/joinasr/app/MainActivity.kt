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
     * The reset token from https://joinasr.io/reset/<token>, if the app was
     * opened by one of those links. Held here rather than read inside the
     * composition because the link can also arrive at an activity that is
     * already running, through onNewIntent, and a composable reading
     * `intent` would never see it.
     */
    private var resetToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resetToken = tokenFrom(intent)
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
                        resetToken = resetToken,
                        // Consumed once the reset screen has it, so rotating
                        // the phone afterwards does not reopen the screen
                        // with a token that has already been spent.
                        onResetTokenHandled = { resetToken = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tokenFrom(intent)?.let { resetToken = it }
    }

    /**
     * The last path segment of a reset link. Anything else — the launcher
     * icon, a share, a link to some other path — yields null, so a stray
     * intent cannot put somebody on a reset screen with an empty token.
     */
    private fun tokenFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val segments = intent.data?.pathSegments ?: return null
        if (segments.size != 2 || segments[0] != "reset") return null
        return segments[1].takeIf { it.isNotBlank() }
    }
}
