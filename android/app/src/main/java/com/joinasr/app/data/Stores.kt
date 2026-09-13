package com.joinasr.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.joinasr.app.diagnostics.Crash
import kotlin.properties.ReadOnlyProperty

/**
 * Every preferences file this app keeps is opened through here, and the
 * reason is one crash.
 *
 * DataStore's answer to a file it cannot parse is to throw -- not once, but
 * on every read, forever, because the bad bytes are still on disk. Nine
 * stores were opened without a corruption handler, so one truncated write
 * (a process killed mid-flush, a phone that lost power) turned the app into
 * one that opens and closes again, on every launch, with no way out but
 * clearing its data. That is what a Pixel 7 Pro did after eight hours:
 *
 *     androidx.datastore.core.CorruptionException: Unable to parse
 *     preferences proto
 *     Caused by: InvalidProtocolBufferException: Protocol message
 *     contained an invalid tag (zero)
 *
 * A tag of zero is a file that was written to but never finished.
 *
 * So a file that will not parse is replaced with an empty one and the app
 * opens. Every store here can survive that: the pact is held by the server
 * and read back by `restoreFromServer`, the token means signing in again,
 * today's minutes and the earn ledger are a day at worst. None of that is
 * free -- but the choice is not between losing a file and keeping it, it is
 * between losing a file and losing the app, and a challenge nobody can open
 * is not being enforced either.
 *
 * The replacement is reported, so a file going bad stays visible instead of
 * becoming a silent reset somebody notices as "my challenge disappeared".
 */
object Stores {

    @Volatile
    private var app: Context? = null

    /**
     * Handed the application context by [com.joinasr.app.AsrApplication], so
     * that a corruption can be reported. A delegate declared at file scope
     * has no context of its own, and the handler runs long after the
     * application exists, so this is set once and read much later.
     */
    fun remember(context: Context) {
        app = context.applicationContext
    }

    /**
     * The handler for one named file. Public only so the test can reach it.
     */
    fun handler(name: String): ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler(
            produceNewData = { error ->
                app?.let { Crash.report(it, error, "datastore:$name") }
                emptyPreferences()
            },
        )
}

/**
 * A preferences file, named once and never opened without a way back from a
 * corrupt one. `preferencesDataStore` is not called anywhere else in this
 * app; `tools/preflight.sh` fails if it is.
 */
fun asrPreferences(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(name = name, corruptionHandler = Stores.handler(name))
