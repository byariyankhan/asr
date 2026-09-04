package io.joinasr.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.joinasr.app.data.Api
import io.joinasr.app.data.ApiResult
import io.joinasr.app.data.InboxItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Figma 19, and the unread count the dashboard's bell shows.
 *
 * Not stored on the phone. The inbox is the server's, it is short, and a
 * cached copy would keep showing "2 unread" after they were read on another
 * device — which is the one number on the screen somebody actually acts on.
 */
class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val tokens = Api.tokens(application)

    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** True once a request has come back, so an empty list can be told from
     *  one that has not loaded. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            _loading.value = true
            when (val result = Api.inbox.list(token)) {
                is ApiResult.Ok -> {
                    _items.value = result.value.items
                    _unread.value = result.value.unreadCount
                    _loaded.value = true
                }
                // A failed refresh leaves what is on screen alone. The inbox
                // is not urgent enough to replace with an error.
                else -> Unit
            }
            _loading.value = false
        }
    }

    /**
     * Marks one as read, and shows it as read straight away.
     *
     * Opening a notification and watching it stay bold for a second reads as
     * a broken app. If the request fails the next refresh puts it back,
     * which is the right way round: the cost of being briefly wrong here is
     * a dot, and the cost of being slow is the person tapping twice.
     */
    fun markRead(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (!item.unread) return
        _items.value = _items.value.map {
            if (it.id == id) it.copy(readAt = "now") else it
        }
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            Api.inbox.markRead(token, listOf(id))
        }
    }

    fun markAllRead() {
        if (_items.value.none { it.unread }) return
        _items.value = _items.value.map { if (it.unread) it.copy(readAt = "now") else it }
        _unread.value = 0
        viewModelScope.launch {
            val token = tokens.current() ?: return@launch
            Api.inbox.markRead(token, ids = null)
        }
    }
}
