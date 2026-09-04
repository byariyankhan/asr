package io.joinasr.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.joinasr.app.apps.InstalledApps
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * An installed app, shown as itself.
 *
 * Several screens were drawing the first letter of the app's name in a box —
 * "X" for X, "I" for Instagram — which is what this falls back to and was
 * never meant to be the normal case. A person recognises TikTok by its
 * logo in a tenth of the time they recognise the word, and on a screen whose
 * whole job is to say *which app* is out of time, that is most of the work
 * the screen does.
 *
 * The letter stays for the app that has since been uninstalled, or whose
 * icon the package manager refuses: the row still has to say something.
 */
@Composable
fun AsrAppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    corner: Dp = 16.dp,
) {
    val shape = RoundedCornerShape(corner)
    val context = LocalContext.current
    // Keyed on the package: the same composable moving between apps reloads,
    // and one that stays put does not.
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = InstalledApps.icon(context, packageName)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(AsrColors.Background)
            .border(1.dp, AsrColors.FieldBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                // Named, because the row's own text is not attached to the
                // image and a screen reader would otherwise read a blank.
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                label.trim().take(1).uppercase().ifBlank { "?" },
                style = AsrType.display(18),
                color = AsrColors.Accent,
            )
        }
    }
}
