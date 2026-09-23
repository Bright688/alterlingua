package com.alterlingua.app.keyboard.engine

import com.alterlingua.app.keyboard.TextTarget

/**
 * Connects a [CandidateEngine] to the text field: the typed pinyin or kana appears underlined in the field while the user types,
 * the candidates are shown on the keyboard, and choosing one replaces the underlined text with the chosen word.
 * Space and Enter are handled here only while something is being composed; otherwise they are ordinary keys.
 */
class CompositionController(
    private val engine: CandidateEngine,
    private val target: TextTarget,
) {
    var composition: Composition = Composition()
        private set

    /** Called whenever [composition] changes, so the candidate bar can update. */
    var onChanged: ((Composition) -> Unit)? = null

    val isComposing: Boolean get() = !composition.isEmpty

    /** Types [input] into the composition. */
    fun type(input: String) = show(engine.append(input))

    /** Returns true when it removed composing input; false when nothing was being composed (the field's own backspace applies). */
    fun backspace(): Boolean {
        if (!isComposing) return false
        show(engine.deleteLast())
        return true
    }

    fun choose(index: Int) {
        if (!isComposing || index !in composition.candidates.indices) return
        val choice = engine.choose(index)
        target.commitText(choice.committed)
        show(choice.remaining)
    }

    /** Space while composing picks the first candidate (or keeps the typed text if there is none). Returns false when not composing. */
    fun space(): Boolean {
        if (!isComposing) return false
        if (composition.candidates.isEmpty()) commitTyped() else choose(0)
        return true
    }

    /** Enter while composing keeps the typed letters as they are and does not send anything. Returns false when not composing. */
    fun enter(): Boolean {
        if (!isComposing) return false
        commitTyped()
        return true
    }

    /** Keeps whatever is typed as it is and ends the composition (for example before symbols or translation). Does nothing if not composing. */
    fun flush() {
        if (isComposing) commitTyped()
    }

    /** Drops the composition without touching the field (for example when the user moves to another field). */
    fun cancel() {
        engine.reset()
        composition = Composition()
        onChanged?.invoke(composition)
    }

    private fun commitTyped() {
        target.commitText(composition.typed)
        engine.reset()
        show(Composition())
    }

    private fun show(next: Composition) {
        composition = next
        target.setComposingText(next.preedit)
        onChanged?.invoke(next)
    }
}
