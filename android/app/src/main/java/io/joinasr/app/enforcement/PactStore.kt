package io.joinasr.app.enforcement

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

private val Context.pactStore: DataStore<Preferences> by preferencesDataStore(name = "asr_pact")

/**
 * Where the pact lives between launches, and the only thing the enforcement
 * service reads to know what to enforce.
 *
 * DataStore rather than Room. The pact is one small immutable value read at
 * service start and written once at the end of setup; a database earns its
 * place when there is history to keep and an outbox to drain, which is the
 * change that brings Room in. Adding it now would be a schema, a compiler
 * plugin and a migration path for a single row.
 *
 * Stored as JSON in one key rather than as separate preferences, so a save
 * is one atomic write. Half a pact — the apps without their limits — is a
 * state the service must never be able to read.
 */
class PactStore(context: Context) {

    private val store = context.applicationContext.pactStore

    val pact: Flow<Pact?> = store.data.map { preferences -> decode(preferences[KEY]) }

    suspend fun current(): Pact? = pact.first()

    suspend fun save(pact: Pact) {
        store.edit { it[KEY] = json.encodeToString(pact) }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    /**
     * A stored pact that cannot be read back is treated as no pact at all.
     *
     * That is the safe direction. The alternative — throwing — kills the
     * service on every start, and a person whose limits silently stopped
     * working will come back to the app, whereas one whose phone has a
     * crashing background service will uninstall it.
     */
    private fun decode(stored: String?): Pact? {
        if (stored.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<Pact>(stored) }
            .getOrNull()
            ?.takeIf { it.isEnforceable }
    }

    private companion object {
        val KEY = stringPreferencesKey("pact")

        /**
         * Lenient about unknown keys so a pact written by a newer build does
         * not become unreadable when somebody downgrades, and explicit about
         * defaults so [Pact.version] survives a round trip.
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
