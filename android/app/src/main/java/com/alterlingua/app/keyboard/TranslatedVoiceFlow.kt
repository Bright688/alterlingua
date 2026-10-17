package com.alterlingua.app.keyboard

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.TtsShareAccess
import com.alterlingua.app.entitlements.TtsShareUsageService
import com.alterlingua.app.learning.Language
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.speak.SpokenAudioFiles
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the keyboard shows while giving a just-translated message a voice, and once it is shared. */
sealed interface TranslatedVoiceUiState {
    data object Idle : TranslatedVoiceUiState

    /** On-device text-to-speech is turning the translation into a playable file. */
    data object Making : TranslatedVoiceUiState

    /** FREE/Standard's monthly trial has been used up; [limit] is how many it allowed. */
    data class LimitReached(val limit: Int) : TranslatedVoiceUiState

    /** No voice is available for the language, or the device's speech engine failed. */
    data object Failed : TranslatedVoiceUiState
}

/**
 * Gives a message the keyboard just translated a voice and hands it to Android's Share sheet, so the user can send
 * it as a voice message themselves -- AlterLingua never sends anything (CLAUDE.md section 18). Entirely on-device
 * text-to-speech (the same [Speaker] the standalone "Translated voice" screen uses, see SpokenTranslationFlow):
 * nothing about the message leaves the phone a second time just to be spoken. The generated file is written into
 * the existing shared "spoken_audio" cache folder ([SpokenAudioFiles]), the same one the standalone screen already
 * uses and the app's FileProvider already declares, so nothing new needs to be exported.
 *
 * Premium: unlimited. FREE and Standard: the monthly trial [TtsShareUsageService] enforces.
 */
class TranslatedVoiceFlow(
    private val speaker: Speaker,
    private val files: SpokenAudioFiles,
    private val entitlements: EntitlementService,
    private val usage: TtsShareUsageService,
    /** Hands the finished file to Android's Share sheet. Never called with a tier past its limit. */
    private val share: (file: File, contentType: String) -> Unit,
    private val scope: CoroutineScope,
) {
    var state: TranslatedVoiceUiState = TranslatedVoiceUiState.Idle
        private set

    var onStateChanged: ((TranslatedVoiceUiState) -> Unit)? = null

    private var job: Job? = null

    /** The user tapped the keyboard's "Voice" button on a just-translated message. */
    fun speakAndShare(text: String, target: Language) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val access = usage.access(entitlements.tier())
                when (access) {
                    is TtsShareAccess.LimitReached -> set(TranslatedVoiceUiState.LimitReached(access.limit))
                    TtsShareAccess.Allowed -> make(text, target)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                set(TranslatedVoiceUiState.Failed)
            }
        }
    }

    private suspend fun make(text: String, target: Language) {
        set(TranslatedVoiceUiState.Making)
        val file = files.newFile()
        if (!speaker.synthesizeToFile(text, target, file)) {
            files.delete(file)
            set(TranslatedVoiceUiState.Failed)
            return
        }
        usage.recordUse()
        set(TranslatedVoiceUiState.Idle)
        share(file, SpokenAudioFiles.AUDIO_TYPE)
    }

    fun dismiss() = set(TranslatedVoiceUiState.Idle)

    fun release() {
        job?.cancel()
        job = null
    }

    private fun set(new: TranslatedVoiceUiState) {
        if (new == state) return
        state = new
        onStateChanged?.invoke(new)
    }
}
