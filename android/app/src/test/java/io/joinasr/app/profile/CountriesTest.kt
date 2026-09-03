package io.joinasr.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {

    @Test
    fun `the list is the platform's ISO set, not a copy in this app`() {
        // Enough of them that a truncated or hand-written list would fail.
        assertTrue(Countries.all.size > 200)
        assertTrue(Countries.all.all { it.value.length == 2 })
        assertTrue(Countries.all.any { it.value == "BD" })
        assertTrue(Countries.all.any { it.value == "US" })
    }

    @Test
    fun `every code is one the server would accept`() {
        // The server validates ISO 3166-1 alpha-2, upper case. A lower-case
        // or three-letter code here would be a 400 the person cannot fix.
        assertTrue(Countries.all.all { it.value == it.value.uppercase() })
    }

    @Test
    fun `no option is shown without a readable name`() {
        assertTrue(Countries.all.none { it.label.isBlank() })
    }

    @Test
    fun `an empty query returns everything, in order`() {
        val result = Countries.search("")
        assertEquals(Countries.all.size, result.size)
        assertEquals(Countries.all.map { it.label }, result.map { it.label })
    }

    @Test
    fun `searching by name works on a substring, not just a prefix`() {
        assertTrue(Countries.search("bangla").any { it.value == "BD" })
        assertTrue(Countries.search("kingdom").any { it.value == "GB" })
    }

    @Test
    fun `usa finds the United States, which shares no substring with it`() {
        assertEquals("US", Countries.search("usa").first().value)
        assertEquals("US", Countries.search("america").first().value)
    }

    @Test
    fun `uk is the United Kingdom and not Ukraine`() {
        // The bug this test exists for: a name-prefix match put Ukraine
        // first, because its name begins with those two letters.
        assertEquals("GB", Countries.search("uk").first().value)
        // One more letter and it is Ukraine again, as it should be.
        assertEquals("UA", Countries.search("ukr").first().value)
    }

    @Test
    fun `a two-letter code is treated as a code`() {
        assertEquals("BD", Countries.search("bd").first().value)
        assertEquals("BD", Countries.search("BD").first().value)
    }

    @Test
    fun `a query nobody can match returns nothing rather than everything`() {
        assertTrue(Countries.search("zzzzzz").isEmpty())
    }
}

class GendersTest {

    @Test
    fun `the values are exactly the server's enum`() {
        // backend/src/lib/schemas.ts: ["male","female","other","prefer_not_to_say"].
        // Any drift here is a 400 the person cannot do anything about.
        assertEquals(
            listOf("male", "female", "other", "prefer_not_to_say"),
            Genders.all.map { it.value },
        )
    }

    @Test
    fun `the option the design promises is there`() {
        // Figma 03 says in as many words: 'Options include "Prefer not to say".'
        assertTrue(Genders.all.any { it.label == "Prefer not to say" })
    }
}
