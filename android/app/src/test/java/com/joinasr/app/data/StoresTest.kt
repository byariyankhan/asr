package com.joinasr.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a preferences file that will not parse costs.
 *
 * It used to cost the app. DataStore throws on a file it cannot parse, and
 * it keeps throwing, because the bad bytes stay on disk -- so a single
 * unfinished write left a Pixel opening Asr and watching it close again,
 * every time, with no way out but clearing its data. Nine stores were open
 * to that and none of them had a handler.
 */
class StoresTest {

    private val corrupt = CorruptionException("Unable to parse preferences proto")

    @Test
    fun `a file that will not parse is replaced, not thrown`() = runBlocking {
        val replaced = Stores.handler("asr_pact").handleCorruption(corrupt)
        assertEquals(emptyPreferences(), replaced)
    }

    @Test
    fun `and replacing it does not need Firebase, or anything else, to be up`() = runBlocking {
        // The handler runs from a file-scope delegate that has no context of
        // its own; AsrApplication hands it one. Everything still has to work
        // before that happens -- a unit test, a process where the report
        // cannot be made -- because the alternative is throwing from inside
        // the recovery, which is the unopenable app again.
        for (name in listOf("asr_auth", "asr_earn", "asr_carried", "asr_usage_floor")) {
            assertEquals(emptyPreferences(), Stores.handler(name).handleCorruption(corrupt))
        }
    }
}
