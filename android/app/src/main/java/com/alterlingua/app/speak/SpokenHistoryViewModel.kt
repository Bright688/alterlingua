package com.alterlingua.app.speak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpokenHistoryUiState(val loading: Boolean = true, val entries: List<SpokenHistoryEntry> = emptyList())

class SpokenHistoryViewModel(private val store: SpokenHistoryStore) : ViewModel() {
    private val state = MutableStateFlow(SpokenHistoryUiState())
    val uiState: StateFlow<SpokenHistoryUiState> = state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            state.value = SpokenHistoryUiState(loading = false, entries = store.recent())
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            store.delete(id)
            reload()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.deleteAll()
            reload()
        }
    }
}
