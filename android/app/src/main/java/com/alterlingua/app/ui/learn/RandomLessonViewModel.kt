package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.LessonState
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Random lesson": practice beyond the one daily lesson, built fresh from the same Personal Language Map and the same
 * quality gate ([com.alterlingua.app.learning.lessons.LessonSelector.selectRandom]), but shuffled, so asking again can give a
 * different lesson. [start] is called each time the learner opens this screen.
 *
 * Unlike the daily lesson, nothing here is saved: the lesson lives only in this ViewModel, for as long as the screen is
 * open, and is gone (not resumed, not remembered) the next time [start] is called. Finishing a card still records the
 * same genuine-practice signal in the Personal Language Map that a daily-lesson card does.
 */
class RandomLessonViewModel(
    private val lessons: LessonService,
    private val map: LanguageMapService,
    private val settings: UserSettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private var learning: Language = UserSettings().targetLanguage
    private val state = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = state.asStateFlow()

    /** Builds a fresh random lesson for whatever the learner is currently learning. */
    fun start() {
        state.value = LearnUiState.Loading
        viewModelScope.launch {
            learning = settings.settings.first().targetLanguage
            show(lessons.random())
        }
    }

    /** The learner finished the card they were on: record it (the same signal a daily-lesson card gives), and move on. */
    fun next() {
        val lesson = (state.value as? LearnUiState.Card)?.lesson ?: return
        viewModelScope.launch {
            val card = lesson.cards[lesson.position]
            if (lesson.position !in lesson.encountered) map.recordLessonEncounter(card.key, card.term, clock())
            val encountered = lesson.encountered + lesson.position
            val finished = lesson.position >= lesson.cards.lastIndex
            show(lesson.copy(position = if (finished) lesson.position else lesson.position + 1, encountered = encountered, completed = finished))
        }
    }

    /** The learner said whether they already knew this card, before being shown whether they were right. */
    fun recordRecognition(card: LessonCard, correct: Boolean) {
        viewModelScope.launch { lessons.recordRecognition(card, correct) }
    }

    /** Back to the previous card. Going back records nothing (matches the daily lesson). */
    fun previous() {
        val lesson = (state.value as? LearnUiState.Card)?.lesson ?: return
        if (lesson.position == 0) return
        show(lesson.copy(position = lesson.position - 1))
    }

    private fun show(lesson: DailyLesson) {
        state.value = if (lesson.completed) LearnUiState.Complete(lesson) else LearnUiState.Card(lesson)
    }

    private fun show(result: LessonState) {
        state.value = when (result) {
            LessonState.NoLesson -> LearnUiState.Empty(learning)
            is LessonState.InProgress -> LearnUiState.Card(result.lesson)
            is LessonState.Completed -> LearnUiState.Complete(result.lesson) // random() never returns this; kept for exhaustiveness
        }
    }
}
