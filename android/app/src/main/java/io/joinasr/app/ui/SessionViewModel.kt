package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.Me
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether anyone is signed in, as far as the server is concerned. */
sealed interface Session {
    /** Deciding: a token may be on disk, and only /v1/me can say if it works. */
    data object Unknown : Session
    data object SignedOut : Session
    data class SignedIn(val me: Me) : Session
}

/**
 * Owns the session and the two forms that create one.
 *
 * The rule this is built around: a stored token is a claim, not a fact. On
 * every start the app asks GET /v1/me before showing a signed-in screen, and
 * a 401 clears the token rather than leaving the app in a state where every
 * screen loads empty and nothing explains why.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val tokens = Api.tokens(app)

    private val _session = MutableStateFlow<Session>(Session.Unknown)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** The last refusal, in words meant for the person reading it. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = tokens.current()
            if (stored.isNullOrBlank()) {
                _session.value = Session.SignedOut
            } else {
                resolve(stored, clearTokenOnUnauthorised = true)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun signUp(email: String, password: String) = submit { Api.auth.signUp(email, password) }

    fun signIn(email: String, password: String) = submit { Api.auth.signIn(email, password) }

    fun signOut() {
        viewModelScope.launch {
            // Cleared locally first and unconditionally. Asking the server to
            // revoke is the right thing to add, but a person who taps sign out
            // on a train must end up signed out regardless of the network.
            tokens.clear()
            _error.value = null
            _session.value = Session.SignedOut
        }
    }

    private fun submit(call: suspend () -> ApiResult<String>) {
        if (_submitting.value) return // A second tap on a slow network is not a second request.
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            when (val result = call()) {
                is ApiResult.Ok -> {
                    tokens.save(result.value)
                    resolve(result.value, clearTokenOnUnauthorised = false)
                }
                is ApiResult.Failure -> _error.value = result.message
                is ApiResult.Offline -> _error.value = result.message
            }
            _submitting.value = false
        }
    }

    /** Turns a token into a session by using it, which is the only real test. */
    private suspend fun resolve(token: String, clearTokenOnUnauthorised: Boolean) {
        when (val me = Api.me.get(token)) {
            is ApiResult.Ok -> _session.value = Session.SignedIn(me.value)
            is ApiResult.Failure -> {
                if (me.code == 401) {
                    if (clearTokenOnUnauthorised) tokens.clear()
                    _session.value = Session.SignedOut
                    // Silent on purpose when resuming: an expired token is
                    // not something the person did wrong, and the log-in
                    // screen already says what to do.
                    if (!clearTokenOnUnauthorised) _error.value = me.message
                } else {
                    _session.value = Session.SignedOut
                    _error.value = me.message
                }
            }
            is ApiResult.Offline -> {
                // The token may well be good; there is just no way to know.
                // Signed out is the safe reading, and the message says why.
                _session.value = Session.SignedOut
                _error.value = me.message
            }
        }
    }
}
