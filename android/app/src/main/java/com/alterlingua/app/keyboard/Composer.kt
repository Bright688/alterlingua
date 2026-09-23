package com.alterlingua.app.keyboard

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** What was found in the text field. */
sealed interface ComposerRead {
    /** The whole text, with where the cursor or selection is (UTF-16 positions, as Android uses). */
    data class Text(val text: String, val selectionStart: Int, val selectionEnd: Int) : ComposerRead

    /** There is more text than can be translated in one go. */
    data object TooLong : ComposerRead

    /** This field does not let a keyboard read its text. */
    data object Unsupported : ComposerRead
}

/**
 * Reading and replacing the whole text of the field. It only ever reads and edits text through Android's supported
 * `InputConnection`; it has no way to press a button, so it cannot send a message.
 */
interface Composer {
    fun read(maxChars: Int): ComposerRead

    /** Replaces all [currentLength] characters with [newText] and puts the selection at the given positions. */
    fun replaceAll(currentLength: Int, newText: String, selectionStart: Int, selectionEnd: Int): Boolean
}

class InputConnectionComposer(private val connection: InputConnection) : Composer {

    override fun read(maxChars: Int): ComposerRead {
        val request = ExtractedTextRequest().apply {
            token = 0
            hintMaxChars = maxChars + 1
            hintMaxLines = Int.MAX_VALUE
        }
        val extracted = connection.getExtractedText(request, 0)
        val whole = extracted?.text
        if (extracted != null && whole != null) {
            if (extracted.startOffset != 0 || whole.length > maxChars) return ComposerRead.TooLong
            val a = extracted.selectionStart.coerceIn(0, whole.length)
            val b = extracted.selectionEnd.coerceIn(0, whole.length)
            return ComposerRead.Text(whole.toString(), minOf(a, b), maxOf(a, b))
        }

        // Some fields cannot give their whole text at once; then read around the cursor instead.
        val before = connection.getTextBeforeCursor(maxChars + 1, 0)
        val after = connection.getTextAfterCursor(maxChars + 1, 0)
        if (before == null || after == null) return ComposerRead.Unsupported
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        if (before.length + selected.length + after.length > maxChars) return ComposerRead.TooLong
        return ComposerRead.Text("$before$selected$after", before.length, before.length + selected.length)
    }

    override fun replaceAll(currentLength: Int, newText: String, selectionStart: Int, selectionEnd: Int): Boolean {
        connection.beginBatchEdit()
        try {
            connection.finishComposingText()
            val selectedAll = connection.setSelection(0, currentLength) || connection.performContextMenuAction(android.R.id.selectAll)
            if (!selectedAll) return false
            if (!connection.commitText(newText, 1)) return false
            connection.setSelection(selectionStart.coerceIn(0, newText.length), selectionEnd.coerceIn(0, newText.length))
            return true
        } finally {
            connection.endBatchEdit()
        }
    }
}
