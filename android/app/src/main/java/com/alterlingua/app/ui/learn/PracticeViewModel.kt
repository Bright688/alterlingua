package com.alterlingua.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.pronunciation.PracticeState
import com.alterlingua.app.learning.pronunciation.PronunciationPractice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Holds the pronunciation practice for the lesson on screen. All the behaviour is in [PronunciationPractice]. */
class PracticeViewModel(create: (CoroutineScope) -> PronunciationPractice) : ViewModel() {
    private val practice = create(viewModelScope)
    val uiState: StateFlow<PracticeState> = practice.uiState

    fun bind(card: LessonCard) = practice.bind(card)
    fun listen() = practice.listen()
    fun repeat() = practice.startRecording()
    fun stop() = practice.stopRecording()
    fun permissionAnswered(granted: Boolean) = practice.permissionAnswered(granted)
    fun release() = practice.release()

    override fun onCleared() = practice.release()
}
