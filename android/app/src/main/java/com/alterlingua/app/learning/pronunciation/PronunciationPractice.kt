package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.keyboard.VoiceFiles
import com.alterlingua.app.keyboard.VoiceRecorder
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.share.Speaker
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

/** Where a practice attempt is. */
sealed interface PracticePhase {
    data object Idle : PracticePhase

    /** The microphone has not been allowed yet. */
    data object NeedsPermission : PracticePhase

    data class Recording(val elapsedMillis: Long, val level: Float) : PracticePhase

    data object Assessing : PracticePhase

    /** [attempts] counts this card's attempts in this session. */
    data class Feedback(val feedback: PronunciationFeedback, val attempts: Int) : PracticePhase

    /** Something other than the learner's speech went wrong (offline, service down...). Nothing was recorded as practice. */
    data class Problem(val failure: VoiceFailure, val canRetry: Boolean) : PracticePhase
}

data class PracticeState(
    val term: String = "",
    /** The phone has a voice for the language being learned (Listen can be offered). */
    val canListen: Boolean = false,
    val listening: Boolean = false,
    val phase: PracticePhase = PracticePhase.Idle,
)

/**
 * Pronunciation practice for one lesson card: Listen, Repeat, record, assess, feedback.
 *
 * What it does and does not claim: the recording is sent to the speech-recognition service, in the language being learned,
 * and what comes back is text. That text is compared with the card's word or phrase ([PronunciationEvaluator]) and the
 * learner sees Good, Nearly, Try again or "couldn't hear that", plus what the recognizer heard. There is no numeric score
 * and no sound-by-sound analysis, because a speech recognizer does not provide one.
 *
 * Practice as a mastery signal: an attempt the recognizer understood (GOOD) and any other attempt that produced a
 * usable result are recorded in the Personal Language Map as pronunciation counts (only GOOD adds points). Silence, errors
 * and offline problems are not recorded as practice, and nothing is recorded if the recording never happened.
 *
 * Privacy: the recording is one temporary file in the app's private cache, deleted as soon as the answer arrives or the
 * attempt is cancelled. The transcript is compared and discarded; it is never stored or logged.
 */
