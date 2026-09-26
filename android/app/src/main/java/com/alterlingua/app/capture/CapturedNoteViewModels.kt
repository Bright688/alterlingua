package com.alterlingua.app.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.learning.engine.PipelineResult
import com.alterlingua.app.learning.lessons.TranslationMeaningProvider
import com.alterlingua.app.share.AndroidSpeaker
import com.alterlingua.app.share.SharedAudioReader
import com.alterlingua.app.share.SharedVoiceViewModel
import com.alterlingua.app.share.VoiceNoteLearning
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Builds the processing for one captured voice note: the same [SharedVoiceViewModel] a shared voice note uses, unchanged,
 * except that it reads the recording through [CapturedAudioSource] (a private file made by the capture) instead of a share.
 * It lives in its own [ViewModelStore], so clearing that store stops it, deletes its audio copy and releases its speech engine.
 */
internal fun newCapturedNoteViewModel(app: AlterLinguaApplication, store: ViewModelStore): SharedVoiceViewModel {
    fun build() = SharedVoiceViewModel(
        reader = SharedAudioReader(CapturedAudioSource(File(app.cacheDir, VoiceCaptureService.DIRECTORY)), File(app.cacheDir, "shared_audio")),
        api = app.voiceApi,
        settings = app.userSettings,
        analyzers = app.analyzers,
        map = app.languageMap,
        meanings = TranslationMeaningProvider(app.translationApi),
        learning = VoiceNoteLearning { interaction ->
            // A translated voice note is counted (kind only) and then offered to the learning pipeline.
            runCatching { app.progressLog.translation(app.userSettings.settings.first().targetLanguage.code, interaction.kind) }
            app.learningPipeline.record(interaction) is PipelineResult.Recorded
        },
        speaker = AndroidSpeaker(app),
    )
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = build() as T
    }
    return ViewModelProvider(store, factory)[SharedVoiceViewModel::class.java]
}
