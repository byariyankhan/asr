package io.joinasr.app.witness

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.InvitePeek
import io.joinasr.app.data.SupportedPerson
import io.joinasr.app.data.WitnessProgress
import io.joinasr.app.enforcement.PactStore
import io.joinasr.app.sync.Sync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An invite ready to hand to the share sheet. */
data class ShareInvite(val relationship: String, val url: String)

/**
 * The witnesses on this account.
 *
 * The server owns them: it allocates the invite code, stores it and answers
 * the link. This holds a copy on the phone so the list is there before the
 * first request comes back and after it fails, which is the difference
 * between a screen that is briefly empty and a screen that is briefly wrong.
 */
class WitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WitnessStore(application)
    private val tokens = Api.tokens(application)
    private val pacts = PactStore(application)
    private val sync = Sync(application)

    val witnesses: StateFlow<List<Witness>> =
        store.witnesses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether [witnesses] has answered yet.
     *
     * It is read from disk, so its first value is an empty list that means
     * "not yet" rather than "nobody". Anything that acts on emptiness --
     * and the app refuses to leave a running challenge with no witnesses --
     * has to be able to tell those two apart, or it fires at everybody for
     * the moment before the store replies.
     */
    val witnessesLoaded: StateFlow<Boolean> =
        store.witnesses
            .map { true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _inviting = MutableStateFlow(false)
    val inviting: StateFlow<Boolean> = _inviting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Set when an invite has been issued and is waiting to be shared.
     * The screen opens the sheet and calls [shared]; nothing else clears it,
     * so a person who backgrounds the app mid-invite still gets the sheet.
     */
    private val _pendingShare = MutableStateFlow<ShareInvite?>(null)
    val pendingShare: StateFlow<ShareInvite?> = _pendingShare.asStateFlow()

    /**
     * The other direction: people this person is a witness for. Not stored
     * on the phone, unlike [witnesses]. Somebody's own witness list has to
     * be on screen before the first request returns because it is part of
     * their own challenge; another person's progress is theirs, and showing
     * a stale copy of it after they revoked access would be wrong.
     */
    private val _supporting = MutableStateFlow<List<SupportedPerson>>(emptyList())
    val supporting: StateFlow<List<SupportedPerson>> = _supporting.asStateFlow()

    /** Figma 17, keyed by the witness row id it was read for. */
    private val _progress = MutableStateFlow<Map<String, WitnessProgress>>(emptyMap())
    val progress: StateFlow<Map<String, WitnessProgress>> = _progress.asStateFlow()

    /** What this person has reacted with, keyed by event id. */
    private val _reactions = MutableStateFlow<Map<String, String>>(emptyMap())
    val reactions: StateFlow<Map<String, String>> = _reactions.asStateFlow()

    /** Figma 18: an invitation opened from a link. */
    private val _invite = MutableStateFlow<InvitePeek?>(null)
    val invite: StateFlow<InvitePeek?> = _invite.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    private val _inviteBusy = MutableStateFlow(false)
    val inviteBusy: StateFlow<Boolean> = _inviteBusy.asStateFlow()

    /** Set once the invite has been accepted or declined, so the screen closes. */
    private val _inviteAnswered = MutableStateFlow(false)
    val inviteAnswered: StateFlow<Boolean> = _inviteAnswered.asStateFlow()

    init {
        refresh()
    }

    /**
     * Asks the server for an invite, stores it, and offers it for sharing.
     *
     * The local row is written before the sheet opens rather than after: the
     * invite exists on the server the moment this returns, whether or not
     * anybody presses send, and a list that hid it until the sheet was used
     * would be a list that disagrees with the server.
     */
    fun invite(relationship: String) {
        if (_inviting.value) return
        viewModelScope.launch {
            _inviting.value = true
            _error.value = null
            val token = tokens.current()
            if (token.isNullOrBlank()) {
                _error.value = "Sign in again to invite a witness."
                _inviting.value = false
                return@launch
            }
            // An invitation is to a challenge, and the server will not issue
            // a link without one. The challenge is committed on the phone
            // and pushed to the server afterwards, in the background and
            // best-effort, so the invitation can easily get there first --
            // and the screen that asks for it is the one that will not let
            // go until an invitation has gone out. Refusing there is a lock
            // with no way out of it.
            //
            // So the challenge is made sure of first. After the first time
            // this costs nothing: the server's id for it is stored, and
            // finding it is a read from disk.
            //
            // And when it cannot be made sure of, that is what gets said.
            // The server's own answer -- "Start a challenge before inviting
            // witnesses to it" -- is true from where it is standing and
            // useless from here, where a challenge has been running for
            // days; it sends somebody looking for a button that does not
            // exist instead of at the connection.
            val pact = pacts.current()
            if (pact != null && runCatching { sync.remotePactId(pact) }.getOrNull() == null) {
                _error.value = "Could not reach the server to register this challenge. " +
                    "Check your connection and try again."
                _inviting.value = false
                return@launch
            }
            when (val result = Api.witnesses.invite(token, relationship)) {
                is ApiResult.Ok -> {
                    store.add(
                        id = result.value.id,
                        relationship = result.value.relationship,
                        inviteUrl = result.value.url,
                    )
                    _pendingShare.value = ShareInvite(result.value.relationship, result.value.url)
                }

                is ApiResult.Failure -> _error.value = result.message
                is ApiResult.Offline -> _error.value = result.message
            }
            _inviting.value = false
        }
    }

    fun shared() {
        _pendingShare.value = null
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Replaces the local copy with the server's.
     *
     * Only on success. A failed refresh must not empty a list somebody is
     * looking at: being a few minutes stale is a smaller lie than showing
     * nobody when there are three.
     */
    fun refresh() {
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            val result = Api.witnesses.list(token)
            if (result !is ApiResult.Ok) return@launch
            store.replace(
                result.value.myWitnesses.map {
                    Witness(
                        id = it.id,
                        relationship = it.relationship,
                        invitedAtMillis = System.currentTimeMillis(),
                        inviteUrl = it.inviteUrl,
                        accepted = it.accepted,
                        name = it.user?.name,
                        image = it.user?.image,
                        reactions = it.reactions,
                    )
                },
            )
            _supporting.value = result.value.iWitness
        }
    }

    /**
     * Loads Figma 17 for one person. Always from the server: it is their
     * data, and a cached copy shown after they stopped sharing would be this
     * app leaking something it was told to stop showing.
     */
    fun loadProgress(witnessId: String) {
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            when (val result = Api.witnesses.progress(token, witnessId)) {
                is ApiResult.Ok -> _progress.value = _progress.value + (witnessId to result.value)
                is ApiResult.Failure -> _error.value = result.message
                is ApiResult.Offline -> _error.value = result.message
            }
        }
    }

    /**
     * Reacts to one of their events.
     *
     * Shown as chosen straight away and put back if the server refuses. A
     * reaction is one tap and the round trip is not instant; making somebody
     * watch a spinner to find out whether their tomato landed would be worse
     * than briefly showing one that did not.
     */
    fun react(witnessId: String, eventId: String, emoji: String) {
        val previous = _reactions.value[eventId]
        _reactions.value = _reactions.value + (eventId to emoji)
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            when (val result = Api.witnesses.react(token, witnessId, eventId, emoji)) {
                is ApiResult.Ok -> loadProgress(witnessId)
                is ApiResult.Failure -> {
                    _reactions.value = _reactions.value - eventId +
                        (previous?.let { mapOf(eventId to it) } ?: emptyMap())
                    _error.value = result.message
                }
                is ApiResult.Offline -> {
                    _reactions.value = _reactions.value - eventId +
                        (previous?.let { mapOf(eventId to it) } ?: emptyMap())
                    _error.value = result.message
                }
            }
        }
    }

    /**
     * Looks up an invitation. No token: the person who opened the link may
     * have no account at all, which is exactly who Figma 18 is for.
     */
    fun openInvite(code: String) {
        viewModelScope.launch {
            _invite.value = null
            _inviteError.value = null
            _inviteAnswered.value = false
            when (val result = Api.witnesses.peekInvite(code, tokens.current())) {
                is ApiResult.Ok -> _invite.value = result.value
                is ApiResult.Failure -> _inviteError.value = if (result.code == 404) {
                    // 404 here means answered, withdrawn or never real, and
                    // the server deliberately does not say which: it answers
                    // to anybody holding a code.
                    "This invitation has already been answered, or the link is not valid."
                } else {
                    result.message
                }
                is ApiResult.Offline -> _inviteError.value = result.message
            }
        }
    }

    fun answerInvite(code: String, accept: Boolean) {
        if (_inviteBusy.value) return
        viewModelScope.launch {
            val token = tokens.current()
            if (token.isNullOrBlank()) {
                _inviteError.value = "Sign in first, then open the link again."
                return@launch
            }
            _inviteBusy.value = true
            _inviteError.value = null
            val result = if (accept) {
                Api.witnesses.acceptInvite(token, code)
            } else {
                Api.witnesses.declineInvite(token, code)
            }
            when (result) {
                is ApiResult.Ok -> {
                    _inviteAnswered.value = true
                    // Accepting adds somebody to the other list, so both are
                    // re-read rather than patched locally.
                    if (accept) refresh()
                }
                is ApiResult.Failure -> _inviteError.value = result.message
                is ApiResult.Offline -> _inviteError.value = result.message
            }
            _inviteBusy.value = false
        }
    }

    fun clearInvite() {
        _invite.value = null
        _inviteError.value = null
        _inviteAnswered.value = false
    }

    fun remove(id: String) {
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            if (Api.witnesses.remove(token, id) is ApiResult.Ok) store.remove(id)
        }
    }
}
