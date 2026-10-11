package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.curriculum.CurriculumLessonService
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The standard curriculum screen's state (CLAUDE.md: "apart from getting from the conversation... normal lessons").
 * Unlike the daily micro-lesson, nothing here is saved beyond the progress counters in [CurriculumLessonService]: a
 * fresh session for today is built each time [start] is called.
 */
class CurriculumLessonViewModel(private val service: CurriculumLessonService) : ViewModel() {

    private val state = MutableStateFlow<CurriculumSessionState>(CurriculumSessionState.Loading)
    val uiState: StateFlow<CurriculumSessionState> = state.asStateFlow()

    /** Builds (or rebuilds) today's session for whatever the learner is currently learning. */
    fun start() {
        state.value = CurriculumSessionState.Loading
        viewModelScope.launch { state.value = service.today() }
    }

    /** The learner marked the current card as learned: records it and moves to the next one. */
    fun next() {
        val current = state.value as? CurriculumSessionState.InProgress ?: return
        viewModelScope.launch { state.value = service.next(current) }
    }
}
