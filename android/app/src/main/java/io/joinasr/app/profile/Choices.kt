package io.joinasr.app.profile

import java.util.Locale

/** One option in a select field: what the server stores, and what a person reads. */
data class Choice(val value: String, val label: String)

/**
 * The country list comes from the platform, not from a table in this app.
 *
 * `Locale.getISOCountries()` is the ISO 3166-1 alpha-2 set Android itself
 * ships, which is what the server validates against, and `displayCountry`
 * gives the name in the reader's own language for free. A hand-copied list
 * would be 249 lines to maintain, in English only, and would drift the first
 * time a country changes its name.
 */
object Countries {
    val all: List<Choice> by lazy {
        Locale.getISOCountries()
            .map { code -> Choice(code, Locale.Builder().setRegion(code).build().displayCountry) }
            // Sorted by the label the reader sees, in their own collation, so
            // the list is alphabetical in Bengali as well as in English.
            .sortedWith(compareBy(java.text.Collator.getInstance()) { it.label })
    }

    /**
     * Matches on the name, the code, and a handful of names nobody types in
     * full. Without the aliases, "usa" and "uk" find nothing at all, which
     * reads as "my country is not in the list" -- a lesson from the sibling
     * project, where exactly that was reported as a bug.
     */
    private val aliases = mapOf(
        "usa" to "US", "us" to "US", "america" to "US",
        "uk" to "GB", "britain" to "GB", "england" to "GB",
        "uae" to "AE", "emirates" to "AE",
        "ksa" to "SA",
        "holland" to "NL",
        "burma" to "MM",
    )

    fun search(query: String): List<Choice> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        // An exactly-typed alias or code beats every name match: "uk" must be
        // the United Kingdom, not Ukraine, whose name starts with those two
        // letters.
        val exact = aliases[q] ?: q.uppercase().takeIf { it.length == 2 }
        val head = all.filter { it.value == exact }
        val rest = all.filter { it.value != exact && it.label.lowercase().contains(q) }
        return head + rest
    }
}

/**
 * The four values the server's `gender` column accepts. The labels are the
 * app's; the values are the enum in backend/src/lib/schemas.ts and cannot
 * drift from it without a 400.
 */
object Genders {
    val all = listOf(
        Choice("male", "Male"),
        Choice("female", "Female"),
        Choice("other", "Other"),
        Choice("prefer_not_to_say", "Prefer not to say"),
    )
}
