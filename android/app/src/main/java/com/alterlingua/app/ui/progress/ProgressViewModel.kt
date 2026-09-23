package com.alterlingua.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.progress.ProgressReport
import com.alterlingua.app.learning.progress.ProgressService
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the Progress tab shows. There is no sample data: until a report exists the screen shows a plain loading state. */
sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Ready(val language: Language, val report: ProgressReport) : ProgressUiState
}

/** Progress for the language being learned, built from the real Personal Language Map and daily counts. */
class ProgressViewModel(
    private val service: ProgressService,
    settings: UserSettingsRepository,
) : ViewModel() {
    private val state = MutableStateFlow<ProgressUiState>(ProgressUiState.Loading)
    val uiState: StateFlow<ProgressUiState> = state.asStateFlow()

    private var language: Language? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.targetLanguage }.distinctUntilChanged().collect {
                language = it
                load(it)
            }
        }
    }

    /** Looks again, for example when the tab is opened after new messages were translated. */
    fun refresh() {
        val current = language ?: return
        viewModelScope.launch { load(current) }
    }

    private suspend fun load(language: Language) {
        state.value = ProgressUiState.Ready(language, service.report(language))
    }
}
