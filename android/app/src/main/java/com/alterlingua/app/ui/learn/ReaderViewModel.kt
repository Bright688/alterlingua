package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.assistance.HelpAnswer
import com.alterlingua.app.learning.assistance.OnDemandHelp
import com.alterlingua.app.learning.assistance.ReaderSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is shown after the user taps a word. */
sealed interface WordHelpState {
    data class Loading(val term: String) : WordHelpState
    data class Shown(val answer: HelpAnswer) : WordHelpState
}

data class ReaderUiState(
    val language: Language? = null,
    val input: String = "",
    /** The text being read, cut into words the user can ask about. Empty until "Show text". */
    val segments: List<ReaderSegment> = emptyList(),
    val help: WordHelpState? = null,
) {
    val reading: Boolean get() = segments.isNotEmpty()
}

/**
 * The On-demand reader: text pasted by the user stays in the language being learned, and a word's meaning appears only
 * when the user taps that word. The text lives only in memory here; nothing of it is saved. Asking is recorded as a mastery
 * signal by [OnDemandHelp], once per word per reading.
 */
class ReaderViewModel(private val help: OnDemandHelp) : ViewModel() {

    private val state = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = state.asStateFlow()
    private val asked = mutableSetOf<String>()

    init {
        refreshLanguage()
    }

    fun refreshLanguage() {
        viewModelScope.launch { help.learningLanguage().let { language -> state.update { it.copy(language = language) } } }
    }

    fun onInputChanged(text: String) = state.update { it.copy(input = text.take(MAX_CHARS)) }

    fun show() {
        val text = state.value.input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            asked.clear()
            val segments = help.read(text)
            state.update { it.copy(segments = segments, help = null, language = help.learningLanguage()) }
        }
    }

    /** The user tapped a word. Only words can be asked about; a repeat question in the same reading is not counted again. */
    fun ask(segment: ReaderSegment) {
        val normalized = segment.normalized ?: return
        val first = asked.add(normalized)
        state.update { it.copy(help = WordHelpState.Loading(segment.text)) }
        viewModelScope.launch {
            val answer = help.request(segment, record = first)
            state.update { it.copy(help = WordHelpState.Shown(answer)) }
        }
    }

    fun dismissHelp() = state.update { it.copy(help = null) }

    /** Forgets the text. */
    fun clear() {
        asked.clear()
        state.update { ReaderUiState(language = it.language) }
    }

    companion object {
        const val MAX_CHARS = 600
    }
}
