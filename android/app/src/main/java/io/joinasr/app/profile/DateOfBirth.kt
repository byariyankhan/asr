package io.joinasr.app.profile

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * The date of birth field, as DD / MM / YYYY.
 *
 * A masked text field rather than a date picker: Material's picker opens on
 * the current month, which is fifty years of tapping away from a birth year,
 * and it arrives with its own colours to fight. Typing eight digits is
 * faster and matches what the design draws.
 *
 * The 13-or-older rule is enforced here as well as on the server. Not
 * because the client is trusted -- it is not, and the server refuses
 * independently -- but so that somebody who is twelve is told before they
 * fill in the rest of the form.
 */
object DateOfBirth {

    const val MIN_AGE = 13
    const val MAX_AGE = 120

    /**
     * Formats whatever has been typed so far, keeping only digits and
     * inserting the separators. Called on every keystroke, so it has to be
     * total: any string in, something sensible out.
     */
    fun format(input: String): String {
        val digits = input.filter { it.isDigit() }.take(8)
        return when {
            digits.length <= 2 -> digits
            digits.length <= 4 -> "${digits.take(2)} / ${digits.drop(2)}"
            else -> "${digits.take(2)} / ${digits.substring(2, 4)} / ${digits.drop(4)}"
        }
    }

    /** True once eight digits are in, whether or not they are a real date. */
    fun isComplete(input: String): Boolean = input.count { it.isDigit() } == 8

    sealed interface Result {
        /** Ready to send: an ISO date, which is what PATCH /v1/me takes. */
        data class Valid(val iso: String) : Result

        /** Nothing to say yet -- still being typed. */
        data object Incomplete : Result

        data class Invalid(val message: String) : Result
    }

    fun validate(input: String, today: LocalDate = LocalDate.now()): Result {
        val digits = input.filter { it.isDigit() }
        if (digits.length < 8) return Result.Incomplete

        val day = digits.substring(0, 2).toInt()
        val month = digits.substring(2, 4).toInt()
        val year = digits.substring(4, 8).toInt()

        // LocalDate.of throws on 31 February and on month 13, which is the
        // check being relied on here rather than a hand-written calendar.
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            ?: return Result.Invalid("That is not a real date.")

        if (date.isAfter(today)) return Result.Invalid("That date is in the future.")

        val age = Period.between(date, today).years
        if (age < MIN_AGE) return Result.Invalid("You need to be $MIN_AGE or older to use Asr.")
        if (age > MAX_AGE) return Result.Invalid("Please check the year.")

        return Result.Valid(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
}
