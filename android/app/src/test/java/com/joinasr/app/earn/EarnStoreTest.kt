package com.joinasr.app.earn

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

    @Test fun `a meditation from the still-phone release comes back as a fresh camera sitting`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("earned.preferences_pb")
        })
        val old = activity().copy(type = EarnRules.MEDITATION, target = 600, progress = 240)
        store.start(old)
        val restored = store.currentActive()!!
        assertEquals(EarnRules.MEDITATION_SECONDS, restored.target)
        assertEquals(0, restored.progress)
        assertEquals(old.id, restored.id)
        // One on today's rule is left as it is.
        val current = activity(id = "two").copy(type = EarnRules.MEDITATION, target = EarnRules.MEDITATION_SECONDS, progress = 30)
        store.start(current)
        assertEquals(current, store.currentActive())
    }

    @Test fun `completion receipt expires when the local day changes`() = runTest {
        var day = "2026-09-08"
        val store = EarnStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                folder.newFile("receipt-day.preferences_pb")
            },
            todayProvider = { day },
        )
        val attempt = activity()
        val finished = attempt.copy(progress = 20)
        store.start(attempt)
        assertTrue(store.complete(finished))
        assertEquals(finished, store.completed.first())
        assertEquals(10, store.earnedToday().forPackage("selected.app"))

        day = "2026-09-09"

        assertNull(store.completed.first())
        assertEquals(0, store.earnedToday().forPackage("selected.app"))
    }

    @Test fun `sign-out clears active receipt and earned state and blocks late completion`() = runTest {
        val store = EarnStore(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("signout.preferences_pb")
        })
        val completedAttempt = activity("completed")
        store.start(completedAttempt)
        assertTrue(store.complete(completedAttempt.copy(progress = 20)))
        assertNotNull(store.completed.first())
        assertEquals(10, store.earnedToday().forPackage("selected.app"))

        val lateAttempt = activity("late", "other.app")
        store.start(lateAttempt)
        store.clearForSignOut()

        assertNull(store.currentActive())
        assertNull(store.completed.first())
        assertTrue(store.earnedToday().minutesByPackage.isEmpty())
        assertFalse(store.complete(lateAttempt.copy(progress = 20)))
        assertNull(store.completed.first())
        assertTrue(store.earnedToday().minutesByPackage.isEmpty())
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