class PronunciationPractice(
    private val scope: CoroutineScope,
    private val recorder: VoiceRecorder,
    private val files: VoiceFiles,
    private val api: VoiceApi,
    private val speaker: Speaker,
    private val map: LanguageMapService,
    private val languages: suspend () -> LessonLanguages,
    private val hasPermission: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRecordingMillis: Long = MAX_MILLIS,
    private val requestTimeoutMillis: Long = 60_000,
) {
    private val state = MutableStateFlow(PracticeState())
    val uiState: StateFlow<PracticeState> = state.asStateFlow()

    private var card: LessonCard? = null
    private var learning: Language? = null
    private var file: File? = null
    private var ticker: Job? = null
    private var assessment: Job? = null
    private var attempts = 0
    private var peak = 0f

    /** Starts practice for [next]. Whatever was going on for the previous card is stopped and its recording deleted. */
    fun bind(next: LessonCard) {
        if (card?.key == next.key && learning != null) return
        stopEverything()
        card = next
        attempts = 0
        state.value = PracticeState(term = next.term)
        scope.launch {
            val language = languages().learning
            learning = language
            val canListen = speaker.canSpeak(language)
            if (card?.key == next.key) state.value = state.value.copy(canListen = canListen)
        }
    }

    /** Listen: the phone's voice reads the word or phrase in the language being learned. */
    fun listen() {
        val current = card ?: return
        val language = learning ?: return
        if (!state.value.canListen) return
        if (state.value.listening) {
            speaker.stop()
            state.value = state.value.copy(listening = false)
        } else if (speaker.speak(current.term, language) { state.value = state.value.copy(listening = false) }) {
            state.value = state.value.copy(listening = true)
        }
    }

    /** Repeat (and Try again): starts recording. */
    fun startRecording() {
        card ?: return
        when (state.value.phase) {
            is PracticePhase.Recording, PracticePhase.Assessing -> return
            else -> Unit
        }
        if (!hasPermission()) {
            state.value = state.value.copy(phase = PracticePhase.NeedsPermission)
            return
        }
        speaker.stop()
        deleteFile()
        val recording = files.newFile()
        if (!recorder.start(recording)) {
            files.delete(recording)
            state.value = state.value.copy(listening = false, phase = PracticePhase.Problem(VoiceFailure.MIC_UNAVAILABLE, canRetry = true))
            return
        }
        file = recording
        peak = 0f
        state.value = state.value.copy(listening = false, phase = PracticePhase.Recording(0, 0f))
        ticker = scope.launch {
            var elapsed = 0L
            while (true) {
                delay(TICK_MILLIS)
                elapsed += TICK_MILLIS
                val level = (recorder.amplitude() / MAX_AMPLITUDE).coerceIn(0f, 1f)
                peak = maxOf(peak, level)
                state.value = state.value.copy(phase = PracticePhase.Recording(elapsed, level))
                if (elapsed >= maxRecordingMillis) {
                    stopRecording()
                    break
                }
            }
        }
    }

    /** The microphone permission question was answered. */
    fun permissionAnswered(granted: Boolean) {
        if (granted) startRecording() else state.value = state.value.copy(phase = PracticePhase.Idle)
    }

    /** Stop: ends the recording and assesses it. */
    fun stopRecording() {
        if (state.value.phase !is PracticePhase.Recording) return
        val elapsed = (state.value.phase as PracticePhase.Recording).elapsedMillis
        ticker?.cancel()
        val saved = recorder.stop()
        val recording = file
        val current = card
        if (!saved || recording == null || current == null || elapsed < MIN_MILLIS) {
            deleteFile()
            state.value = state.value.copy(phase = PracticePhase.Feedback(PronunciationFeedback(PronunciationVerdict.NOT_HEARD, null), attempts))
            return
        }
        if (peak < SILENCE_LEVEL) { // nothing but silence: not worth uploading
            deleteFile()
            state.value = state.value.copy(phase = PracticePhase.Feedback(PronunciationFeedback(PronunciationVerdict.NOT_HEARD, null), attempts))
            return
        }
        state.value = state.value.copy(phase = PracticePhase.Assessing)
        assessment = scope.launch { assess(current, recording) }
    }

    /** Leaving the card, the lesson or the screen: nothing keeps recording and no audio is left behind. */
    fun release() {
        stopEverything()
        card = null
        learning = null
    }

    private suspend fun assess(current: LessonCard, recording: File) {
        val langs = languages()
        val answer = try {
            withTimeout(requestTimeoutMillis) {
                api.translate(recording, RECORDING_TYPE, source = langs.learning.code, target = langs.native.code)
            }
        } catch (_: TimeoutCancellationException) {
            VoiceResult.Failure(VoiceFailure.TIMEOUT)
        } catch (cancelled: CancellationException) {
            deleteFile()
            throw cancelled
        }
        deleteFile() // the answer is here: the recording is not needed any more
        if (card?.key != current.key) return // the learner moved on
        val phase = when (answer) {
            is VoiceResult.Success -> judge(current, langs.learning, answer.value.transcript)
            is VoiceResult.Failure -> when (answer.failure) {
                // Nothing recognisable was said: a normal outcome, not an error.
                VoiceFailure.NO_SPEECH, VoiceFailure.TOO_SHORT, VoiceFailure.UNCLEAR_SPEECH ->
                    PracticePhase.Feedback(PronunciationFeedback(PronunciationVerdict.NOT_HEARD, null), attempts)
                // The words were understood but not translated: the transcript is all that is needed here.
                VoiceFailure.PARTIAL -> judge(current, langs.learning, answer.heard)
                else -> PracticePhase.Problem(answer.failure, answer.failure.canRetry)
            }
        }
        state.value = state.value.copy(phase = phase)
    }

    private suspend fun judge(current: LessonCard, language: Language, heard: String?): PracticePhase {
        val feedback = PronunciationEvaluator.evaluate(current.term, heard, language)
        if (feedback.verdict == PronunciationVerdict.NOT_HEARD) return PracticePhase.Feedback(feedback, attempts)
        attempts++
        recordSignal(current.key, current.term, feedback.verdict == PronunciationVerdict.GOOD)
        return PracticePhase.Feedback(feedback, attempts)
    }

    private suspend fun recordSignal(key: UnitKey, term: String, understood: Boolean) {
        try {
            map.recordPronunciation(key, understood, term, clock())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Saving the practice must never break the lesson.
        }
    }

    private fun stopEverything() {
        ticker?.cancel()
        assessment?.cancel()
        recorder.cancel()
        speaker.stop()
        deleteFile()
        state.value = state.value.copy(listening = false, phase = PracticePhase.Idle)
    }

    private fun deleteFile() {
        file?.let { files.delete(it) }
        file = null
    }

    companion object {
        const val RECORDING_TYPE = "audio/mp4"
        const val TICK_MILLIS = 100L
        const val MIN_MILLIS = 500L
        const val MAX_MILLIS = 8_000L
        private const val MAX_AMPLITUDE = 32767f
        private const val SILENCE_LEVEL = 0.02f
    }
}
