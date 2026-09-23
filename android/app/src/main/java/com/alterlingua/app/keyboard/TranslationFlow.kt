package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** What the keyboard shows about a translation. */
sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data class Translating(val target: Language) : TranslationUiState

    /** Done. Undo is available. */
    data class Translated(val target: Language) : TranslationUiState

    /** Undo worked. */
    data object Restored : TranslationUiState

    /** Nothing was translated. [canRestore] is true only if the original text could not be put back automatically. */
    data class Failed(val failure: TranslationFailure, val canRetry: Boolean, val canRestore: Boolean = false) : TranslationUiState
}

/**
 * The outgoing-translation flow: read the text, ask the backend, replace the text, offer Undo.
 *
 * Promises (CLAUDE.md sections 18, 19, 41):
 * - The target language is asked for at the moment Translate is tapped ([targetLanguage]); French or any other
 *   language is never assumed.
 * - The user's text is replaced only after a successful, verified answer. Every failure leaves it exactly as it was.
 * - If the user switches app or field while waiting, the request is cancelled and nothing is written.
 * - If the text was edited while waiting, the result is dropped instead of overwriting the edit.
 * - It can only read and replace text. It never sends a message and never touches another app's buttons or files.
 * - The original text is held in memory only (for Undo), never saved or logged, and is dropped when the field is left.
 */
