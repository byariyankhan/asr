package io.joinasr.app.earn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.analytics.Analytics
import io.joinasr.app.enforcement.Pact
import io.joinasr.app.enforcement.PactApp
import io.joinasr.app.sync.Sync
import io.joinasr.app.sync.Uuid7
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Earning time: Figma 21 through 24.
 *
 * The order everything happens in is the same as everywhere else in this
 * app. The reward is applied on the phone the instant an activity completes,
 * and the server is told afterwards — somebody standing in the street having
 * walked two kilometres should not have to find signal before their ten
 * minutes exist.
 *
 * The cap is therefore checked in both places. The server owns the real one
 * across every device; this one stops a phone that has been offline all
 * afternoon from handing out an hour of TikTok for one walk.
 */
class EarnViewModel(application: Application) : AndroidViewModel(application) {

    private val store = EarnStore(application)
    private val sync = Sync(application)
    val steps = StepCounter(application)

    val active: StateFlow<EarnActivity?> =
        store.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val earned: StateFlow<EarnedToday> = store.earned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EarnedToday(""))

    /** Set when an activity finishes, so Figma 24 is shown once. */
    private val _justEarned = MutableStateFlow<EarnActivity?>(null)
    val justEarned: StateFlow<EarnActivity?> = _justEarned.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Begins an attempt.
     *
     * The id is made here and is the server's id too, so a start that is
     * sent twice on a bad connection produces one row rather than two — and
     * so completing it later needs nothing but the phone's own record.
     */
    fun start(pact: Pact, app: PactApp, type: String) {
        viewModelScope.launch {
            if (store.currentActive() != null) return@launch
            val earnedSoFar = store.earnedToday().forPackage(app.packageName)
            if (earnedSoFar >= EarnRules.DAILY_CAP_MINUTES) {
                _error.value = "You have earned all the bonus time ${app.label} can have today."
                return@launch
            }
            val now = System.currentTimeMillis()
            val activity = EarnActivity(
                id = Uuid7.next(now),
                type = type,
                packageName = app.packageName,
                appLabel = app.label,
                target = if (type == EarnRules.WALK) {
                    EarnRules.WALK_STEPS
                } else {
                    EarnRules.FOCUS_MINUTES
                },
                rewardMinutes = EarnRules.REWARD_MINUTES,
                startedAtMillis = now,
                deadlineAtMillis = now + EarnRules.DEADLINE_HOURS * 60 * 60 * 1000,
            )
            store.start(activity)
            // Stood down only on a settled refusal -- the day's bonus for
            // this app already spent, which the server can know before this
            // phone does. Silence and every other failure leave it running.
            val answer = runCatching { sync.startActivity(pact, activity) }
                .getOrDefault(Sync.StartResult.Unknown)
            if (answer is Sync.StartResult.Refused) {
                store.clearActive()
                _error.value = answer.message
            }
        }
    }

    /**
     * A reading from the step counter.
     *
     * The sensor's total is shared with every app on the phone and cannot be
     * reset, so the first reading after starting becomes the baseline and
     * everything after it is a difference. A reboot resets the hardware
     * counter to zero, which shows up here as a total below the baseline;
     * that re-baselines rather than counting backwards, at the cost of the
     * steps taken before the reboot. Losing those is better than a walk that
     * can never finish.
     */
    fun onSteps(total: Int) {
        viewModelScope.launch {
            val running = store.currentActive() ?: return@launch
            if (!running.isWalk) return@launch
            if (running.baselineSteps < 0 || total < running.baselineSteps) {
                store.update(running.copy(baselineSteps = total, progress = 0))
                return@launch
            }
            val walked = total - running.baselineSteps
            if (walked == running.progress) return@launch
            val updated = running.copy(progress = walked)
            if (updated.isComplete) finish(updated) else store.update(updated)
        }
    }

    /** A tick of the focus timer, in whole minutes elapsed. */
    fun onFocusMinutes(minutes: Int) {
        viewModelScope.launch {
            val running = store.currentActive() ?: return@launch
            if (running.isWalk) return@launch
            if (minutes <= running.progress) return@launch
            val updated = running.copy(progress = minutes)
            if (updated.isComplete) finish(updated) else store.update(updated)
        }
    }

    fun cancel() {
        viewModelScope.launch {
            val running = store.currentActive() ?: return@launch
            store.clearActive()
            runCatching { sync.cancelActivity(running) }
        }
    }

    fun acknowledgeEarned() {
        _justEarned.value = null
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * The moment it is earned. Applied locally first and reported after, and
     * in that order on purpose: the minutes are the thing the person walked
     * for, and the report is bookkeeping.
     */
    private suspend fun finish(activity: EarnActivity) {
        val now = System.currentTimeMillis()
        store.award(activity.packageName, activity.rewardMinutes)
        Analytics.log(Analytics.extraTimeEarned(activity.type))
        store.clearActive()
        _justEarned.value = activity
        runCatching { sync.completeActivity(activity, now) }
    }
}
