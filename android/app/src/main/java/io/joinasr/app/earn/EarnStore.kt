package io.joinasr.app.earn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.joinasr.app.usage.Day
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

private val Context.earnStore: DataStore<Preferences> by preferencesDataStore(name = "asr_earn")

/**
 * The activity being attempted, and what today's attempts have won.
 *
 * Both live here rather than in the pact, because the pact is the thing that
 * cannot change and this changes all day. The enforcement loop reads the
 * earned minutes on every pass, which is the only reason this file exists:
 * time that is earned and not applied is a reward nobody receives.
 *
 * Earned minutes are stamped with the local date and read back through
 * [earnedToday], which returns nothing once the date has moved on. Bonus
 * time is for the day it was earned; carrying it forward would turn a walk
 * into a bank balance.
 */
class EarnStore internal constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.earnStore)

    val active: Flow<EarnActivity?> = store.data.map { decodeActive(it[ACTIVE]) }

    val completed: Flow<EarnActivity?> = store.data.map { decodeActive(it[COMPLETED]) }

    suspend fun acknowledgeCompleted() {
        store.edit { it.remove(COMPLETED) }
    }

    suspend fun currentActive(): EarnActivity? = active.first()

    suspend fun start(activity: EarnActivity) {
        store.edit {
            it[ACTIVE] = json.encodeToString(activity)
            it.remove(COMPLETED)
        }
    }

    suspend fun update(activity: EarnActivity) {
        store.edit {
            if (decodeActive(it[ACTIVE])?.id == activity.id) {
                it[ACTIVE] = json.encodeToString(activity)
            }
        }
    }

    suspend fun clearActive(id: String? = null) {
        store.edit {
            if (id == null || decodeActive(it[ACTIVE])?.id == id) it.remove(ACTIVE)
        }
    }

    /**
     * Today's earned minutes per package, or nothing if the stored figures
     * belong to a day that has ended.
     *
     * Re-read at midnight as well as on every write. A screen left open
     * across it used to keep showing yesterday's bonus in today's
     * allowance -- "5 of 40 min" against a limit of 30 -- while the loop,
     * which asks afresh on every pass, was already enforcing the 30.
     */
    val earned: Flow<EarnedToday> = combine(store.data, midnights()) { preferences, _ ->
        val stored = decodeEarned(preferences[EARNED])
        val today = today()
        if (stored == null || stored.day != today) EarnedToday(today) else stored
    }

    /** Emits once now, then once each time the local date changes. */
    private fun midnights(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val now = System.currentTimeMillis()
            delay((Day.nextMidnight(now) - now).coerceAtLeast(1_000L) + 1_000L)
        }
    }

    suspend fun earnedToday(): EarnedToday = earned.first()

    /** Claim and reward together: cancellation or duplicate ticks cannot award twice. */
    suspend fun complete(activity: EarnActivity): Boolean {
        var awarded = false
        store.edit { preferences ->
            if (decodeActive(preferences[ACTIVE])?.id != activity.id) return@edit
            if (!activity.isComplete) return@edit
            val today = today()
            val stored = decodeEarned(preferences[EARNED])
                ?.takeIf { it.day == today }
                ?: EarnedToday(today)
            val already = stored.forPackage(activity.packageName)
            val capped = (already + activity.rewardMinutes).coerceAtMost(EarnRules.DAILY_CAP_MINUTES)
            preferences[EARNED] = json.encodeToString(
                stored.copy(minutesByPackage = stored.minutesByPackage + (activity.packageName to capped)),
            )
            preferences[COMPLETED] = json.encodeToString(activity)
            preferences.remove(ACTIVE)
            awarded = true
        }
        return awarded
    }

    private fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    private fun decodeActive(stored: String?): EarnActivity? {
        if (stored.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<EarnActivity>(stored) }.getOrNull()
    }

    private fun decodeEarned(stored: String?): EarnedToday? {
        if (stored.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<EarnedToday>(stored) }.getOrNull()
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active")
        val COMPLETED = stringPreferencesKey("completed")
        val EARNED = stringPreferencesKey("earned")

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
