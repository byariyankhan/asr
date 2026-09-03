package io.joinasr.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authStore: DataStore<Preferences> by preferencesDataStore(name = "asr_auth")

/**
 * Where the session token lives between launches.
 *
 * DataStore in the app's private directory, with `allowBackup="false"` on the
 * application. docs/API.md originally said EncryptedSharedPreferences; that
 * is written against `androidx.security:security-crypto`, whose only release
 * carrying the fixes is an alpha Google has stopped developing, and shipping
 * an unmaintained alpha to hold credentials is not obviously safer than the
 * OS sandbox. What the encryption would add over private storage is
 * protection against extraction from a rooted device — against which a
 * revocable 30-day session token is the wrong thing to spend an alpha
 * dependency on. Revisit if the threat model changes.
 */
class TokenStore(private val context: Context) {

    val token: Flow<String?> = context.authStore.data.map { it[KEY] }

    suspend fun current(): String? = token.first()

    suspend fun save(value: String) {
        context.authStore.edit { it[KEY] = value }
    }

    suspend fun clear() {
        context.authStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("session_token")
    }
}
