package io.joinasr.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoScalingTest {

    @Test
    fun `a photo already at or below the target is not subsampled`() {
        assertEquals(1, PhotoScaling.sampleSizeFor(512, 512))
        assertEquals(1, PhotoScaling.sampleSizeFor(600, 520))
        assertEquals(1, PhotoScaling.sampleSizeFor(300, 300))
    }

    @Test
    fun `a phone camera photo is subsampled, never below the target`() {
        // 12MP, 4:3. 3024 / 4 = 756, still >= 512; / 8 = 378, too small.
        assertEquals(4, PhotoScaling.sampleSizeFor(4032, 3024))
        assertTrue(3024 / 4 >= PhotoScaling.TARGET_EDGE)
    }

    @Test
    fun `the short side is what decides, not the long one`() {
        // A panorama: subsampling on the long side would leave the short one
        // below 512 and the result would be upscaled, which looks worse than
        // not resizing at all.
        assertEquals(1, PhotoScaling.sampleSizeFor(8000, 600))
        assertEquals(2, PhotoScaling.sampleSizeFor(8000, 1100))
    }

    @Test
    fun `the sample size is always a power of two, as BitmapFactory requires`() {
        for (w in listOf(513, 1000, 1023, 1024, 2000, 4032, 9999)) {
            val s = PhotoScaling.sampleSizeFor(w, w)
            assertEquals("$w gave $s", 0, s and (s - 1))
        }
    }

    @Test
    fun `a square crop is centred and as large as the image allows`() {
        assertEquals(PhotoScaling.Crop(0, 0, 500), PhotoScaling.centreSquare(500, 500))
        // Landscape: trimmed left and right equally.
        assertEquals(PhotoScaling.Crop(250, 0, 500), PhotoScaling.centreSquare(1000, 500))
        // Portrait: trimmed top and bottom equally, which keeps a face near
        // the middle rather than cutting the top of a head off.
        assertEquals(PhotoScaling.Crop(0, 250, 500), PhotoScaling.centreSquare(500, 1000))
    }

    @Test
    fun `an odd dimension does not push the crop outside the image`() {
        val crop = PhotoScaling.centreSquare(1001, 500)
        assertTrue(crop.x + crop.size <= 1001)
        assertTrue(crop.y + crop.size <= 500)
    }

    @Test
    fun `EXIF orientations map to the rotation that makes them upright`() {
        assertEquals(0, PhotoScaling.rotationFor(1)) // normal
        assertEquals(90, PhotoScaling.rotationFor(6))
        assertEquals(180, PhotoScaling.rotationFor(3))
        assertEquals(270, PhotoScaling.rotationFor(8))
    }

    @Test
    fun `a mirrored or missing orientation is left alone rather than guessed`() {
        // 2, 4, 5, 7 are flips. Rotating them would be wrong in a different
        // way; 0 is undefined. None of them is worth guessing at.
        for (tag in listOf(0, 2, 4, 5, 7, 99)) {
            assertEquals("tag $tag", 0, PhotoScaling.rotationFor(tag))
        }
    }

    @Test
    fun `zero or negative dimensions are a bug, not a case to handle`() {
        for (pair in listOf(0 to 100, 100 to 0, -1 to 10)) {
            try {
                PhotoScaling.sampleSizeFor(pair.first, pair.second)
                assertTrue("expected a throw for $pair", false)
            } catch (expected: IllegalArgumentException) {
                // A decoder that reports 0x0 failed to read the file; the
                // caller has to say so, not divide by it.
            }
        }
    }
}
