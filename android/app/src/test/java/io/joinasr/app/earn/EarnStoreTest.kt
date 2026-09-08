package io.joinasr.app.earn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EarnStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun activity(id: String = "one", app: String = "selected.app") = EarnActivity(
        id = id, type = EarnRules.FOCUS, packageName = app, appLabel = app,
        target = 20, rewardMinutes = 10, startedAtMillis = 0, deadlineAtMillis = Long.MAX_VALUE,
    )

    @Test fun `concurrent completion delivers one app-specific reward and receipt`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("earned.preferences_pb")
        })
        val attempt = activity()
        store.start(attempt)
        val finished = attempt.copy(progress = 20)
        val winners = List(4) { async { store.complete(finished) } }.awaitAll()
        assertEquals(1, winners.count { it })
        assertEquals(10, store.earnedToday().forPackage("selected.app"))
        assertEquals(0, store.earnedToday().forPackage("other.app"))
        assertNull(store.currentActive())
        assertEquals(finished, store.completed.first())
        store.acknowledgeCompleted()
        assertNull(store.completed.first())
        assertEquals(10, store.earnedToday().forPackage("selected.app"))
    }

    @Test fun `cancelled or replaced attempts cannot be resurrected or rewarded`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("cancel.preferences_pb")
        })
        val old = activity()
        store.start(old)
        store.clearActive(old.id)
        store.update(old.copy(focusLockedSinceElapsed = 100))
        assertNull(store.currentActive())
        assertFalse(store.complete(old.copy(progress = 20)))
        val next = activity("two", "other.app")
        store.start(next)
        store.update(old)
        store.clearActive(old.id)
        assertFalse(store.complete(old.copy(progress = 20)))
        assertEquals(next, store.currentActive())
        assertTrue(store.earnedToday().minutesByPackage.isEmpty())
    }

    @Test fun `incomplete attempts earn nothing and the existing per-app cap is preserved`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("cap.preferences_pb")
        })
        store.start(activity())
        assertFalse(store.complete(activity().copy(progress = 19)))
        repeat(5) { index ->
            val attempt = activity(index.toString())
            store.start(attempt)
            assertTrue(store.complete(attempt.copy(progress = 20)))
        }
        assertEquals(EarnRules.DAILY_CAP_MINUTES, store.earnedToday().forPackage("selected.app"))
        val other = activity("other", "other.app")
        store.start(other)
        assertNull(store.completed.first())
        assertTrue(store.complete(other.copy(progress = 20)))
        assertEquals(10, store.earnedToday().forPackage("other.app"))
    }

    @Test fun `walking still earns its existing reward`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("walk.preferences_pb")
        })
        val walk = activity().copy(type = EarnRules.WALK, target = EarnRules.WALK_STEPS)
        store.start(walk)
        assertTrue(store.complete(walk.copy(progress = EarnRules.WALK_STEPS)))
        assertEquals(10, store.earnedToday().forPackage(walk.packageName))
    }
}
