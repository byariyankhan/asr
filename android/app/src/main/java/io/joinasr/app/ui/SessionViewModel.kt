package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.analytics.Analytics
import io.joinasr.app.data.Api
import io.joinasr.app.data.LocalSignOut
import io.joinasr.app.sync.Sync
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.Me
import io.joinasr.app.data.ProfileUpdate
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
    private val sync = Sync(app)

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
        // One account, one phone: signing in on another handset ends the
        // session here, and the push that says so clears the token from
        // outside this class. Without watching for that, somebody looking at
        // the app when it arrives keeps looking at a signed-in screen whose
        // every request now fails.
        viewModelScope.launch {
            tokens.token.collect { token ->
                if (token.isNullOrBlank() && _session.value is Session.SignedIn) {
                    _session.value = Session.SignedOut
                }
            }
        }
    }

    /**
     * Registers this phone, with its push token, the moment a session is
     * real.
     *
     * Here rather than anywhere in the pact code, because the people who
     * most need push are the ones who never start a challenge: a witness
     * signs up to answer an invitation and nothing else. Registering only
     * when a pact exists would have left exactly them unreachable, and a
     * witness who cannot be told is not a witness.
     *
     * Failure is silent. It is retried on the next launch and by the
     * heartbeat, and there is nothing a person can do about it on a sign-in
     * screen.
     */
    private fun registerDevice() {
        viewModelScope.launch { runCatching { sync.deviceId() } }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Re-reads the profile from the server, keeping the session as it is
     * when that fails. For the moments something outside this class changed
     * the profile -- the email address, from Email & password -- and the
     * screens should show what the server now holds.
     */
    fun refresh() {
        viewModelScope.launch {
            val token = tokens.current()
            if (token.isNullOrBlank()) return@launch
            val me = Api.me.get(token)
            if (me is ApiResult.Ok) _session.value = Session.SignedIn(me.value)
        }
    }

    fun signUp(email: String, password: String) = submit(Analytics.signUp()) { Api.auth.signUp(email, password) }

    fun signIn(email: String, password: String) = submit(Analytics.login()) { Api.auth.signIn(email, password) }

    fun signOut() {
        viewModelScope.launch {
            // The push token goes first, while there is still a session to
            // do it with. Without this the server keeps a live token for the
            // person who just left, and the next person to sign in on this
            // phone gets their notifications -- their name, their pact,
            // their breaches -- on a phone they do not own.
            //
            // Best-effort, and the sign-out does not wait on the answer. A
            // person who taps sign out on a train must end up signed out
            // regardless of the network.
            runCatching { sync.forgetDevice() }
            // Then everything of theirs that lives on this phone: the pact,
            // the witness list, the day carried over from another handset,
            // the token -- and the service, which used to be left running
            // over a pact it could no longer report on. Same path a phone
            // takes when somebody signs in elsewhere, for the same reason.
            runCatching { LocalSignOut.run(getApplication<Application>()) }
            tokens.clear()
            _error.value = null
            _session.value = Session.SignedOut
        }
    }

    /**
     * The About You screen, as one PATCH. The photo is uploaded separately
     * and immediately on picking, so a slow upload never holds up the form.
     */
    fun saveProfile(name: String, dobIso: String, country: String, gender: String) {
        withToken { token ->
            Api.me.update(
                token,
                ProfileUpdate(name = name, dateOfBirth = dobIso, country = country, gender = gender),
            )
        }
    }

    fun uploadPhoto(jpeg: ByteArray) {
        // Deliberately not setting `submitting`: the form stays usable while
        // this happens, and a failed photo must not read as a failed form.
        if (session.value !is Session.SignedIn) return
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            when (val result = Api.me.uploadAvatar(token, jpeg)) {
                is ApiResult.Ok -> _session.value = Session.SignedIn(result.value)
                is ApiResult.Failure -> _error.value = result.message
                is ApiResult.Offline -> _error.value = result.message
            }
        }
    }

    /** Runs a call that needs the stored token and replaces the session with
     *  whatever profile comes back, so the app trusts the server's copy. */
    private fun withToken(call: suspend (String) -> ApiResult<Me>) {
        if (_submitting.value) return
        viewModelScope.launch {
            val token = tokens.current()
            if (token.isNullOrBlank()) {
                _session.value = Session.SignedOut
                return@launch
            }
            _submitting.value = true
            _error.value = null
            when (val result = call(token)) {
                is ApiResult.Ok -> {
                    // Onboarding is over the moment the profile becomes
                    // complete, whichever screen did it and only that once.
                    val wasComplete = (_session.value as? Session.SignedIn)?.me?.profileComplete
                    if (wasComplete == false && result.value.profileComplete) Analytics.log(Analytics.onboardingComplete())
                    _session.value = Session.SignedIn(result.value)
                }
                is ApiResult.Failure -> _error.value = result.message
                is ApiResult.Offline -> _error.value = result.message
            }
            _submitting.value = false
        }
    }

    private fun submit(onSuccess: Analytics.Event, call: suspend () -> ApiResult<String>) {
        if (_submitting.value) return // A second tap on a slow network is not a second request.
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            when (val result = call()) {
                is ApiResult.Ok -> {
                    tokens.save(result.value)
                    Analytics.log(onSuccess)
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
            is ApiResult.Ok -> {
                _session.value = Session.SignedIn(me.value)
                registerDevice()
            }
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