class TranslationFlow(
    private val composer: () -> Composer?,
    private val traits: () -> EditorTraits,
    private val api: TranslationApi,
    private val targetLanguage: suspend () -> Language,
    private val scope: CoroutineScope,
    /** "auto" (detect the language of the text) or the fixed source language code, read when Translate is tapped. */
    private val sourceLanguage: suspend () -> String = { "auto" },
    private val timeoutMillis: Long = 25_000,
    private val maxChars: Int = 5_000,
    private val resultMillis: Long = 10_000,
    /** Told about each successful translation so the learning engine can pick out useful units. Never delays the translation. */
    private val learning: LearningRecorder = LearningRecorder.None,
) {
    var state: TranslationUiState = TranslationUiState.Idle
        private set

    var onStateChanged: ((TranslationUiState) -> Unit)? = null

    /** [translated] is null when the field's text is unknown and the original should be restored regardless. */
    private class UndoRecord(val original: ComposerRead.Text, val translated: String?)

    private var job: Job? = null
    private var expiry: Job? = null
    private var autoTranslateJob: Job? = null
    private var undoRecord: UndoRecord? = null

    /**
     * Called on every key that changes the text while auto-translate is on (CLAUDE.md 17: settings-gated, off by
     * default): (re)starts a countdown, cancelling whatever countdown was already running. [translate] only runs
     * once the user has genuinely stopped, so a request is never sent character-by-character, and not on the brief
     * pauses between words while a message is still being composed (a real device test showed 900ms firing mid-
     * sentence: [AUTO_TRANSLATE_DEBOUNCE_MILLIS] is deliberately long enough to sit past normal thinking pauses).
     */
    fun scheduleAutoTranslate(afterMillis: Long = AUTO_TRANSLATE_DEBOUNCE_MILLIS) {
        autoTranslateJob?.cancel()
        autoTranslateJob = scope.launch {
            delay(afterMillis)
            val current = composer()?.read(maxChars)
            if (current is ComposerRead.Text && current.text.isNotBlank()) translate()
        }
    }

    /** The user tapped Translate (or Retry). */
    fun translate() {
        if (job?.isActive == true) return
        forgetResult()
        val editor = traits()
        if (editor.isKeyEventOnly) return fail(TranslationFailure.UNSUPPORTED_EDITOR)
        if (editor.isPassword) return fail(TranslationFailure.PASSWORD_FIELD) // never send passwords anywhere
        val field = composer() ?: return fail(TranslationFailure.UNSUPPORTED_EDITOR)
        val original = when (val read = field.read(maxChars)) {
            ComposerRead.Unsupported -> return fail(TranslationFailure.UNSUPPORTED_EDITOR)
            ComposerRead.TooLong -> return fail(TranslationFailure.TEXT_TOO_LONG)
            is ComposerRead.Text -> read
        }
        if (original.text.isBlank()) return fail(TranslationFailure.EMPTY_TEXT)
        job = scope.launch { run(original) }
    }

    private suspend fun run(original: ComposerRead.Text) {
        val target = try {
            targetLanguage()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return fail(TranslationFailure.TRANSLATION_FAILED)
        }
        set(TranslationUiState.Translating(target))
        val result = try {
            val source = try {
                sourceLanguage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "auto"
            }
            withTimeout(timeoutMillis) { api.translate(TranslationRequest(text = original.text, target = target.code, source = source)) }
        } catch (_: TimeoutCancellationException) {
            TranslationResult.Failure(TranslationFailure.TIMEOUT)
        }
        when (result) {
            is TranslationResult.Failure -> fail(result.failure)
            is TranslationResult.Success -> apply(original, result.translation.text, target)
        }
    }

    private fun apply(original: ComposerRead.Text, translated: String, target: Language) {
        val field = composer() ?: return set(TranslationUiState.Idle) // the field is gone: the user moved on
        val now = field.read(maxChars)
        if (now !is ComposerRead.Text || now.text != original.text) return fail(TranslationFailure.TEXT_CHANGED)

        val written = field.replaceAll(original.text.length, translated, translated.length, translated.length)
        if (written && textIs(field, translated)) {
            undoRecord = UndoRecord(original, translated)
            set(TranslationUiState.Translated(target))
            scheduleExpiry(resultMillis) { undoRecord = null }
            learning.record(TranslationInteraction(InteractionKind.OUTGOING_TEXT, translated, target.code))
            return
        }
        putOriginalBack(field, original, translated)
    }

    /** The translation could not be applied cleanly: put the user's text back, and if that fails too, keep it for Restore. */
    private fun putOriginalBack(field: Composer, original: ComposerRead.Text, translated: String) {
        val after = field.read(maxChars)
        if (after is ComposerRead.Text) {
            if (after.text == original.text) return fail(TranslationFailure.APPLY_FAILED)
            val restored = field.replaceAll(after.text.length, original.text, original.selectionStart, original.selectionEnd)
            if (restored && textIs(field, original.text)) return fail(TranslationFailure.APPLY_FAILED)
        }
        undoRecord = UndoRecord(original, translated = null)
        set(TranslationUiState.Failed(TranslationFailure.APPLY_FAILED, canRetry = false, canRestore = true))
    }

    /** The user tapped Undo (or Restore): put the exact original text back, if the text is still what we left. */
    fun undo() {
        val record = undoRecord ?: return
        val field = composer()
        val now = field?.read(maxChars)
        if (field == null || now !is ComposerRead.Text) return failUndo()
        if (record.translated != null && now.text != record.translated) return failUndo()
        val original = record.original
        val restored = field.replaceAll(now.text.length, original.text, original.selectionStart, original.selectionEnd)
        if (restored && textIs(field, original.text)) {
            forgetResult()
            set(TranslationUiState.Restored)
            scheduleExpiry(RESTORED_MILLIS) {}
        } else {
            undoRecord = UndoRecord(original, translated = null)
            set(TranslationUiState.Failed(TranslationFailure.APPLY_FAILED, canRetry = false, canRestore = true))
        }
    }

    private fun failUndo() {
        undoRecord = null
        fail(TranslationFailure.UNDO_UNAVAILABLE)
    }

    /** A different text field or app took over: stop, write nothing, and forget the original. */
    fun onInputStarted(restarting: Boolean) {
        if (!restarting) reset()
    }

    fun onInputFinished() = reset()

    /** The user pressed a key that changes the text: the result banner and Undo no longer apply. */
    fun onUserEdited() {
        if (state is TranslationUiState.Translating) return
        if (state != TranslationUiState.Idle) dismiss()
    }

    /** The user cancelled while waiting. */
    fun cancel() {
        if (state is TranslationUiState.Translating) reset()
    }

    fun dismiss() {
        forgetResult()
        set(TranslationUiState.Idle)
    }

    fun release() = reset()

    private fun reset() {
        job?.cancel()
        job = null
        autoTranslateJob?.cancel()
        autoTranslateJob = null
        forgetResult()
        set(TranslationUiState.Idle)
    }

    private fun forgetResult() {
        expiry?.cancel()
        expiry = null
        undoRecord = null
    }

    private fun scheduleExpiry(afterMillis: Long, cleanup: () -> Unit) {
        expiry?.cancel()
        expiry = scope.launch {
            delay(afterMillis)
            cleanup()
            set(TranslationUiState.Idle)
        }
    }

    private fun textIs(field: Composer, expected: String): Boolean = (field.read(maxChars) as? ComposerRead.Text)?.text == expected

    private fun fail(failure: TranslationFailure) = set(TranslationUiState.Failed(failure, failure.canRetry))

    private fun set(new: TranslationUiState) {
        if (new == state) return
        state = new
        onStateChanged?.invoke(new)
    }

    private companion object {
        const val RESTORED_MILLIS = 3_000L
        const val AUTO_TRANSLATE_DEBOUNCE_MILLIS = 2_500L
    }
}
