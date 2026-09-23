package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

/** The languages voice input works with: the one the user speaks (their own) and the one they translate into. */
data class VoiceLanguages(val spoken: Language, val target: Language)

/** What the voice panel shows. */
sealed interface VoiceUiState {
    data object Idle : VoiceUiState

    /** The microphone has not been allowed yet; the panel explains why before asking. */
    data object NeedsPermission : VoiceUiState

    /** [levels] are the recent loudness values (0 to 1) for the waveform, oldest first. */
    data class Recording(val spoken: Language, val elapsedMillis: Long, val levels: List<Float>) : VoiceUiState

    data class Understanding(val target: Language) : VoiceUiState

    data class Translating(val target: Language) : VoiceUiState

    /** [translation] is what will be inserted; while [editing] it is the text being edited. */
    data class Result(
        val original: Language?,
        val originalText: String,
        val target: Language,
        val translation: String,
        val playing: Boolean = false,
        val editing: Boolean = false,
    ) : VoiceUiState

    /** [heard] is the recognised text, when the words were understood but something later failed. */
    data class Failed(val failure: VoiceFailure, val canRetry: Boolean, val heard: String? = null) : VoiceUiState
}

/**
 * Voice input: record, upload, review, insert.
 *
 * Promises (CLAUDE.md sections 18, 20, 25, 41):
 * - The language spoken is the user's own; the target is read from the settings when recording stops, never assumed.
 * - The translation goes into the composer only when the user taps Insert, and only as typed text. Nothing is sent.
 * - Recordings live in one temporary file which is deleted when the panel closes for any reason: insert, cancel,
 *   record again, the keyboard being dismissed, another app or field taking over, or the service ending.
 * - Silence and clips too short to hold speech are not uploaded.
 * - Transcript and translation are held in memory only and never logged.
 */
