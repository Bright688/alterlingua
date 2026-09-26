package com.alterlingua.app

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.alterlingua.app.learning.engine.PipelineResult
import com.alterlingua.app.learning.lessons.TranslationMeaningProvider
import com.alterlingua.app.setup.SetupViewModel
import com.alterlingua.app.share.AndroidSpeaker
import com.alterlingua.app.share.ContentResolverAudioSource
import com.alterlingua.app.share.SharedAudioReader
import com.alterlingua.app.share.SharedVoiceViewModel
import com.alterlingua.app.share.VoiceNoteLearning
import java.io.File
import com.alterlingua.app.ui.home.HomeViewModel
import com.alterlingua.app.ui.learn.LearnViewModel
import com.alterlingua.app.speak.SpeakViewModel
import com.alterlingua.app.speak.SpokenAudioFiles
import com.alterlingua.app.speak.SpokenLanguages
import com.alterlingua.app.speak.SpokenTranslationFlow
import com.alterlingua.app.keyboard.MediaPlayerVoicePlayer
import com.alterlingua.app.ui.progress.ProgressViewModel
import com.alterlingua.app.ui.learn.PracticeViewModel
import com.alterlingua.app.keyboard.MediaRecorderVoiceRecorder
import com.alterlingua.app.keyboard.VoiceFiles
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.pronunciation.PronunciationPractice
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.first
import com.alterlingua.app.ui.learn.ReaderViewModel
import com.alterlingua.app.ui.onboarding.OnboardingViewModel
import com.alterlingua.app.ui.settings.SettingsViewModel
import com.alterlingua.app.ui.words.WordsViewModel

private fun CreationExtras.application(): AlterLinguaApplication =
    this[APPLICATION_KEY] as AlterLinguaApplication

/** Builds the ViewModels that need the saved-settings repository. Screens use `viewModel(factory = ...)`. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { AppViewModel(application().userSettings) }
        initializer { OnboardingViewModel(application().userSettings, createSavedStateHandle()) }
        initializer { HomeViewModel(application().userSettings, application().progressService, application().lessonService) }
        initializer { ProgressViewModel(application().progressService, application().userSettings) }
        initializer { SetupViewModel(application().setupChecker, application().userSettings) }
        initializer { LearnViewModel(application().lessonService, application().userSettings) }
        initializer {
            val app = application()
            PracticeViewModel { scope ->
                PronunciationPractice(
                    scope = scope,
                    recorder = MediaRecorderVoiceRecorder(app),
                    files = VoiceFiles(File(app.cacheDir, "pronunciation")),
                    api = app.voiceApi,
                    speaker = AndroidSpeaker(app),
                    map = app.languageMap,
                    languages = { app.userSettings.settings.first().let { LessonLanguages(it.targetLanguage, it.nativeLanguage) } },
                    hasPermission = { ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                )
            }
        }
        initializer {
            val app = application()
            SpeakViewModel { scope ->
                SpokenTranslationFlow(
                    scope = scope,
                    recorder = MediaRecorderVoiceRecorder(app),
                    recordings = VoiceFiles(File(app.cacheDir, "voice")),
                    spokenFiles = SpokenAudioFiles(File(app.cacheDir, "spoken_audio")),
                    player = MediaPlayerVoicePlayer(),
                    api = app.voiceApi,
                    speaker = AndroidSpeaker(app),
                    languages = { app.userSettings.settings.first().let { SpokenLanguages(it.nativeLanguage, it.targetLanguage) } },
                    hasPermission = { ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                    learning = app.learningRecorder,
                )
            }
        }
        initializer { ReaderViewModel(application().onDemandHelp) }
        initializer {
            val app = application()
            SharedVoiceViewModel(
                reader = SharedAudioReader(ContentResolverAudioSource(app.contentResolver), File(app.cacheDir, "shared_audio")),
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
        }
        initializer { WordsViewModel(application().userSettings, application().languageMap) }
        initializer { SettingsViewModel(application().userSettings, application().incomingStatus.last, liveChatReading = application().liveChatStatus.last, voiceCaptureListening = com.alterlingua.app.capture.VoiceCaptureState.listening, eraseLearningData = application().learningDataEraser::eraseAll) }
    }
}
