package io.joinasr.app.apps

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app list, the search box and the selection for screen 06.
 *
 * Loaded in two passes on purpose. Reading a few hundred labels takes a
 * moment and reading a few hundred icons takes several, so the list appears
 * as soon as the names are known and the tiles fill in behind it. One pass
 * would mean a spinner for a second or more on a phone with many apps, for
 * a screen where the names are what a person is reading anyway.
 *
 * The selection lives here and not in storage. A half-made challenge is not
 * a thing the app should remember: it is committed on the review screen,
 * with its limits and its witnesses, or it never existed.
 */
class ChooseAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val _loading = MutableStateFlow(true)
    private val _query = MutableStateFlow("")
    private val _selected = MutableStateFlow<Set<String>>(emptySet())

    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** The list as the screen should draw it: filtered by the search box. */
    val visible: StateFlow<List<AppEntry>> =
        combine(_apps, _query) { apps, query -> AppCatalog.search(apps, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _apps.value = InstalledApps.load(context)
            _loading.value = false

            // Behind the list, newest first in the order they are shown, so
            // the tiles a person is looking at appear before the ones they
            // would have to scroll to.
            val loaded = mutableMapOf<String, ImageBitmap>()
            for (entry in _apps.value) {
                InstalledApps.icon(context, entry.packageName)?.let { bitmap ->
                    loaded[entry.packageName] = bitmap
                    _icons.value = loaded.toMap()
                }
            }
        }
    }

    fun search(text: String) {
        _query.value = text
    }

    /**
     * The chosen apps as entries, not package names: the next screen shows
     * their names, and re-deriving a label from a package name is either a
     * second PackageManager pass or a guess.
     *
     * Read from the full list rather than what is on screen, so a selection
     * made before a search is not lost by the search.
     */
    fun chosen(): List<AppEntry> = _apps.value.filter { it.packageName in _selected.value }

    fun toggle(packageName: String) {
        _selected.value = _selected.value.let {
            if (packageName in it) it - packageName else it + packageName
        }
    }
}
