package com.alterlingua.app.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.LanguageMapSummary
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.WordEntry
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * [filter] null means "All". [summary] holds the totals shown on the filter tabs; [words] are the real entries of the Personal
 * Language Map of the language being learned (counts and meanings, never messages).
 */
data class WordsUiState(
    val summary: LanguageMapSummary,
    val words: List<WordEntry>,
    val query: String = "",
    val filter: MasteryStatus? = null,
) {
    /** True when the map has no entries at all yet (as opposed to none matching the search or filter). */
    val isEmpty: Boolean get() = words.isEmpty()

    val visibleWords: List<WordEntry>
        get() = words.filter { word ->
            (filter == null || word.status == filter) &&
                (query.isBlank() ||
                    word.term.contains(query.trim(), ignoreCase = true) ||
                    word.meaning.contains(query.trim(), ignoreCase = true))
        }
}

private data class WordsFilters(val query: String = "", val filter: MasteryStatus? = null)

class WordsViewModel(repository: UserSettingsRepository, map: LanguageMapService) : ViewModel() {
    private val filters = MutableStateFlow(WordsFilters())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val entries = repository.settings.map { it.targetLanguage.code }.distinctUntilChanged().flatMapLatest { map.observe(it) }

    val uiState: StateFlow<WordsUiState> = combine(entries, filters) { items, current ->
        WordsUiState(
            summary = LanguageMapSummary(
                newCount = items.count { it.masteryState == MasteryStatus.UNKNOWN },
                learningCount = items.count { it.masteryState == MasteryStatus.LEARNING },
                familiarCount = items.count { it.masteryState == MasteryStatus.FAMILIAR },
                masteredCount = items.count { it.masteryState == MasteryStatus.MASTERED },
            ),
            words = items.map { WordEntry(it.displayForm, "", it.meaning.orEmpty(), it.masteryState, it.exposureCount) },
            query = current.query,
            filter = current.filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WordsUiState(summary = LanguageMapSummary(0, 0, 0, 0), words = emptyList()),
    )

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    fun onFilterSelected(filter: MasteryStatus?) = filters.update { it.copy(filter = filter) }
}
