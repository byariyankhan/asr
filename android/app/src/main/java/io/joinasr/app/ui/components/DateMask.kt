package io.joinasr.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import io.joinasr.app.profile.DateOfBirth

/**
 * Draws eight digits as `DD / MM / YYYY` without putting the separators into
 * the value.
 *
 * This is the fix for a field nobody could type a date into. The obvious
 * approach — reformat the string in `onValueChange` and hand the formatted
 * version back — does not work with Compose's String-based text field: it
 * keeps the caret offset from before the edit, so as soon as the returned
 * text is longer than what was typed the caret is left standing in the
 * middle of the field and every further digit is inserted there. Typing
 * 14/02/2002 came out as 14 / 20 / 2002.
 *
 * A VisualTransformation has no such problem because the value never
 * changes: the field holds "14022002" and Compose is told, precisely, which
 * displayed character each stored character became. The offset mapping below
 * is that correspondence, and it is the whole of the fix.
 */
object DateMask : VisualTransformation {

    private const val SEPARATOR = " / "

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = DateOfBirth.digitsOf(text.text)
        val shown = buildString {
            digits.forEachIndexed { index, digit ->
                if (index == 2 || index == 4) append(SEPARATOR)
                append(digit)
            }
        }
        return TransformedText(AnnotatedString(shown), Mapping(digits.length, shown.length))
    }

    /**
     * Where each stored digit lands once the separators are in, and back.
     *
     * Both directions are clamped to what actually exists. Compose treats an
     * offset outside either string as a programming error and throws, and it
     * asks about offsets during selection and during backspace at the very
     * edges -- so the arithmetic being right for the ordinary case is not
     * enough.
     */
    private class Mapping(private val digits: Int, private val shownLength: Int) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            val o = offset.coerceIn(0, digits)
            val shifted = when {
                o <= 2 -> o
                o <= 4 -> o + SEPARATOR.length
                else -> o + SEPARATOR.length * 2
            }
            return shifted.coerceIn(0, shownLength)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val t = offset.coerceIn(0, shownLength)
            // A caret inside a separator belongs to the digit before it: the
            // three characters of " / " are not somewhere a person can stand.
            val original = when {
                t <= 2 -> t
                t <= 7 -> (t - SEPARATOR.length).coerceIn(2, 4)
                else -> (t - SEPARATOR.length * 2).coerceIn(4, DateOfBirth.DIGITS)
            }
            return original.coerceIn(0, digits)
        }
    }
}
