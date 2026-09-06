package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The account operations that are not the profile: the email address, the
 * password, other sessions, and deletion.
 *
 * Separate from [SessionViewModel] because none of these produce a `Me`.
 * They produce a sentence — "Password updated", "That password is wrong" —
 * and one of them ends the session. Folding them into the session's
 * submitting/error pair would mean a failed password change lighting up an
 * error under the About You form.
 *
 * Deletion does not sign anybody out from here. It reports that it happened
 * and [AsrApp] signs out, because clearing the token is the session's job
 * and doing it in two places is how a token survives one of them.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val tokens = Api.tokens(application)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** A confirmation worth showing: the operation worked and left no screen. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Set once the server has accepted the deletion. Cleared by [consumeDeleted]. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Set once a reset token has been spent. Cleared by [consumeReset]. */
    private val _reset = MutableStateFlow(false)
    val reset: StateFlow<Boolean> = _reset.asStateFlow()

    /**
     * The address a reset link was accepted for. Cleared by
     * [consumeResetEmailSent]. The screen that follows says "reset link
     * sent" as a fact, so it is reached only when the server took the
     * request — not the moment the button was pressed.
     */
    private val _resetEmailSentTo = MutableStateFlow<String?>(null)
    val resetEmailSentTo: StateFlow<String?> = _resetEmailSentTo.asStateFlow()

    /**
     * Set once the server has taken a new address. The address lives in the
     * session's copy of the profile, which [AsrApp] re-reads on this and
     * then clears it with [consumeEmailChanged].
     */
    private val _emailChanged = MutableStateFlow(false)
    val emailChanged: StateFlow<Boolean> = _emailChanged.asStateFlow()

    fun clear() {
        _error.value = null
        _notice.value = null
    }

    fun sendVerification() = withToken { token ->
        Api.account.sendVerification(token).onOk {
            _notice.value = "Link sent. Check your inbox; it works for an hour."
        }
    }

    fun changeEmail(newEmail: String, password: String) = withToken { token ->
        Api.account.changeEmail(token, newEmail, password).onOk {
            _notice.value = "Email updated. Confirm it whenever you like, from this screen."
            _emailChanged.value = true
        }
    }

    fun consumeEmailChanged() {
        _emailChanged.value = false
    }

    fun changePassword(current: String, next: String) = withToken { token ->
        Api.account.changePassword(token, current, next).onOk {
            // The server revokes other sessions on a password change, so say
            // so: somebody wondering whether their old tablet is still signed
            // in should not have to guess.
            _notice.value = "Password updated. Other devices have been signed out."
        }
    }

    fun signOutOtherSessions() = withToken { token ->
        Api.account.revokeOtherSessions(token).onOk {
            _notice.value = "Signed out everywhere else."
        }
    }

    fun deleteAccount(password: String) = withToken { token ->
        Api.account.deleteAccount(token, password).onOk { _deleted.value = true }
    }

    fun consumeDeleted() {
        _deleted.value = false
    }

    /**
     * Asks for the reset email. Success here means the server accepted the
     * request, not that an account exists: it answers the same way for an
     * address it has never seen, and a client that distinguished the two
     * would turn this screen into a way to test whether somebody has an
     * account. So the wording never claims more than that.
     */
    fun sendResetEmail(email: String) = submit {
        Api.account.sendResetEmail(email).onOk {
            _resetEmailSentTo.value = email.trim()
            _notice.value = "Sent. If that address has an account, the link is on its way."
        }
    }

    fun consumeResetEmailSent() {
        _resetEmailSentTo.value = null
    }

    fun resetPassword(token: String, password: String) = submit {
        Api.account.resetPassword(token, password).onOk { _reset.value = true }
    }

    fun consumeReset() {
        _reset.value = false
    }

    private fun ApiResult<Unit>.onOk(block: () -> Unit) {
        when (this) {
            is ApiResult.Ok -> block()
            is ApiResult.Failure -> _error.value = message
            is ApiResult.Offline -> _error.value = message
        }
    }

    /** For calls that need no token: the reset pair, which run signed out. */
    private fun submit(call: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _notice.value = null
            call()
            _busy.value = false
        }
    }

    private fun withToken(call: suspend (String) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            val token = tokens.current()
            if (token.isNullOrBlank()) {
                _error.value = "You are signed out. Sign in and try again."
                return@launch
            }
            _busy.value = true
            _error.value = null
            _notice.value = null
            call(token)
            _busy.value = false
        }
    }
}
