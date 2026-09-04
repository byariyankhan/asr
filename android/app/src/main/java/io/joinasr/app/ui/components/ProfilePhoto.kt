package io.joinasr.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import io.joinasr.app.data.Api
import io.joinasr.app.ui.theme.AsrColors
import io.joinasr.app.ui.theme.AsrType

/**
 * Somebody's profile photo, or the first letter of their name.
 *
 * This did not exist. Every avatar in the app drew an initial and a comment
 * saying the photo was on the server "which this app has no image loader
 * for yet" -- so uploading one worked, changed the row in the database,
 * put the object in the bucket, and produced no visible effect anywhere.
 * From the outside that is indistinguishable from an upload that fails
 * silently, which is what it was reported as.
 *
 * Forty lines rather than an image-loading library, because this app shows
 * one kind of image: a square JPEG of at most 1024px that the client itself
 * sized before uploading. No transformations, no placeholders to configure,
 * no disk cache to invalidate.
 *
 * The cache can be by key alone because the key is not stable: replacing a
 * photo writes a new random object name, so a changed photo is a different
 * key and cannot be served stale. That is what the random half of the key
 * was for.
 */
private val cache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
fun AsrProfilePhoto(
    imagePath: String?,
    fallback: String,
    size: Dp,
    modifier: Modifier = Modifier,
    initialSize: Int = 20,
) {
    val photo by produceState<ImageBitmap?>(cache.get(imagePath.orEmpty()), imagePath) {
        val path = imagePath
        if (path.isNullOrBlank()) {
            value = null
            return@produceState
        }
        cache.get(path)?.let {
            value = it
            return@produceState
        }
        val bytes = Api.media.bytes(path) ?: return@produceState
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@produceState
        val image = bitmap.asImageBitmap()
        cache.put(path, image)
        value = image
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AsrColors.Field)
            .border(1.dp, AsrColors.FieldBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = photo
        if (loaded == null) {
            // Shown while it loads as well as when there is none. A spinner
            // for a 50KB image is a flicker, and the initial is what the
            // design falls back to anyway.
            Text(
                fallback.trim().take(1).uppercase().ifBlank { "?" },
                style = AsrType.display(initialSize),
                color = AsrColors.Accent,
            )
        } else {
            Image(
                bitmap = loaded,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
