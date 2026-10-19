package com.alterlingua.app.speak

import com.alterlingua.app.keyboard.VoiceFiles
import com.alterlingua.app.keyboard.VoicePlayer
import com.alterlingua.app.keyboard.VoiceRecorder
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/** The language the user speaks (their own) and the language the message is translated into and spoken in. */
data class SpokenLanguages(val spoken: Language, val target: Language)

/** Where the generated speech is kept: a folder in the app's private cache that only [SpokenFileProvider] paths reach. */
class SpokenAudioFiles(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    /** A fresh file for generated speech. Android's on-device text-to-speech always writes WAV, but [Speaker]
     * re-encodes it to AAC before this file is ever read -- WhatsApp's own voice-message bubble will not play a
     * raw WAV. */
    fun newFile(): File {
        directory.mkdirs()
        return File(directory, "voice-${UUID.randomUUID()}.m4a")
    }

    fun delete(file: File?) {
        file?.delete()
    }

    /** Removes anything older than [olderThanMillis]: leftovers of a crash or a killed screen. */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    companion object {
        const val STALE_MILLIS = 60L * 60 * 1000
        const val AUDIO_TYPE = "audio/mp4"
    }
}

/** A finished translated voice message, ready to listen to and share. */
data class SpokenResult(
    val originalCode: String,
    val transcript: String,
    val target: Language,
    val translation: String,
    val audioFile: File,
    val audioType: String,
    val playing: Boolean = false,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "SpokenResult(redacted)"
}

sealed interface SpeakPhase {
    data object Idle : SpeakPhase
    data object NeedsPermission : SpeakPhase
    data class Recording(val elapsedMillis: Long, val level: Float) : SpeakPhase
    data object Working : SpeakPhase
    data class Result(val value: SpokenResult) : SpeakPhase
    data class Problem(val failure: VoiceFailure, val canRetry: Boolean, val heard: String? = null) : SpeakPhase
}

data class SpeakState(val spoken: Language? = null, val target: Language? = null, val phase: SpeakPhase = SpeakPhase.Idle)

/** A transcript already translated, waiting only to be spoken on the device; kept so a synthesis failure can be
 * retried without re-uploading the recording (which is already gone by the time synthesis is attempted). */
private data class PendingSpeech(val sourceCode: String, val transcript: String, val target: Language, val translation: String)

/**
 * Translated outgoing voice: record in your language, hear it translated and spoken in another, share it.
 *
 * The backend (CLAUDE.md 23) only transcribes and translates the recording; the target-language speech is generated
 * on the device with Android's own text-to-speech (CLAUDE.md 37, 41: no cloud provider currently has a real voice for
 * every catalogue language, while the device usually does, and nothing about the message needs to leave the phone a
 * second time just to be spoken). [VoiceFailure.NO_VOICE_FOR_TARGET] now means "this phone has no voice installed for
 * that language" rather than a cloud-provider gap.
 *
 * Promises (CLAUDE.md 18, 20, 25):
 * - The languages are never assumed: the spoken language is the user's own, the target is the one chosen on screen.
 * - Nothing is sent to anyone. The finished audio is only handed to Android's Share sheet when the user taps Share, and
 *   the user then picks the chat and presses Send themselves.
 * - The user can listen before sharing, and record again.
 * - The recording is deleted as soon as it has been transcribed and translated (kept only while Try again can use it
 *   to retry that step). The generated speech is deleted when the user records again, leaves the screen, or the
 *   screen is destroyed; old leftovers are swept when the screen opens.
 * - Transcript and translation are never logged. Only when the user shares does the translated text go to the
 *   learning engine (as with the keyboard microphone). Each finished message is also saved to [history] (own
 *   language text, CLAUDE.md section 25's usual preference against this is a deliberate, explicit exception --
 *   see [SpokenHistoryEntry]'s own privacy note), so the owner can review and delete past messages.
 * - Typing instead of speaking (CLAUDE.md 23) skips the recording and transcription step only: the typed text still goes
 *   through the same text-translate backend, the same on-device speech, and the same Result/Listen/Share screen.
 */
