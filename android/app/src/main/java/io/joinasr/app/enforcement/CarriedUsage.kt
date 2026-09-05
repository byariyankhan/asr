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
import java.time.Instant
import java.time.ZoneId

private val Context.carriedStore: DataStore<Preferences> by preferencesDataStore(name = "asr_carried")

/**
 * Minutes spent today on a phone that is not this one.
 *
 * A day belongs to the person, and a phone can only measure its own screen.
 * So a challenge arriving on a new handset arrives with the day already
 * half gone, and nothing on this phone can see that: thirty minutes of
 * Instagram on the old one, sign in here, and the counter reads zero. It was
 * a fresh allowance for the cost of signing in, once per phone, every day.
 *
 * These minutes are added to what this phone measures, for one named day
 * only. A figure from yesterday added to today would block somebody out of
 * an app they have not opened, which is the opposite failure and a worse
 * one.
 *
 * Resolved late on purpose -- by the enforcement loop rather than at
 * sign-in. The subtraction below needs this phone's own reading for today,
 * and that reading is zero until usage access is granted, which on a new
 * install happens after the challenge has already arrived.
 */
class CarriedUsage(context: Context) {

    private val store = context.applicationContext.carriedStore

    /**
     * Says that this phone has taken a challenge over and does not yet know
     * what the day already holds.
     *
     * The day is stamped in: somebody who signs in at ten to midnight and
     * opens the app the next morning is owed a clean day, not last night's
     * total.
     */
    suspend fun expect(day: String) {
        store.edit {
            it[PENDING_DAY] = day
            it.remove(CARRIED_DAY)
            it.remove(CARRIED)
        }
    }

    /** The day still waiting for an answer, if it is [today]. */
    suspend fun pendingFor(today: String): Boolean = store.data.first()[PENDING_DAY] == today

    /**
     * What was spent elsewhere today: the whole day as the server has it,
     * less what this phone can already see of it.
     *
     * The subtraction is the point. A reinstall on the *same* phone reads
     * back a total that includes this handset's own morning, and adding that
     * to a counter which is about to measure the same morning again would
     * double it.
     */
    suspend fun resolve(day: String, serverTotals: Map<String, Int>, ownSoFar: Map<String, Int>) {
        val elsewhere = elsewhere(serverTotals, ownSoFar)
        store.edit {
            it.remove(PENDING_DAY)
            it[CARRIED_DAY] = day
            it[CARRIED] = json.encodeToString(elsewhere)
        }
    }

    /** The carried minutes for [day], or nothing when they belong to another one. */
    suspend fun forDay(day: String): Map<String, Int> {
        val preferences = store.data.first()
        if (preferences[CARRIED_DAY] != day) return emptyMap()
        val stored = preferences[CARRIED] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Int>>(stored) }.getOrDefault(emptyMap())
    }

    suspend fun clear() {
        store.edit {
            it.remove(PENDING_DAY)
            it.remove(CARRIED_DAY)
            it.remove(CARRIED)
        }
    }

    companion object {

        /**
         * The day's total, less this phone's own share of it.
         *
         * Negative differences are dropped rather than subtracted. This
         * phone's reading can legitimately be ahead of the server's -- the
         * last summary is up to five minutes old -- and a negative carry
         * would hand back minutes that were spent.
         */
        fun elsewhere(serverTotals: Map<String, Int>, ownSoFar: Map<String, Int>): Map<String, Int> =
            serverTotals
                .mapValues { (packageName, total) -> total - (ownSoFar[packageName] ?: 0) }
                .filterValues { it > 0 }

        /** The local day as the server writes it: YYYY-MM-DD, in this zone. */
        fun today(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String =
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

        private val PENDING_DAY = stringPreferencesKey("pending_day")
        private val CARRIED_DAY = stringPreferencesKey("carried_day")
        private val CARRIED = stringPreferencesKey("carried")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
