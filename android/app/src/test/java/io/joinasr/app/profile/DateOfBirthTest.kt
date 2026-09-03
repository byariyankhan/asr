package io.joinasr.app.profile

import io.joinasr.app.profile.DateOfBirth.Result
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateOfBirthTest {

    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `the value is the digits and nothing else`() {
        assertEquals("", DateOfBirth.digitsOf(""))
        assertEquals("29", DateOfBirth.digitsOf("29"))
        assertEquals("29022000", DateOfBirth.digitsOf("29022000"))
    }

    @Test
    fun `it ignores anything that is not a digit, wherever it comes from`() {
        // Paste, a keyboard that inserts its own spaces, or text that has
        // already been through the display mask and come back.
        assertEquals("29022000", DateOfBirth.digitsOf("29 / 02 / 2000"))
        assertEquals("29022000", DateOfBirth.digitsOf("29-02-2000"))
        // Letters are dropped, so "29feb2000" leaves six digits, not eight.
        assertEquals("292000", DateOfBirth.digitsOf("abc29feb2000xyz"))
    }

    @Test
    fun `it stops at eight digits rather than growing forever`() {
        assertEquals("29022000", DateOfBirth.digitsOf("290220001234"))
    }

    @Test
    fun `says nothing until all eight digits are in`() {
        assertEquals(Result.Incomplete, DateOfBirth.validate("290220", today))
        assertFalse(DateOfBirth.isComplete("2902200"))
        assertTrue(DateOfBirth.isComplete("29022000"))
    }

    @Test
    fun `accepts a real date and hands back the ISO form the server wants`() {
        // A leap day, which is the case a hand-written calendar check gets
        // wrong: 2000 is a leap year because it divides by 400.
        assertEquals(Result.Valid("2000-02-29"), DateOfBirth.validate("29022000", today))
        assertEquals(Result.Valid("1990-12-31"), DateOfBirth.validate("31121990", today))
    }

    @Test
    fun `refuses a day that does not exist in that month`() {
        assertTrue(DateOfBirth.validate("29022001", today) is Result.Invalid)
        assertTrue(DateOfBirth.validate("31042000", today) is Result.Invalid)
        assertTrue(DateOfBirth.validate("00012000", today) is Result.Invalid)
        assertTrue(DateOfBirth.validate("01132000", today) is Result.Invalid)
    }

    @Test
    fun `refuses the future`() {
        val result = DateOfBirth.validate("04092026", today)
        assertTrue(result is Result.Invalid)
        assertTrue((result as Result.Invalid).message.contains("future"))
    }

    @Test
    fun `enforces thirteen, to the day`() {
        // Turns 13 tomorrow: still too young today.
        assertTrue(DateOfBirth.validate("04092013", today) is Result.Invalid)
        // Turns 13 today: allowed.
        assertEquals(Result.Valid("2013-09-03"), DateOfBirth.validate("03092013", today))
    }

    @Test
    fun `says what to do about being too young, not just that it is wrong`() {
        val result = DateOfBirth.validate("01012020", today) as Result.Invalid
        assertTrue(result.message.contains("13"))
    }

    @Test
    fun `refuses an implausible year rather than storing it`() {
        assertTrue(DateOfBirth.validate("01011850", today) is Result.Invalid)
    }
}