class SpokenTranslationFlow(
    private val scope: CoroutineScope,
    private val recorder: VoiceRecorder,
    private val recordings: VoiceFiles,
    private val spokenFiles: SpokenAudioFiles,
    private val player: VoicePlayer,
    private val api: VoiceApi,
    /** Translates typed text (sendTyped); the recorded path keeps using [api], which transcribes and translates together. */
    private val translationApi: TranslationApi,
    private val speaker: Speaker,
    private val languages: suspend () -> SpokenLanguages,
    private val hasPermission: () -> Boolean,
    private val learning: LearningRecorder = LearningRecorder.None,
    private val history: SpokenHistoryStore = SpokenHistoryStore.None,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRecordingMillis: Long = MAX_MILLIS,
    private val requestTimeoutMillis: Long = 120_000,
) {
    private val state = MutableStateFlow(SpeakState())
    val uiState: StateFlow<SpeakState> = state.asStateFlow()

    private var recording: File? = null
    private var pending: PendingSpeech? = null
    /** The last typed text sent, kept only so Retry can resend it if the translate step itself failed. */
    private var lastTyped: String? = null
    private var ticker: Job? = null
    private var request: Job? = null
    private var peak = 0f

    /** Reads the languages and clears leftovers. Call once when the screen opens. */
    fun open() {
        spokenFiles.sweep()
        scope.launch {
            val langs = languages()
            state.value = state.value.copy(spoken = langs.spoken, target = langs.target)
        }
    }

    /** Picks the language to translate into and speak, for this message only. */
    fun chooseTarget(language: Language) {
        val phase = state.value.phase
        if (phase is SpeakPhase.Recording || phase is SpeakPhase.Working) return
        state.value = state.value.copy(target = language)
    }

    fun start() {
        when (state.value.phase) {
            is SpeakPhase.Recording, SpeakPhase.Working -> return
            else -> Unit
        }
        if (!hasPermission()) {
            state.value = state.value.copy(phase = SpeakPhase.NeedsPermission)
            return
        }
        discardEverything()
        val file = recordings.newFile()
        if (!recorder.start(file)) {
            recordings.delete(file)
            state.value = state.value.copy(phase = SpeakPhase.Problem(VoiceFailure.MIC_UNAVAILABLE, canRetry = false))
            return
        }
        recording = file
        peak = 0f
        state.value = state.value.copy(phase = SpeakPhase.Recording(0, 0f))
        ticker = scope.launch {
            var elapsed = 0L
            while (true) {
                delay(TICK_MILLIS)
                elapsed += TICK_MILLIS
                val level = (recorder.amplitude() / MAX_AMPLITUDE).coerceIn(0f, 1f)
                peak = maxOf(peak, level)
                state.value = state.value.copy(phase = SpeakPhase.Recording(elapsed, level))
                if (elapsed >= maxRecordingMillis) {
                    stop()
                    break
                }
            }
        }
    }

    fun permissionAnswered(granted: Boolean) {
        if (granted) start() else state.value = state.value.copy(phase = SpeakPhase.Idle)
    }

    fun stop() {
        val phase = state.value.phase as? SpeakPhase.Recording ?: return
        ticker?.cancel()
        val saved = recorder.stop()
        val file = recording
        when {
            !saved || file == null || phase.elapsedMillis < MIN_MILLIS -> {
                deleteRecording()
                state.value = state.value.copy(phase = SpeakPhase.Problem(VoiceFailure.TOO_SHORT, canRetry = false))
            }
            peak < SILENCE_LEVEL -> { // nothing but silence: not worth uploading
                deleteRecording()
                state.value = state.value.copy(phase = SpeakPhase.Problem(VoiceFailure.NO_SPEECH, canRetry = false))
            }
            else -> send(file)
        }
    }

    /** Try again after a problem that may pass: only the step that failed is repeated. */
    fun retry() {
        if (state.value.phase !is SpeakPhase.Problem) return
        val stillPending = pending
        if (stillPending != null) {
            state.value = state.value.copy(phase = SpeakPhase.Working)
            request = scope.launch { speakLocally(stillPending) }
            return
        }
        val file = recording
        if (file != null) return send(file)
        val text = lastTyped ?: return
        sendTyped(text)
    }

    /** Type instead of speaking: the typed text is translated and then spoken on the device, same as a recording. */
    fun sendTyped(text: String) {
        if (state.value.phase is SpeakPhase.Recording || state.value.phase is SpeakPhase.Working) return
        if (text.isBlank()) return
        discardEverything()
        lastTyped = text
        val target = state.value.target ?: return
        state.value = state.value.copy(phase = SpeakPhase.Working)
        request = scope.launch {
            val source = state.value.spoken?.code ?: "auto"
            val result = try {
                withTimeout(requestTimeoutMillis) { translationApi.translate(TranslationRequest(text = text, target = target.code, source = source)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: TimeoutCancellationException) {
                TranslationResult.Failure(TranslationFailure.TIMEOUT)
            }
            when (result) {
                is TranslationResult.Failure -> {
                    val failure = result.failure.toVoiceFailure()
                    state.value = state.value.copy(phase = SpeakPhase.Problem(failure, failure.canRetry))
                }
                is TranslationResult.Success ->
                    speakLocally(PendingSpeech(result.translation.sourceLanguage, text, target, result.translation.text))
            }
        }
    }

    private fun send(file: File) {
        state.value = state.value.copy(phase = SpeakPhase.Working)
        request = scope.launch {
            val langs = state.value
            val spoken = langs.spoken ?: return@launch
            val target = langs.target ?: return@launch
            val answer = try {
                withTimeout(requestTimeoutMillis) { api.translate(file, RECORDING_TYPE, source = spoken.code, target = target.code) }
            } catch (_: TimeoutCancellationException) {
                VoiceResult.Failure(VoiceFailure.TIMEOUT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            when (answer) {
                is VoiceResult.Failure -> {
                    if (!answer.failure.canRetry) deleteRecording()
                    state.value = state.value.copy(phase = SpeakPhase.Problem(answer.failure, answer.failure.canRetry, answer.heard))
                }
                is VoiceResult.Success -> {
                    deleteRecording() // the answer is here: the recording is not needed any more, even if speaking it fails
                    speakLocally(PendingSpeech(answer.value.sourceLanguage, answer.value.transcript, target, answer.value.translation))
                }
            }
        }
    }

    private suspend fun speakLocally(speech: PendingSpeech) {
        pending = speech
        if (!speaker.canSpeak(speech.target)) {
            pending = null
            state.value = state.value.copy(phase = SpeakPhase.Problem(VoiceFailure.NO_VOICE_FOR_TARGET, canRetry = false))
            return
        }
        val generated = spokenFiles.newFile()
        if (!speaker.synthesizeToFile(speech.translation, speech.target, generated)) {
            spokenFiles.delete(generated)
            // pending stays set: retry() speaks the same already-translated text again, without touching the network.
            state.value = state.value.copy(phase = SpeakPhase.Problem(VoiceFailure.TRANSLATION_FAILED, canRetry = true))
            return
        }
        pending = null
        state.value = state.value.copy(
            phase = SpeakPhase.Result(SpokenResult(speech.sourceCode, speech.transcript, speech.target, speech.translation, generated, SpokenAudioFiles.AUDIO_TYPE)),
        )
        scope.launch {
            history.add(
                SpokenHistoryEntry(
                    createdAtMillis = clock(),
                    sourceLanguage = Languages.fromCode(speech.sourceCode),
                    targetLanguage = speech.target,
                    transcript = speech.transcript,
                    translation = speech.translation,
                ),
            )
        }
    }

    /** Listen: plays the generated speech, so the user can check it before sharing. */
    fun listen() {
        val result = (state.value.phase as? SpeakPhase.Result)?.value ?: return
        if (result.playing) {
            player.stop()
            setResult(result.copy(playing = false))
        } else if (player.play(result.audioFile) { (state.value.phase as? SpeakPhase.Result)?.let { setResult(it.value.copy(playing = false)) } }) {
            setResult(result.copy(playing = true))
        }
    }

    /**
     * The user tapped Share. Returns the file to hand to Android's Share sheet (the caller wraps it in a content address),
     * and lets the learning engine see the translated text. Nothing is sent by this call.
     */
    fun prepareShare(): SpokenResult? {
        val result = (state.value.phase as? SpeakPhase.Result)?.value ?: return null
        player.stop()
        setResult(result.copy(playing = false))
        learning.record(TranslationInteraction(InteractionKind.OUTGOING_VOICE, result.translation, result.target.code))
        return result
    }

    /** Record again: throws the generated speech away. */
    fun recordAgain() {
        discardEverything()
        state.value = state.value.copy(phase = SpeakPhase.Idle)
    }

    /** The screen closed or Cancel: stops everything and deletes every file. */
    fun release() {
        discardEverything()
        state.value = state.value.copy(phase = SpeakPhase.Idle)
    }

    private fun setResult(value: SpokenResult) {
        state.value = state.value.copy(phase = SpeakPhase.Result(value))
    }

    private fun discardEverything() {
        ticker?.cancel()
        request?.cancel()
        recorder.cancel()
        player.stop()
        deleteRecording()
        pending = null
        lastTyped = null
        (state.value.phase as? SpeakPhase.Result)?.let { spokenFiles.delete(it.value.audioFile) }
    }

    private fun deleteRecording() {
        recording?.let { recordings.delete(it) }
        recording = null
    }

    /** Maps the text-translate backend's failures onto the same [VoiceFailure] vocabulary the recorded path already
     * shows messages for (CLAUDE.md: one language-aware service, not separate behaviour per input path). */
    private fun TranslationFailure.toVoiceFailure(): VoiceFailure = when (this) {
        TranslationFailure.OFFLINE -> VoiceFailure.OFFLINE
        TranslationFailure.BACKEND_UNAVAILABLE -> VoiceFailure.BACKEND_UNAVAILABLE
        TranslationFailure.TIMEOUT -> VoiceFailure.TIMEOUT
        TranslationFailure.NOT_CONFIGURED -> VoiceFailure.NOT_CONFIGURED
        TranslationFailure.UNSUPPORTED_LANGUAGE, TranslationFailure.UNSUPPORTED_PAIR -> VoiceFailure.UNSUPPORTED_TARGET
        TranslationFailure.SOURCE_UNDETECTED -> VoiceFailure.UNCLEAR_SPEECH
        else -> VoiceFailure.TRANSLATION_FAILED
    }

    companion object {
        const val RECORDING_TYPE = "audio/mp4"
        const val TICK_MILLIS = 100L
        const val MIN_MILLIS = 700L
        const val MAX_MILLIS = 60_000L
        private const val MAX_AMPLITUDE = 32767f
        private const val SILENCE_LEVEL = 0.02f
    }
}
