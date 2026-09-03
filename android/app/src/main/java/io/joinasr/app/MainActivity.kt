package io.joinasr.app

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
import androidx.compose.ui.Modifier
import io.joinasr.app.ui.AsrApp
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    AsrApp()
                }
            }
        }
    }
}
