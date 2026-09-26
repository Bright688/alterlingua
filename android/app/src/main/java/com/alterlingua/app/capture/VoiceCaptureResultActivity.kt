package com.alterlingua.app.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.learning.engine.PipelineResult
import com.alterlingua.app.learning.lessons.TranslationMeaningProvider
import com.alterlingua.app.localization.LocalizedActivity
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.share.AndroidSpeaker
import com.alterlingua.app.share.SharedAudioReader
import com.alterlingua.app.share.SharedVoiceScreen
import com.alterlingua.app.share.SharedVoiceViewModel
import com.alterlingua.app.share.VoiceNoteLearning
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Opened from the "Voice note captured" notification. It shows the same Voice Translation screen as a voice note shared into
 * AlterLingua, and reuses that flow unchanged: the recording is read through [CapturedAudioSource] instead of a share, then
 * transcribed, translated and shown, and the audio is deleted as soon as the backend has answered.
 */
class VoiceCaptureResultActivity : LocalizedActivity() {

    private val viewModel: SharedVoiceViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AlterLinguaApplication
                SharedVoiceViewModel(
                    reader = SharedAudioReader(CapturedAudioSource(File(app.cacheDir, VoiceCaptureService.DIRECTORY)), File(app.cacheDir, "shared_audio")),
                    api = app.voiceApi,
                    settings = app.userSettings,
                    analyzers = app.analyzers,
                    map = app.languageMap,
                    meanings = TranslationMeaningProvider(app.translationApi),
                    learning = VoiceNoteLearning { interaction ->
                        runCatching { app.progressLog.translation(app.userSettings.settings.first().targetLanguage.code, interaction.kind) }
                        app.learningPipeline.record(interaction) is PipelineResult.Recorded
                    },
                    speaker = AndroidSpeaker(app),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation recreates the activity but keeps the view model, which ignores a second start for the same screen.
        viewModel.start(intent.getStringExtra(EXTRA_ADDRESS))
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SharedVoiceScreen(
                    state = state,
                    onListen = viewModel::listen,
                    onReview = {
                        startActivity(AppLinks.learnIntent(this))
                        finish()
                    },
                    onRetry = viewModel::retry,
                    onClose = {
                        viewModel.cancel()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.start(intent.getStringExtra(EXTRA_ADDRESS), force = true)
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
