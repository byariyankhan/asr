package io.joinasr.app.profile

import java.util.Locale

/**
 * The country list, from the platform rather than from a table shipped in
 * the app.
 *
 * `Locale.getISOCountries()` is ISO 3166-1 alpha-2, which is exactly what
 * PATCH /v1/me validates against, and the display names come out in the
 * reader's own language for free. A hand-written list would be another thing
 * to keep current, and would be wrong about names the moment a country
 * renames itself.
 */
object Countries {

    data class Country(val code: String, val name: String)

    /** Sorted by name in the reader's locale, so the order matches the
     *  alphabet they are scanning with.
     *
     *  Locale.of() is Java 19 and only reached Android at API 36; minSdk here
     *  is 26, so the deprecated constructor is the one that exists on every
     *  device this app runs on. */
    @Suppress("DEPRECATION")
    val all: List<Country> by lazy {
        val display = Locale.getDefault()
        Locale.getISOCountries()
            .map { code -> Country(code, Locale("", code).getDisplayCountry(display)) }
            // A code with no name in this locale would render as the raw two
            // letters; showing "ZZ" in a picker is worse than omitting it.
            .filter { it.name.isNotBlank() && it.name != it.code }
            .sortedBy { it.name.lowercase(display) }
    }

    fun nameOf(code: String?): String? =
        code?.let { c -> all.firstOrNull { it.code == c }?.name }

    /**
     * Substring match on the name, plus an exact match on the code.
     *
     * 249 entries is too many to scroll, and the sibling project learned the
     * rest of this the hard way: matching only names means "usa" finds
     * nothing at all, because the ISO name is "United States". Codes are
     * matched exactly so that typing "in" does not put India above every
     * country with "in" in its name.
     */
    fun search(query: String): List<Country> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val lower = q.lowercase(Locale.getDefault())
        val exactCode = all.firstOrNull { it.code.equals(q, ignoreCase = true) }
        val byName = all.filter { it.name.lowercase(Locale.getDefault()).contains(lower) }
        return if (exactCode != null && !byName.contains(exactCode)) {
            listOf(exactCode) + byName
        } else if (exactCode != null) {
            listOf(exactCode) + byName.filter { it != exactCode }
        } else {
            byName
        }
    }
}

/**
 * The four values PATCH /v1/me accepts, with the wording the design asks
 * for. The wire value is the enum name the server validates; the label is
 * what a person reads, and the two are kept together so they cannot drift.
 */
enum class Gender(val wire: String, val label: String) {
    Male("male", "Male"),
    Female("female", "Female"),
    Other("other", "Other"),
    PreferNotToSay("prefer_not_to_say", "Prefer not to say"),
    ;

    companion object {
        fun fromWire(value: String?): Gender? = entries.firstOrNull { it.wire == value }
    }
}
