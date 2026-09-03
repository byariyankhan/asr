package io.joinasr.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
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
 * also means a 12MP original never crosses the network: whatever comes in,
 * about 40KB goes out.
 *
 * **Any image the phone can open is accepted, at any file size.** The size
 * that matters is the size after this runs, and that is fixed. From Android
 * 9 the decode goes through ImageDecoder, which handles HEIC — the default
 * camera format on most modern phones — along with WEBP and AVIF, none of
 * which BitmapFactory can be relied on for. BitmapFactory is the path on
 * older versions and the second attempt when ImageDecoder refuses.
 */
object PhotoPrep {

    /** Quality 85: past this the file grows fast for a 512px image and the
     *  difference is not visible in a 68dp circle. */
    private const val JPEG_QUALITY = 85

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
            val decoded = decode(context, uri)
                ?: return@withContext Result.Failed("That photo could not be opened.")
            Result.Ok(squareJpeg(context, uri, decoded))
        } catch (e: OutOfMemoryError) {
            // A very large image on a small phone. Reported as an ordinary
            // failure because to the person it is one.
            Result.Failed("That photo is too large for this phone to open.")
        } catch (e: Exception) {
            Result.Failed("That photo could not be opened.")
        }
    }

    /**
     * The decode, by whichever route this version of Android has.
     *
     * ImageDecoder is tried first from API 28 and is the reason a HEIC out of
     * the camera works at all. It is allowed to fail — a corrupt file, a
     * format even it does not know — and BitmapFactory gets a turn after it,
     * because two attempts cost a moment and a refused photo costs the person
     * the only thing this screen asks them for.
     */
    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.setTargetSampleSize(
                        PhotoScaling.sampleSizeFor(info.size.width, info.size.height),
                    )
                    // Software, because these pixels are read back to crop
                    // and compress, and a hardware bitmap cannot be.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return decodeWithBitmapFactory(context, uri)
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        // The stream is what is null-checked here, not the decode. Decoding
        // with inJustDecodeBounds returns null by design, so an elvis over
        // the whole expression rejects every image ever chosen -- which is
        // precisely what this did until somebody tried to use it, and is why
        // the screen answered "that file is not an image" to every photo on
        // the phone.
        val opened = resolver.openInputStream(uri) ?: return null
        opened.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoScaling.sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun squareJpeg(context: Context, uri: Uri, decoded: Bitmap): ByteArray {
        // A separate stream: decoding consumed the first one, and the
        // orientation tag has to be read from the original file rather than
        // from the bitmap, which does not carry it. androidx's ExifInterface,
        // not the framework's, which cannot read HEIC.
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PhotoScaling.rotationFor(
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    ),
                )
            }
        }.getOrNull() ?: 0

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

        val scaled = if (square.width <= PhotoScaling.TARGET_EDGE) {
            // Never upscaled: a 200px photo blown up to 512 is a bigger file
            // that looks worse.
            square
        } else {
            Bitmap.createScaledBitmap(
                square,
                PhotoScaling.TARGET_EDGE,
                PhotoScaling.TARGET_EDGE,
                true,
            ).also { if (it != square) square.recycle() }
        }

        val out = ByteArrayOutputStream()
        // The re-encode is what guarantees the server sees a JPEG with no
        // metadata in it, whatever the picker handed over -- a HEIC, a PNG
        // screenshot, or a photo full of GPS coordinates.
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        scaled.recycle()
        return out.toByteArray()
    }
}
