package io.joinasr.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateMaskTest {

    private fun shown(value: String) = DateMask.filter(AnnotatedString(value)).text.text

    private fun mapping(value: String) = DateMask.filter(AnnotatedString(value)).offsetMapping

    @Test
    fun `separators appear as the digits arrive`() {
        assertEquals("", shown(""))
        assertEquals("1", shown("1"))
        assertEquals("14", shown("14"))
        assertEquals("14 / 0", shown("140"))
        assertEquals("14 / 02", shown("1402"))
        assertEquals("14 / 02 / 2", shown("14022"))
        assertEquals("14 / 02 / 2002", shown("14022002"))
    }

    @Test
    fun `it shows only digits, and only eight of them`() {
        assertEquals("14 / 02 / 2002", shown("14 / 02 / 2002"))
        assertEquals("14 / 02 / 2002", shown("14022002999"))
        assertEquals("14 / 02 / 2002", shown("14-02-2002"))
    }

    @Test
    fun `the caret lands after the digit it was after`() {
        // The whole point. Typing the third digit must put the caret after
        // it, past the separator that just appeared, or the fourth digit
        // goes somewhere else -- which is how 14/02/2002 became 14/20/2002.
        val map = mapping("14022002")
        assertEquals(0, map.originalToTransformed(0))
        assertEquals(2, map.originalToTransformed(2))
        assertEquals(6, map.originalToTransformed(3)) // after "14 / 0"
        assertEquals(7, map.originalToTransformed(4)) // after "14 / 02"
        assertEquals(11, map.originalToTransformed(5)) // after "14 / 02 / 2"
        assertEquals(14, map.originalToTransformed(8)) // the end
    }

    @Test
    fun `a caret inside a separator belongs to the digit before it`() {
        // Nobody can stand between the space and the slash, so a tap there
        // has to resolve to a real position in the value.
        val map = mapping("14022002")
        assertEquals(2, map.transformedToOriginal(3))
        assertEquals(2, map.transformedToOriginal(4))
        assertEquals(2, map.transformedToOriginal(5))
        assertEquals(3, map.transformedToOriginal(6))
        assertEquals(4, map.transformedToOriginal(9))
        assertEquals(8, map.transformedToOriginal(14))
    }

    @Test
    fun `every offset either way is inside the string it points at`() {
        // Compose treats an offset past the end as a programming error and
        // throws, and it asks about the edges during selection and during a
        // backspace. Arithmetic that is right for the ordinary case is not
        // enough; this walks every state of the field.
        for (length in 0..8) {
            val value = "14022002".take(length)
            val transformed = DateMask.filter(AnnotatedString(value))
            val map = transformed.offsetMapping
            val shownLength = transformed.text.text.length

            for (offset in 0..length) {
                val out = map.originalToTransformed(offset)
                assertTrue("original $offset of $length -> $out", out in 0..shownLength)
            }
            for (offset in 0..shownLength) {
                val out = map.transformedToOriginal(offset)
                assertTrue("transformed $offset of $shownLength -> $out", out in 0..length)
            }
        }
    }

    @Test
    fun `the caret at the end of the value is the end of the display`() {
        // Where it sits after every keystroke, so it is the one that must
        // never be off by even one.
        for (length in 0..8) {
            val value = "14022002".take(length)
            val transformed = DateMask.filter(AnnotatedString(value))
            assertEquals(
                "after $length digits",
                transformed.text.text.length,
                transformed.offsetMapping.originalToTransformed(length),
            )
        }
    }
}
