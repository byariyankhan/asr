package io.joinasr.app.enforcement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionStore: DataStore<Preferences> by
    preferencesDataStore(name = "asr_protection")

/**
 * Whether the enforcement loop is actually running, and when it last looked.
 *
 * This exists because the first two attempts at blocking failed silently.
 * The dashboard said LOCKED, the loop had either died or was having its
 * launches dropped by the system, and the app carried on looking healthy
 * while enforcing nothing. Nothing in the product is worse than that: a
 * person is relying on it precisely when it is not working.
 *
 * So the loop leaves a mark every time it runs, and the dashboard reads it.
 * A stale mark means the loop is not running, and the screen says so instead
 * of claiming protection it is not providing.
 */
data class ProtectionStatus(
    /** When the loop last completed a pass. Zero if it never has. */
    val lastCheckMillis: Long,
    /** Whether that pass was able to enforce anything at all. */
    val enforcing: Boolean,
    /**
     * When a block screen was last dropped by the system. Android refuses a
     * background activity launch without the overlay permission and says
     * nothing, so this is the only trace it leaves.
     */
    val lastBlockFailedMillis: Long,
) {
    /**
     * Whether the mark is recent enough to trust.
     *
     * The loop's slowest pass is 15 seconds, so a minute of silence is three
     * missed passes and not a slow one.
     */
    fun isLive(nowMillis: Long): Boolean =
        lastCheckMillis > 0 && nowMillis - lastCheckMillis < STALE_AFTER_MILLIS

    companion object {
        const val STALE_AFTER_MILLIS = 60_000L
    }
}

class ProtectionStatusStore(context: Context) {

    private val store = context.applicationContext.protectionStore

    val status: Flow<ProtectionStatus> = store.data.map {
        ProtectionStatus(
            lastCheckMillis = it[LAST_CHECK] ?: 0,
            enforcing = it[ENFORCING] ?: false,
            lastBlockFailedMillis = it[BLOCK_FAILED] ?: 0,
        )
    }

    /**
     * Marks a completed pass, at most once every [WRITE_EVERY_MILLIS].
     *
     * The loop runs as often as once a second near a limit, and DataStore
     * rewrites its whole file on every write. Marking each pass would be
     * hundreds of file writes an hour to record something the screen only
     * needs to the nearest half minute.
     */
    suspend fun record(nowMillis: Long, enforcing: Boolean) {
        if (nowMillis - lastWrittenAt < WRITE_EVERY_MILLIS && enforcing == lastWrittenEnforcing) {
            return
        }
        lastWrittenAt = nowMillis
        lastWrittenEnforcing = enforcing
        runCatching {
            store.edit {
                it[LAST_CHECK] = nowMillis
                it[ENFORCING] = enforcing
            }
        }
    }

    /** Not throttled: a dropped block screen is rare and always worth knowing. */
    suspend fun recordBlockFailed(nowMillis: Long) {
        runCatching { store.edit { it[BLOCK_FAILED] = nowMillis } }
    }

    @Volatile
    private var lastWrittenAt = 0L

    @Volatile
    private var lastWrittenEnforcing = false

    private companion object {
        val LAST_CHECK = longPreferencesKey("last_check")
        val ENFORCING = booleanPreferencesKey("enforcing")
        val BLOCK_FAILED = longPreferencesKey("block_failed")
        const val WRITE_EVERY_MILLIS = 20_000L
    }
}
