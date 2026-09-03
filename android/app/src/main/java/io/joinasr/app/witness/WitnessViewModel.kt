package io.joinasr.app.witness

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The witnesses on this phone, for the setup screen and the tab. */
class WitnessViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WitnessStore(application)

    val witnesses: StateFlow<List<Witness>> =
        store.witnesses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(relationship: String) {
        viewModelScope.launch { store.add(relationship) }
    }

    fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }
}
