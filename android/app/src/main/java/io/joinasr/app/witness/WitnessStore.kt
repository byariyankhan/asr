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
 * A copy, not the truth: the server issues the invite code and owns the
 * status. This is here so the list is on screen before the first request
 * comes back and still there after one fails, which is the difference
 * between a screen that is briefly empty and one that is briefly wrong.
 *
 * One JSON value under one key, for the same reason the pact is: a list read
 * back half-written is not a state anything downstream should have to think
 * about.
 */
class WitnessStore(context: Context) {

    private val store = context.applicationContext.witnessStore

    val witnesses: Flow<List<Witness>> = store.data.map { decode(it[KEY]) }

    suspend fun current(): List<Witness> = witnesses.first()

    suspend fun add(
        id: String,
        relationship: String,
        inviteUrl: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val witness = Witness(
            id = id,
            relationship = relationship,
            invitedAtMillis = nowMillis,
            inviteUrl = inviteUrl,
        )
        store.edit { preferences ->
            val existing = decode(preferences[KEY]).filterNot { it.id == id }
            preferences[KEY] = json.encodeToString(existing + witness)
        }
    }

    /** The server's list, wholesale. Used after a successful refresh. */
    suspend fun replace(witnesses: List<Witness>) {
        store.edit { it[KEY] = json.encodeToString(witnesses) }
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
