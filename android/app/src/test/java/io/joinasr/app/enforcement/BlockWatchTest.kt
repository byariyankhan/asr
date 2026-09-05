package io.joinasr.app.enforcement

import io.joinasr.app.enforcement.BlockWatch.Step
import io.joinasr.app.enforcement.BlockWatch.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockWatchTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"

    @Test
    fun `nothing to block is nothing to do`() {
        val watch = BlockWatch()
        assertEquals(Step.Nothing, watch.next(null, 1_000))
        assertNull(watch.packageName)
    }

    @Test
    fun `a newly blocked app gets the activity first`() {
        val watch = BlockWatch()
        assertEquals(Step.LaunchActivity, watch.next(instagram, 1_000))
        assertEquals(instagram, watch.packageName)
    }

    @Test
    fun `a launch that took holds until the app changes`() {
        val watch = BlockWatch()
        watch.next(instagram, 1_000)
        watch.shown(Via.Activity, 1_000)
        // Still in front a second later: within the grace, nothing yet.
        assertEquals(Step.Nothing, watch.next(instagram, 2_000))
        // The block screen came up, so this app is in front and nothing is blocked.
        assertEquals(Step.Nothing, watch.next(null, 2_500))
        assertNull(watch.packageName)
        // Back to the app: a fresh launch.
        assertEquals(Step.LaunchActivity, watch.next(instagram, 3_000))
    }

    @Test
    fun `a launch that was dropped falls back to the overlay after the grace`() {
        val watch = BlockWatch()
        watch.next(instagram, 1_000)
        watch.shown(Via.Activity, 1_000)
        assertEquals(Step.Nothing, watch.next(instagram, 1_000 + BlockWatch.LAUNCH_GRACE_MILLIS - 1))
        assertEquals(Step.ShowOverlay, watch.next(instagram, 1_000 + BlockWatch.LAUNCH_GRACE_MILLIS))
        watch.shown(Via.Overlay, 3_500)
        assertTrue(watch.showingOverlay)
        // The overlay does not take the app out of the foreground, so the
        // same answer keeps coming and nothing is re-launched over it.
        assertEquals(Step.Nothing, watch.next(instagram, 60_000))
        assertEquals(Step.Nothing, watch.next(instagram, 600_000))
    }

    @Test
    fun `when neither route works it tries again rather than never`() {
        val watch = BlockWatch()
        watch.next(instagram, 1_000)
        watch.failed(1_000)
        assertEquals(Step.Nothing, watch.next(instagram, 1_000 + BlockWatch.RETRY_MILLIS - 1))
        assertEquals(Step.LaunchActivity, watch.next(instagram, 1_000 + BlockWatch.RETRY_MILLIS))
        watch.shown(Via.Activity, 11_000)
        assertEquals(Step.ShowOverlay, watch.next(instagram, 11_000 + BlockWatch.LAUNCH_GRACE_MILLIS))
        watch.failed(13_500)
        assertFalse(watch.showingOverlay)
        assertEquals(Step.LaunchActivity, watch.next(instagram, 13_500 + BlockWatch.RETRY_MILLIS))
    }

    @Test
    fun `a different blocked app starts over`() {
        val watch = BlockWatch()
        watch.next(instagram, 1_000)
        watch.shown(Via.Overlay, 1_000)
        assertEquals(Step.LaunchActivity, watch.next(youtube, 2_000))
        assertEquals(youtube, watch.packageName)
        assertFalse(watch.showingOverlay)
    }

    @Test
    fun `clearing forgets everything`() {
        val watch = BlockWatch()
        watch.next(instagram, 1_000)
        watch.shown(Via.Overlay, 1_000)
        watch.clear()
        assertNull(watch.packageName)
        assertFalse(watch.showingOverlay)
        assertEquals(Step.LaunchActivity, watch.next(instagram, 1_500))
    }
}
