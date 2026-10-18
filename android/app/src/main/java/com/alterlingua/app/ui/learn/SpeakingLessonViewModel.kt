package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.speaking.SpeakingLessonService
import com.alterlingua.app.learning.speaking.SpeakingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The Speaking exercise's state (Lesson menu -> Speaking). Each sentence is handed to the same pronunciation
 * engine the daily lesson already uses ([PracticeRoute]/[PracticeViewModel]); this only picks which sentences. */
class SpeakingLessonViewModel(private val service: SpeakingLessonService) : ViewModel() {

    private val state = MutableStateFlow<SpeakingSessionState>(SpeakingSessionState.Loading)
    val uiState: StateFlow<SpeakingSessionState> = state.asStateFlow()

    fun start() {
        state.value = SpeakingSessionState.Loading
        viewModelScope.launch { state.value = service.start() }
    }

    fun next() {
        val current = state.value as? SpeakingSessionState.InProgress ?: return
        state.value = service.next(current)
    }
}
