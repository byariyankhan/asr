package io.joinasr.app.witness

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val witnesses: StateFlow<List<Witness>> =
        store.witnesses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            if (result is ApiResult.Ok) {
                store.replace(
                    result.value.map {
                        Witness(
                            id = it.id,
                            relationship = it.relationship,
                            invitedAtMillis = System.currentTimeMillis(),
                            accepted = it.accepted,
                        )
                    },
                )
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            if (Api.witnesses.remove(token, id) is ApiResult.Ok) store.remove(id)
        }
    }
}
