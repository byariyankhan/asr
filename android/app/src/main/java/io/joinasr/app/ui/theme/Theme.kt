package io.joinasr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder scheme. The real tokens come from the Figma file with screen 01;
// deliberately not invented here, because a colour that is nearly right is
// harder to notice and fix than one that is obviously absent.
private val Amber = Color(0xFFE8B44A)
private val Ink = Color(0xFF0B0E14)

private val DarkScheme = darkColorScheme(primary = Amber, background = Ink, surface = Ink)
private val LightScheme = lightColorScheme(primary = Amber)

@Composable
fun AsrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
