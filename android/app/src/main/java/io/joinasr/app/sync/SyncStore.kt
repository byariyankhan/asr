package io.joinasr.app.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "asr_sync")

/**
 * Something that happened on the phone and the server has not been told
 * about yet.
 *
 * [id] is generated when the thing happens, not when it is sent, so a retry
 * after a week of no signal is still the same event to the server rather
 * than a second breach.
 */
@Serializable
data class PendingEvent(
    val id: String,
    val type: String,
    val reason: String? = null,
    val appPackage: String? = null,
    val minutes: Int? = null,
    val occurredAtMillis: Long,
)

/**
 * What this phone knows about the server's copy of things.
 *
 * Kept apart from [io.joinasr.app.enforcement.PactStore] on purpose. That
 * store is the pact, and enforcement reads it on a phone with no signal in
 * flight mode; this one is bookkeeping about a network that may be down for
 * a week. Mixing them would put a failed request in the path of a limit.
 */
class SyncStore(context: Context) {

    private val store = context.applicationContext.syncStore

    /**
     * This install's own id, made once and kept. The server is idempotent on
     * it, so reinstalling makes a new device row and reinstalling twice does
     * not make three.
     */
    suspend fun installId(): String {
        store.data.first()[INSTALL_ID]?.let { return it }
        val made = Uuid7.next()
        var winner = made
        store.edit { preferences ->
            // Another caller may have written one between the read and here.
            val existing = preferences[INSTALL_ID]
            if (existing == null) preferences[INSTALL_ID] = made else winner = existing
        }
        return winner
    }

    suspend fun deviceId(): String? = store.data.first()[DEVICE_ID]

    suspend fun saveDeviceId(id: String) {
        store.edit { it[DEVICE_ID] = id }
    }

    /**
     * The server's id for the pact that started at [startedAtMillis], or
     * null when this phone has not managed to create it yet. Keyed by the
     * start time so a stale id from a previous challenge can never be
     * reported against the current one.
     */
    suspend fun remotePactId(startedAtMillis: Long): String? {
        val preferences = store.data.first()
        if (preferences[PACT_STARTED_AT] != startedAtMillis) return null
        return preferences[PACT_ID]
    }

    suspend fun saveRemotePact(id: String, startedAtMillis: Long) {
        store.edit {
            it[PACT_ID] = id
            it[PACT_STARTED_AT] = startedAtMillis
        }
    }

    suspend fun pending(): List<PendingEvent> = store.data.map { decode(it[OUTBOX]) }.first()

    suspend fun enqueue(event: PendingEvent) {
        store.edit { preferences ->
            val queue = decode(preferences[OUTBOX])
            if (queue.any { it.id == event.id }) return@edit
            preferences[OUTBOX] = json.encodeToString(queue + event)
        }
    }

    suspend fun drop(id: String) {
        store.edit { preferences ->
            val queue = decode(preferences[OUTBOX])
            preferences[OUTBOX] = json.encodeToString(queue.filterNot { it.id == id })
        }
    }

    /**
     * An outbox that cannot be read is an empty one. Throwing here would
     * take down the loop that reports breaches, which is a worse outcome
     * than losing a queued event nothing can decode anyway.
     */
    private fun decode(stored: String?): List<PendingEvent> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PendingEvent>>(stored) }
            .getOrDefault(emptyList())
    }

    private companion object {
        val INSTALL_ID = stringPreferencesKey("install_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PACT_ID = stringPreferencesKey("pact_id")
        val PACT_STARTED_AT = longPreferencesKey("pact_started_at")
        val OUTBOX = stringPreferencesKey("outbox")

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
