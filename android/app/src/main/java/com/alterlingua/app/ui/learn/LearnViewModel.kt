package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.LessonState
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the Learn tab shows. */
sealed interface LearnUiState {
    data object Loading : LearnUiState

    /** Nothing met yet that is worth a lesson. */
    data class Empty(val language: Language) : LearnUiState

    /** A card of today's lesson. [lesson].position is the card shown. */
    data class Card(val lesson: DailyLesson) : LearnUiState {
        val index: Int get() = lesson.position
        val total: Int get() = lesson.cards.size
        val isLast: Boolean get() = lesson.position == lesson.cards.lastIndex
        val canGoBack: Boolean get() = lesson.position > 0
    }

    data class Complete(val lesson: DailyLesson) : LearnUiState
}

/**
 * The daily micro-lesson screen's state. It asks the [LessonService] for today's lesson, moves through its cards (each
 * finished card is recorded there as a mastery signal), and starts again when the language being learned changes.
 */
class LearnViewModel(
    private val lessons: LessonService,
    settings: UserSettingsRepository,
) : ViewModel() {

    private var learning: Language = UserSettings().targetLanguage
    private val state = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            // Each time the language being learned (or the user's own) changes, the lesson for it is shown.
            settings.settings.map { it.targetLanguage to it.nativeLanguage }.distinctUntilChanged().collect { (target, _) ->
                learning = target
                show(lessons.today())
            }
        }
    }

    /** Looks for today's lesson again, for example when the tab is opened after new messages were translated. */
    fun refresh() {
        viewModelScope.launch { show(lessons.today()) }
    }

    fun next() {
        viewModelScope.launch { show(lessons.next()) }
    }

    fun previous() {
        viewModelScope.launch { show(lessons.previous()) }
    }

    private fun show(result: LessonState) {
        state.value = when (result) {
            LessonState.NoLesson -> LearnUiState.Empty(learning)
            is LessonState.InProgress -> LearnUiState.Card(result.lesson)
            is LessonState.Completed -> LearnUiState.Complete(result.lesson)
        }
    }
}