class VoiceFlow(
    private val recorder: VoiceRecorder,
    private val player: VoicePlayer,
    private val api: VoiceApi,
    private val files: VoiceFiles,
    private val hasPermission: () -> Boolean,
    private val traits: () -> EditorTraits,
    private val languages: suspend () -> VoiceLanguages,
    private val insert: (String) -> Unit,
    private val scope: CoroutineScope,
    private val maxRecordingMillis: Long = 60_000,
    private val requestTimeoutMillis: Long = 90_000,
    /** Told about a voice translation the user inserted, so the learning engine can pick out useful units. */
    private val learning: LearningRecorder = LearningRecorder.None,
) {
    var state: VoiceUiState = VoiceUiState.Idle
        private set

    var onStateChanged: ((VoiceUiState) -> Unit)? = null

    private var languagesInUse: VoiceLanguages? = null
    private var recordingFile: File? = null
    private var ticker: Job? = null
    private var upload: Job? = null
    private var peakLevel = 0f
    private var elapsed = 0L
    private val levels = ArrayDeque<Float>()
    private val edited = StringBuilder()

    /** True while the user is editing the translation with the keyboard. */
    val isEditing: Boolean get() = (state as? VoiceUiState.Result)?.editing == true

    /** The microphone button, and Record again. */
    fun start() {
        when (state) {
            is VoiceUiState.Recording, is VoiceUiState.Understanding, is VoiceUiState.Translating -> return
            else -> Unit
        }
        val editor = traits()
        if (editor.isPassword || editor.isKeyEventOnly) return fail(VoiceFailure.NOT_AVAILABLE_HERE)
        if (!hasPermission()) return set(VoiceUiState.NeedsPermission)
        discard()
        ticker = scope.launch {
            val chosen = try {
                languages()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch fail(VoiceFailure.RECORDING_FAILED)
            }
            languagesInUse = chosen
            val file = files.newFile()
            if (!recorder.start(file)) {
                files.delete(file)
                return@launch fail(VoiceFailure.MIC_UNAVAILABLE)
            }
            recordingFile = file
            elapsed = 0
            peakLevel = 0f
            levels.clear()
            set(VoiceUiState.Recording(chosen.spoken, 0, emptyList()))
            while (true) {
                delay(TICK_MILLIS)
                elapsed += TICK_MILLIS
                val level = (recorder.amplitude() / MAX_AMPLITUDE).coerceIn(0f, 1f)
                peakLevel = maxOf(peakLevel, level)
                levels.addLast(level)
                while (levels.size > WAVEFORM_BARS) levels.removeFirst()
                set(VoiceUiState.Recording(chosen.spoken, elapsed, levels.toList()))
                if (elapsed >= maxRecordingMillis) {
                    stop()
                    break
                }
            }
        }
    }

    /** The Stop button (or the time limit). */
    fun stop() {
        if (state !is VoiceUiState.Recording) return
        ticker?.cancel()
        ticker = null
        val saved = recorder.stop()
        val file = recordingFile
        when {
            !saved || file == null || elapsed < MIN_MILLIS -> {
                discard()
                fail(VoiceFailure.TOO_SHORT)
            }
            peakLevel < SILENCE_LEVEL -> { // nothing but silence: no need to upload it
                discard()
                fail(VoiceFailure.NO_SPEECH)
            }
            else -> send(file)
        }
    }

    private fun send(file: File) {
        val chosen = languagesInUse ?: return fail(VoiceFailure.RECORDING_FAILED)
        upload = scope.launch {
            // The target is read now, at the moment of Stop, so it is always the user's current choice.
            val target = try {
                languages().target
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                chosen.target
            }
            set(VoiceUiState.Understanding(target))
            val phase = launch {
                delay(UNDERSTANDING_MILLIS) // one request does both jobs; the label moves on while it waits
                if (state is VoiceUiState.Understanding) set(VoiceUiState.Translating(target))
            }
            val result = try {
                withTimeout(requestTimeoutMillis) { api.translate(file, RECORDING_TYPE, chosen.spoken.code, target.code) }
            } catch (_: TimeoutCancellationException) {
                VoiceResult.Failure(VoiceFailure.TIMEOUT)
            } finally {
                phase.cancel()
            }
            when (result) {
                is VoiceResult.Success -> {
                    val answer = result.value
                    set(
                        VoiceUiState.Result(
                            original = Languages.fromCode(answer.sourceLanguage),
                            originalText = answer.transcript,
                            target = Languages.fromCode(answer.targetLanguage) ?: target,
                            translation = answer.translation,
                        ),
                    )
                }
                is VoiceResult.Failure -> {
                    // A network problem keeps the recording so Retry can send it again; anything else needs a new recording.
                    if (!result.failure.canRetry) removeRecording()
                    set(VoiceUiState.Failed(result.failure, canRetry = result.failure.canRetry && recordingFile != null, heard = result.heard))
                }
            }
        }
    }

    /** Send the same recording again after a network problem. */
    fun retry() {
        val file = recordingFile ?: return
        if (state is VoiceUiState.Failed) send(file)
    }

    /** The Cancel and close buttons. Ends whatever is happening and deletes the recording. */
    fun cancel() = close()

    /** Play the recording back (or stop playing). */
    fun listen() {
        val current = state as? VoiceUiState.Result ?: return
        val file = recordingFile ?: return
        if (current.playing) {
            player.stop()
            set(current.copy(playing = false))
        } else if (player.play(file) { (state as? VoiceUiState.Result)?.let { set(it.copy(playing = false)) } }) {
            set(current.copy(playing = true))
        }
    }

    /** Edit the translation with the keyboard before inserting it. */
    fun edit() {
        val current = state as? VoiceUiState.Result ?: return
        player.stop()
        edited.setLength(0)
        edited.append(current.translation)
        set(current.copy(editing = true, playing = false))
    }

    /** Text typed while editing. */
    val editTarget: TextTarget = object : TextTarget {
        override fun commitText(text: String) = update { edited.append(text) }

        override fun deleteBackward() = update {
            if (edited.isNotEmpty()) edited.setLength(edited.offsetByCodePoints(edited.length, -1))
        }

        override fun performEditorAction(actionId: Int) = Unit

        override fun sendKey(keyCode: Int) = Unit

        override fun finishComposing() = Unit

        override fun cursorCapsMode(inputType: Int): Int = 0

        private fun update(change: () -> Unit) {
            val current = state as? VoiceUiState.Result ?: return
            if (!current.editing) return
            change()
            set(current.copy(translation = edited.toString()))
        }
    }

    /** The Insert as text button: puts the translation in the composer as text. It never sends anything. */
    fun insertTranslation() {
        val current = state as? VoiceUiState.Result ?: return
        if (current.translation.isBlank()) return
        finishWith(current.translation)
        // What the user chose to put in their message is in the language they are learning to write.
        learning.record(TranslationInteraction(InteractionKind.OUTGOING_VOICE, current.translation, current.target.code))
    }

    /** After a partial result: use the words that were understood. */
    fun insertHeard() {
        val heard = (state as? VoiceUiState.Failed)?.heard?.takeIf { it.isNotBlank() } ?: return
        finishWith(heard)
    }

    private fun finishWith(text: String) {
        close()
        insert(text)
    }

    /** The Record again button. */
    fun recordAgain() {
        if (state is VoiceUiState.Recording || state is VoiceUiState.Understanding || state is VoiceUiState.Translating) return
        removeRecording()
        set(VoiceUiState.Idle)
        start()
    }

    /** A different field or app took over. Restarting the same field keeps everything. */
    fun onInputStarted(restarting: Boolean) {
        if (!restarting) close()
    }

    /** The keyboard was dismissed or the user switched apps: stop everything and delete the recording. */
    fun onInputFinished() = close()

    fun release() = close()

    private fun close() {
        ticker?.cancel()
        ticker = null
        upload?.cancel()
        upload = null
        recorder.cancel()
        player.stop()
        discard()
        set(VoiceUiState.Idle)
    }

    /** Deletes the recording and clears what the last run left. */
    private fun discard() {
        removeRecording()
        levels.clear()
        edited.setLength(0)
        elapsed = 0
        peakLevel = 0f
    }

    private fun removeRecording() {
        files.delete(recordingFile)
        recordingFile = null
    }

    private fun fail(failure: VoiceFailure) = set(VoiceUiState.Failed(failure, canRetry = false))

    private fun set(new: VoiceUiState) {
        if (new == state) return
        state = new
        onStateChanged?.invoke(new)
    }

    companion object {
        const val RECORDING_TYPE = "audio/mp4"
        const val TICK_MILLIS = 100L
        const val MIN_MILLIS = 700L
        const val UNDERSTANDING_MILLIS = 1_500L
        const val WAVEFORM_BARS = 32
        private const val MAX_AMPLITUDE = 32767f
        private const val SILENCE_LEVEL = 0.02f
    }
}
