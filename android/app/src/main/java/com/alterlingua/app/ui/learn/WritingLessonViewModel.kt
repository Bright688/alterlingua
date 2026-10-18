package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.writing.WritingLessonService
import com.alterlingua.app.learning.writing.WritingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Writing exercise's state (Lesson menu -> Writing). Nothing the learner writes is saved or graded -- there is
 * no automated grading anywhere in this app yet, and this does not fake one; the prompt itself is real (the same
 * standard curriculum Vocabulary already uses), the writing is genuine practice, self-assessed.
 */
class WritingLessonViewModel(private val service: WritingLessonService) : ViewModel() {

    private val state = MutableStateFlow<WritingSessionState>(WritingSessionState.Loading)
    val uiState: StateFlow<WritingSessionState> = state.asStateFlow()

    fun start() {
        state.value = WritingSessionState.Loading
        viewModelScope.launch { state.value = service.start() }
    }

    fun next() {
        val current = state.value as? WritingSessionState.InProgress ?: return
        state.value = service.next(current)
    }
}
