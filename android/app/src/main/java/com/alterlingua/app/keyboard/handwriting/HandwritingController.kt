package com.alterlingua.app.keyboard.handwriting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where handwriting is: getting ready, ready to write, or unable to get the recogniser's language model. */
enum class HandwritingStatus { PREPARING, DOWNLOADING, READY, FAILED }

data class HandwritingState(
    val status: HandwritingStatus = HandwritingStatus.PREPARING,
    val candidates: List<String> = emptyList(),
    /** True while there are strokes on the pad that are not yet turned into text. */
    val hasInk: Boolean = false,
)

/**
 * The logic of the handwriting pad. Strokes are collected; a moment after the pen is lifted (and no new stroke has begun) the
 * strokes so far are recognised and the candidates offered. Choosing one gives the text to put in the field and clears the pad.
 * Nothing is stored: the strokes live only until they are turned into text or cleared.
 */
class HandwritingController(
    private val recognizer: InkRecognizer,
    private val scope: CoroutineScope,
    private val pauseMillis: Long = 600,
) {
    var state: HandwritingState = HandwritingState()
        private set

    /** Called whenever [state] changes, so the pad can update. */
    var onChanged: ((HandwritingState) -> Unit)? = null

    private val strokes = mutableListOf<InkStroke>()
    private var recognizing: Job? = null
    private var preparing: Job? = null
    private var language: String? = null

    /** Gets the recogniser ready for [languageCode]. Safe to call again (for example on retry or when the language changes). */
    fun open(languageCode: String) {
        language = languageCode
        clearInk()
        preparing?.cancel()
        update(HandwritingState(HandwritingStatus.PREPARING))
        preparing = scope.launch {
            val ok = recognizer.prepare(languageCode) { update(state.copy(status = HandwritingStatus.DOWNLOADING)) }
            update(state.copy(status = if (ok) HandwritingStatus.READY else HandwritingStatus.FAILED))
        }
    }

    fun retry() = language?.let { open(it) }

    /** A stroke was started: a pending recognition is no longer wanted (the writer is not finished). */
    fun strokeStarted() {
        recognizing?.cancel()
    }

    /** A stroke was finished. Recognition follows after a short pause, unless another stroke begins. */
    fun strokeFinished(stroke: InkStroke) {
        if (stroke.isEmpty() || state.status != HandwritingStatus.READY) return
        strokes += stroke
        update(state.copy(hasInk = true))
        recognizing?.cancel()
        recognizing = scope.launch {
            delay(pauseMillis)
            val candidates = recognizer.recognize(strokes.toList())
            update(state.copy(candidates = candidates))
        }
    }

    /** Returns the text of candidate [index] and clears the pad, or null if there is no such candidate. */
    fun choose(index: Int): String? {
        val text = state.candidates.getOrNull(index) ?: return null
        clearInk()
        update(state.copy(candidates = emptyList(), hasInk = false))
        return text
    }

    /** Clears whatever is drawn. Returns true if there was something (so a backspace on the pad clears the drawing before it deletes text). */
    fun clear(): Boolean {
        val had = strokes.isNotEmpty() || state.candidates.isNotEmpty()
        clearInk()
        if (had) update(state.copy(candidates = emptyList(), hasInk = false))
        return had
    }

    fun close() {
        preparing?.cancel()
        clearInk()
        recognizer.close()
        state = HandwritingState()
    }

    private fun clearInk() {
        recognizing?.cancel()
        strokes.clear()
    }

    private fun update(next: HandwritingState) {
        state = next
        onChanged?.invoke(next)
    }
}
