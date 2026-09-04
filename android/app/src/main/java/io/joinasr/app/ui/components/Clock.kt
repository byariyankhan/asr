package io.joinasr.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Now, as something a screen can read and be recomposed by.
 *
 * "9 hr ago" is worked out during composition, so it was as old as the last
 * thing that happened to change the screen -- an hour into looking at it, it
 * still said 9. Text that claims to be a duration has to be one.
 *
 * A minute is the resolution the copy has ("min ago", "hr ago", "2 days
 * ago"), so a minute is the tick. Nothing that is not on screen holds a
 * timer: the loop lives in the composition and stops with it, and it pauses
 * while the app is in the background rather than waking the phone to update
 * a label nobody is looking at.
 */
@Composable
fun rememberNow(): State<Instant> {
    val owner = LocalLifecycleOwner.current
    val resumed by owner.lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.RESUMED) }
        .collectAsStateWithLifecycle(initialValue = true)

    val now = remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(resumed) {
        // Straight away on coming back, because the thing this is for is
        // somebody who closed the app at nine and opened it at noon.
        now.value = Instant.now()
        while (resumed) {
            delay(60_000)
            now.value = Instant.now()
        }
    }
    return now
}
