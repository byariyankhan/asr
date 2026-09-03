package io.joinasr.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns whatever the photo picker returned into the bytes the server accepts:
 * an upright, centre-cropped, 512px JPEG.
 *
 * All of this happens on the phone on purpose. The server refuses anything
 * but a small JPEG and strips EXIF without decoding, so it cannot rotate an
 * image — if the client did not apply the orientation tag, a portrait photo
 * from most phones would arrive sideways and stay that way. Doing it here
 * also means a 12MP original never crosses the network.
 */
object PhotoPrep {

    /** Quality 85: past this the file grows fast for a 512px image and the
     *  difference is not visible in a 68dp circle. */
    private const val JPEG_QUALITY = 85

    class Unreadable(message: String) : Exception(message)

    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw Unreadable("That photo could not be opened.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw Unreadable("That file is not an image we can read.")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoScaling.sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw Unreadable("That photo could not be opened.")

        // A separate stream: decoding consumed the first one, and the
        // orientation tag has to be read from the original file rather than
        // from the bitmap, which does not carry it.
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            PhotoScaling.rotationFor(
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                ),
            )
        } ?: 0

        val upright = if (rotation == 0) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true,
            ).also { if (it != decoded) decoded.recycle() }
        }

        val crop = PhotoScaling.centreSquare(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, crop.x, crop.y, crop.size, crop.size)
            .also { if (it != upright) upright.recycle() }

        val scaled = if (square.width == PhotoScaling.TARGET_EDGE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, PhotoScaling.TARGET_EDGE, PhotoScaling.TARGET_EDGE, true)
                .also { if (it != square) square.recycle() }
        }

        val out = ByteArrayOutputStream()
        // The re-encode is what guarantees the server sees a JPEG with no
        // metadata in it, whatever the picker handed over -- a HEIC, a PNG
        // screenshot, or a photo full of GPS coordinates.
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        scaled.recycle()
        out.toByteArray()
    }
}
