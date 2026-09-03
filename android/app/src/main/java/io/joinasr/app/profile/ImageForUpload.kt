package io.joinasr.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns whatever the photo picker returned into the JPEG the server accepts.
 *
 * All of this happens on the phone on purpose. The phone already has an
 * image decoder; the server does not, and giving it one means a native
 * dependency inside a Next standalone build, which this project has already
 * been bitten by twice. Doing it here also saves the person's data: a 4MB
 * camera photo becomes about 40KB before it leaves the device.
 *
 * Three things happen, and each is load-bearing:
 *
 *  - **Orientation.** A phone photo is usually stored sideways with an EXIF
 *    tag saying which way is up. The server strips EXIF, so if the rotation
 *    were left to the tag the face would arrive on its side. It is applied to
 *    the pixels here instead.
 *  - **Square crop.** The avatar is drawn in a circle everywhere it appears.
 *    Cropping to a centre square here means the circle never squashes anyone.
 *  - **Re-encode.** The bytes that go up are ours, not the file's. Whatever
 *    metadata, colour profile or oddity the original carried is gone by
 *    construction rather than by filtering.
 */
object ImageForUpload {

    /** The circle is never drawn larger than this, and the server refuses
     *  anything over 1024 on a side. */
    const val EDGE = 512

    private const val QUALITY = 85

    sealed interface Result {
        data class Ok(val jpeg: ByteArray) : Result {
            // ByteArray in a data class: equals would compare references.
            // Nothing compares these, and saying so beats a lint suppression.
            override fun equals(other: Any?): Boolean = this === other
            override fun hashCode(): Int = System.identityHashCode(this)
        }

        data class Failed(val message: String) : Result
    }

    suspend fun prepare(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val bounds = readBounds(context, uri)
                ?: return@withContext Result.Failed("That file is not an image.")
            val decoded = decodeDownsampled(context, uri, bounds)
                ?: return@withContext Result.Failed("That image could not be read.")
            val upright = applyOrientation(context, uri, decoded)
            val square = cropToSquare(upright)
            val scaled = scaleTo(square, EDGE)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            for (bitmap in setOf(decoded, upright, square, scaled)) bitmap.recycle()
            Result.Ok(out.toByteArray())
        } catch (e: OutOfMemoryError) {
            // A very large image on a small phone. Reported as a normal
            // failure because to the person it is one.
            Result.Failed("That photo is too large for this device to open.")
        } catch (e: Exception) {
            Result.Failed("That image could not be read.")
        }
    }

    private fun readBounds(context: Context, uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return if (options.outWidth > 0 && options.outHeight > 0) options else null
    }

    /**
     * Decodes at a power-of-two fraction close to the size actually needed.
     * Decoding a 12-megapixel photo at full size to then throw 98% of it
     * away is how an avatar picker runs a phone out of memory.
     */
    private fun decodeDownsampled(
        context: Context,
        uri: Uri,
        bounds: BitmapFactory.Options,
    ): Bitmap? {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= EDGE && bounds.outHeight / (sample * 2) >= EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream) }.getOrNull()
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            // Selfie cameras produce these two, and a mirrored-and-rotated
            // face looks wrong in a way people notice without knowing why.
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == bitmap.height) return bitmap
        val x = (bitmap.width - edge) / 2
        val y = (bitmap.height - edge) / 2
        return Bitmap.createBitmap(bitmap, x, y, edge, edge)
    }

    private fun scaleTo(bitmap: Bitmap, edge: Int): Bitmap {
        // Never upscaled: a 200px photo blown up to 512 is a bigger file that
        // looks worse.
        if (bitmap.width <= edge) return bitmap
        return Bitmap.createScaledBitmap(bitmap, edge, edge, true)
    }
}
