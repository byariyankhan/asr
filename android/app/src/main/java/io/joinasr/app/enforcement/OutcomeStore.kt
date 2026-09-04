package io.joinasr.app.enforcement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.outcomeStore: DataStore<Preferences> by
    preferencesDataStore(name = "asr_outcome")

/**
 * The last challenge that ended, and whether its ending has been shown.
 *
 * Two values rather than one, because they answer different questions. The
 * outcome is kept: it is what Progress history is built from. The
 * acknowledgement is what stops Figma 26 reappearing every time the app is
 * opened — being told once that the challenge failed is enough.
 */
class OutcomeStore(context: Context) {

    private val store = context.applicationContext.outcomeStore

    val outcome: Flow<PactOutcome?> = store.data.map { decode(it[KEY]) }

    /** The outcome the person has not been shown yet, if there is one. */
    val unseen: Flow<PactOutcome?> = store.data.map { preferences ->
        if (preferences[SEEN] == true) null else decode(preferences[KEY])
    }

    suspend fun current(): PactOutcome? = outcome.first()

    suspend fun save(outcome: PactOutcome) {
        store.edit {
            it[KEY] = json.encodeToString(outcome)
            it[SEEN] = false
        }
    }

    suspend fun markSeen() {
        store.edit { it[SEEN] = true }
    }

    /** Marks the stored outcome as having reached the server. */
    suspend fun markReported() {
        store.edit { preferences ->
            val stored = decode(preferences[KEY]) ?: return@edit
            preferences[KEY] = json.encodeToString(stored.copy(reported = true))
        }
    }

    private fun decode(stored: String?): PactOutcome? {
        if (stored.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<PactOutcome>(stored) }.getOrNull()
    }

    private companion object {
        val KEY = stringPreferencesKey("outcome")
        val SEEN = booleanPreferencesKey("seen")

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
