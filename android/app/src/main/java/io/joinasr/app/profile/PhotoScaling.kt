package io.joinasr.app.profile

/**
 * The arithmetic behind preparing a photo for upload, kept separate from the
 * Android decoder so it can be tested at all: everything in `PhotoPrep` is
 * Bitmap and streams, which needs a device, and this needs nothing.
 */
object PhotoScaling {

    /** What the server accepts, and what the app sends. */
    const val TARGET_EDGE = 512

    /**
     * The power-of-two subsample BitmapFactory takes, chosen so the decoded
     * bitmap is the smallest one still at least [target] on its short side.
     *
     * Decoding a 12-megapixel photo at full size to make a 512px square is
     * 48MB of heap for something thrown away a line later, and on a cheap
     * phone that is the OutOfMemoryError people report as "the app crashes
     * when I pick a photo".
     */
    fun sampleSizeFor(width: Int, height: Int, target: Int = TARGET_EDGE): Int {
        require(width > 0 && height > 0) { "not an image: ${width}x$height" }
        var sample = 1
        // Halve while the short side would still be big enough afterwards.
        while (minOf(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** A square, centred: the design draws the photo in a circle, and a
     *  non-square bitmap in a circle crops arbitrarily at display time. */
    data class Crop(val x: Int, val y: Int, val size: Int)

    fun centreSquare(width: Int, height: Int): Crop {
        require(width > 0 && height > 0) { "not an image: ${width}x$height" }
        val size = minOf(width, height)
        return Crop(x = (width - size) / 2, y = (height - size) / 2, size = size)
    }

    /**
     * Degrees to rotate for an EXIF orientation tag.
     *
     * This has to happen on the phone, because the server strips EXIF and
     * cannot rotate: it never decodes the image. Skip this and a photo taken
     * in portrait on most phones arrives sideways and stays sideways.
     */
    fun rotationFor(exifOrientation: Int): Int = when (exifOrientation) {
        6 -> 90 // ORIENTATION_ROTATE_90
        3 -> 180 // ORIENTATION_ROTATE_180
        8 -> 270 // ORIENTATION_ROTATE_270
        else -> 0
    }
}
