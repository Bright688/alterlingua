package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.reading.ReadingLessonService
import com.alterlingua.app.learning.reading.ReadingSessionState
import com.alterlingua.app.share.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Reading exercise's state (Lesson menu -> Reading, see ForgeScreen.kt's LessonMenuScreen). Unlike the
 * Vocabulary curriculum, nothing is saved between sessions: a fresh set of real sentences (see
 * [ReadingLessonService]) is picked each time [start] is called.
 */
class ReadingLessonViewModel(
    private val service: ReadingLessonService,
    private val speaker: Speaker,
) : ViewModel() {

    private val state = MutableStateFlow<ReadingSessionState>(ReadingSessionState.Loading)
    val uiState: StateFlow<ReadingSessionState> = state.asStateFlow()

    private val listeningState = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningState.asStateFlow()

    private val canListenState = MutableStateFlow(false)
    val canListen: StateFlow<Boolean> = canListenState.asStateFlow()

    fun start() {
        state.value = ReadingSessionState.Loading
        viewModelScope.launch { show(service.start()) }
    }

    /** The learner asked to see the current sentence's meaning. */
    fun reveal() {
        val current = state.value as? ReadingSessionState.InProgress ?: return
        viewModelScope.launch { state.value = service.reveal(current) }
    }

    /** Hears the current sentence spoken aloud (on-device text-to-speech, like every other Listen button). */
    fun listen() {
        val current = state.value as? ReadingSessionState.InProgress ?: return
        if (listeningState.value) {
            speaker.stop()
            listeningState.value = false
            return
        }
        listeningState.value = speaker.speak(current.current.sentence, current.language) { listeningState.value = false }
    }

    fun next() {
        val current = state.value as? ReadingSessionState.InProgress ?: return
        listeningState.value = false
        speaker.stop()
        state.value = service.next(current)
    }

    private suspend fun show(result: ReadingSessionState) {
        state.value = result
        val language = (result as? ReadingSessionState.InProgress)?.language
        canListenState.value = language != null && speaker.canSpeak(language)
    }

    override fun onCleared() {
        speaker.stop()
    }
}
