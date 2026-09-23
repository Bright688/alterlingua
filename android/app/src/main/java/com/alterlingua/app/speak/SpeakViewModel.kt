package com.alterlingua.app.speak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Holds the translated-voice flow for the screen. All behaviour is in [SpokenTranslationFlow]. */
class SpeakViewModel(create: (CoroutineScope) -> SpokenTranslationFlow) : ViewModel() {
    private val flow = create(viewModelScope)
    val uiState: StateFlow<SpeakState> = flow.uiState

    init {
        flow.open()
    }

    fun chooseTarget(language: Language) = flow.chooseTarget(language)
    fun start() = flow.start()
    fun stop() = flow.stop()
    fun permissionAnswered(granted: Boolean) = flow.permissionAnswered(granted)
    fun retry() = flow.retry()
    fun listen() = flow.listen()
    fun prepareShare() = flow.prepareShare()
    fun recordAgain() = flow.recordAgain()
    fun release() = flow.release()

    override fun onCleared() = flow.release()
}
