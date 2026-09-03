package io.joinasr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * One theme, always dark. The design has no light variant, and a screen whose
 * job is to sit on top of another app has to be predictable — following the
 * system theme would give a reader two different block screens depending on
 * a setting they set months ago.
 *
 * Material's scheme is filled in so that any Material component pulled in
 * later is already the right colour; the app's own components read AsrColors
 * directly, which keeps a screen's intent readable at the call site.
 */
@Composable
fun AsrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AsrColors.Accent,
            onPrimary = AsrColors.OnAccent,
            background = AsrColors.Background,
            onBackground = AsrColors.TextPrimary,
            surface = AsrColors.Surface,
            onSurface = AsrColors.TextPrimary,
            outline = AsrColors.SurfaceBorder,
        ),
        typography = Typography(
            bodyLarge = AsrType.Body,
            bodyMedium = AsrType.Field,
            labelLarge = AsrType.Label,
        ),
        content = content,
    )
}
