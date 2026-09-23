package com.alterlingua.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.LanguageMapSummary
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.LessonState
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.progress.DependenceStatus
import com.alterlingua.app.learning.progress.ProgressService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One item of today's lesson as Home shows it. */
data class LessonChip(val term: String, val meaning: String)

data class HomeUiState(
    val targetLanguage: String,
    /** Real counts from the Progress log for the current week (zero until there is activity; never sample figures). */
    val translationsThisWeek: Int,
    val newWordsThisWeek: Int,
    /** Today's real lesson (from the Learn engine); empty until there is something worth a lesson. */
    val lessonItems: List<LessonChip>,
    /** Real: how many of the language's words and phrases are in each state. */
    val languageMap: LanguageMapSummary,
    /** Real Translation Dependence: NoData, Collecting or Ready. */
    val dependence: DependenceStatus = DependenceStatus.NoData,
) {
    companion object {
        /** Everything at zero and no lesson yet. */
        fun empty(language: Language = UserSettings().targetLanguage) = HomeUiState(
            targetLanguage = language.displayName,
            translationsThisWeek = 0,
            newWordsThisWeek = 0,
            lessonItems = emptyList(),
            languageMap = LanguageMapSummary(0, 0, 0, 0),
        )
    }
}

/**
 * The language being learned comes from the user's saved settings. The counts, the language map and translation dependence are
 * real: they come from the Progress report, and the lesson card shows today's real lesson.
 */
class HomeViewModel(
    private val repository: UserSettingsRepository,
    private val progress: ProgressService,
    private val lessons: LessonService,
) : ViewModel() {
    private val lesson = MutableStateFlow<List<LessonChip>>(emptyList())
    private val report = MutableStateFlow<com.alterlingua.app.learning.progress.ProgressReport?>(null)

    val uiState: StateFlow<HomeUiState> = combine(repository.settings.map { it.targetLanguage }.distinctUntilChanged(), report, lesson) { language, current, chips ->
        val empty = HomeUiState.empty(language).copy(lessonItems = chips)
        if (current == null || current.language != language.code) empty else empty.copy(
            translationsThisWeek = current.thisWeek.translations,
            newWordsThisWeek = current.thisWeek.newWords,
            languageMap = LanguageMapSummary(
                newCount = current.totals.newCount,
                learningCount = current.totals.learning,
                familiarCount = current.totals.familiar,
                masteredCount = current.totals.mastered,
            ),
            dependence = current.dependence,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.empty())

    init {
        refresh()
    }

    /** Looks at the real figures again, for example when the tab is opened. */
    fun refresh() {
        viewModelScope.launch {
            val language = repository.settings.first().targetLanguage
            report.value = progress.report(language)
            lesson.value = when (val state = lessons.today()) {
                LessonState.NoLesson -> emptyList()
                is LessonState.InProgress -> state.lesson.cards.map { LessonChip(it.term, it.meaning.orEmpty()) }
                is LessonState.Completed -> state.lesson.cards.map { LessonChip(it.term, it.meaning.orEmpty()) }
            }
        }
    }
}
