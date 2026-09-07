package io.joinasr.app.enforcement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.floorStore: DataStore<Preferences> by preferencesDataStore(name = "asr_usage_floor")

/**
 * The minutes a day has already had, kept where Android cannot take them
 * back.
 *
 * Every figure this app measures comes from `UsageStatsManager`, and Android
 * throws a package's events away when that package is uninstalled. So
 * uninstalling a limited app and installing it again emptied its day: the
 * row went from "30 of 30 min" to "0 of 30 min", the block screen came down,
 * and the whole allowance was there to spend a second time. Two minutes'
 * work, repeatable as often as somebody liked, and the witnesses were told
 * nothing because as far as this phone could see nothing had been used.
 *
 * This is the other copy. Each day's highest reading per app is written here
 * -- in this app's own storage, which an uninstall of *another* app cannot
 * reach -- and every reading afterwards is taken as at least that. Foreground
 * time only ever accumulates within a day, so a reading that has gone down
 * is not a person who used less; it is a measurement that lost its memory,
 * and the honest answer is the one that was already true.
 *
 * It repairs more than the reinstall: usage access revoked and granted again,
 * the system trimming its event log, this app's process restarting on a
 * phone whose events have been cleared. All of them read as a day that never
 * happened, and all of them are answered the same way.
 *
 * Keyed by local day, like everything else the phone stamps, and pruned to
 * the last few. Yesterday's minutes added to today would block somebody out
 * of an app they have not opened -- the opposite failure, and a worse one.
 */
class UsageFloor(context: Context) {

    private val store = context.applicationContext.floorStore

    /**
     * Writes [measured] down for [day] and returns what the day actually
     * holds -- never less than it held before.
     *
     * Called on every pass of the enforcement loop, so it writes only when a
     * number has moved: a minute is the smallest step any of these figures
     * takes, and a poll every few seconds must not be a disk write every few
     * seconds.
     */
    suspend fun keep(day: String, measured: Map<String, Int>): Map<String, Int> {
        val stored = forDay(day)
        val highest = highest(stored, measured)
        if (highest == stored) return highest
        store.edit { preferences ->
            // Re-read inside the edit: the dashboard polls its own reader
            // beside the loop, and the later write must not undo the higher
            // figure the earlier one put down.
            val days = decode(preferences[DAYS]).toMutableMap()
            days[day] = highest(days[day].orEmpty(), highest)
            preferences[DAYS] = json.encodeToString(prune(days))
        }
        return highest
    }

    /** What [day] is known to have held, without taking a new reading. */
    suspend fun forDay(day: String): Map<String, Int> = days()[day].orEmpty()

    /** Every day still kept, for the week the progress screen draws. */
    suspend fun days(): Map<String, Map<String, Int>> = decode(store.data.first()[DAYS])

    /**
     * Forgotten with everything else of this person's when they sign out.
     * The next person to sign in on this handset must not inherit a day.
     */
    suspend fun clear() {
        store.edit { it.remove(DAYS) }
    }

    private fun decode(stored: String?): Map<String, Map<String, Int>> {
        if (stored.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Map<String, Int>>>(stored) }
            .getOrDefault(emptyMap())
    }

    /**
     * The newest days, and no more. Days are `YYYY-MM-DD`, which sorts by
     * date as a string, so this needs no calendar.
     */
    private fun prune(days: Map<String, Map<String, Int>>): Map<String, Map<String, Int>> =
        if (days.size <= DAYS_KEPT) days else days.toSortedMap().entries.takeLast(DAYS_KEPT).associate { it.toPair() }

    companion object {

        /**
         * The higher of the two figures for every app in either.
         *
         * Not the sum. These are two accounts of the same minutes -- what the
         * system can still remember, and what this app wrote down while it
         * could -- so adding them would count the same afternoon twice. And
         * not the newer one: that is exactly the reading an uninstall has
         * just emptied.
         */
        fun highest(kept: Map<String, Int>, measured: Map<String, Int>): Map<String, Int> {
            if (kept.isEmpty()) return measured
            if (measured.isEmpty()) return kept
            val merged = kept.toMutableMap()
            for ((packageName, minutes) in measured) {
                merged[packageName] = maxOf(merged[packageName] ?: 0, minutes)
            }
            return merged
        }

        /**
         * How many days are kept: the week the progress screen draws, and one
         * spare for the day rolling over while a screen is open.
         */
        const val DAYS_KEPT = 8

        private val DAYS = stringPreferencesKey("days")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
