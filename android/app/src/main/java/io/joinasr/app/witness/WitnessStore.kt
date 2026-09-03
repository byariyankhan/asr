package io.joinasr.app.witness

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.witnessStore: DataStore<Preferences> by
    preferencesDataStore(name = "asr_witnesses")

/**
 * The witnesses invited for the current challenge, on this phone.
 *
 * On the phone because that is where they are made: screen 08 collects a
 * relationship and hands the invite to Android's share sheet, and none of
 * that touches a server. When the server can issue and accept invites, this
 * becomes the local half of it rather than something to throw away — the
 * acceptance flag is already in the model for exactly that.
 *
 * One JSON value under one key, for the same reason the pact is: a list read
 * back half-written is not a state anything downstream should have to think
 * about.
 */
class WitnessStore(context: Context) {

    private val store = context.applicationContext.witnessStore

    val witnesses: Flow<List<Witness>> = store.data.map { decode(it[KEY]) }

    suspend fun current(): List<Witness> = witnesses.first()

    suspend fun add(relationship: String, nowMillis: Long = System.currentTimeMillis()) {
        val witness = Witness(
            id = "${nowMillis}-${relationship}",
            relationship = relationship,
            invitedAtMillis = nowMillis,
        )
        store.edit { preferences ->
            val existing = decode(preferences[KEY])
            preferences[KEY] = json.encodeToString(existing + witness)
        }
    }

    suspend fun remove(id: String) {
        store.edit { preferences ->
            val kept = decode(preferences[KEY]).filterNot { it.id == id }
            preferences[KEY] = json.encodeToString(kept)
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    /** Unreadable is empty, not a crash: see PactStore for why. */
    private fun decode(stored: String?): List<Witness> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Witness>>(stored) }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("witnesses")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
