package com.alterlingua.app.keyboard

import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * How the keyboard puts text into the app's text field. Kept as an interface so the typing logic can be tested
 * without a phone. Nothing here stores or logs what is typed.
 */
interface TextTarget {
    fun commitText(text: String)

    /**
     * Shows [text] underlined as unfinished input (pinyin or kana being converted). An empty [text] removes it. Committing
     * text afterwards replaces it. Targets that cannot do this keep the default, which does nothing.
     */
    fun setComposingText(text: String) = Unit

    /** Deletes the selection, or the character before the cursor. */
    fun deleteBackward()

    fun performEditorAction(actionId: Int)

    /** For fields that only understand key events. */
    fun sendKey(keyCode: Int)

    /** Ends any underlined "composing" text another keyboard left, so typing does not replace it. */
    fun finishComposing()

    /** Non-zero when the cursor is somewhere that starts with a capital (for example a new sentence). */
    fun cursorCapsMode(inputType: Int): Int
}

/** The real thing: talks to the app through Android's `InputConnection`. */
class InputConnectionTextTarget(private val connection: () -> InputConnection?) : TextTarget {

    override fun commitText(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun setComposingText(text: String) {
        val ic = connection() ?: return
        if (text.isEmpty()) ic.commitText("", 1) else ic.setComposingText(text, 1)
    }

    override fun deleteBackward() {
        val ic = connection() ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1) // replaces the selection with nothing
        } else if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
            ic.deleteSurroundingText(1, 0)
        }
    }

    override fun performEditorAction(actionId: Int) {
        connection()?.performEditorAction(actionId)
    }

    override fun sendKey(keyCode: Int) {
        val ic = connection() ?: return
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
    }

    override fun cursorCapsMode(inputType: Int): Int = connection()?.getCursorCapsMode(inputType) ?: 0
}

/**
 * Sends typing to the app's field normally, or to a different target while one is set (for example the text being
 * edited in the voice panel), so the same keys work in both places.
 */
class SwitchableTextTarget(private val primary: TextTarget) : TextTarget {
    /** While set, all typing goes here instead of the app's field. */
    var override: TextTarget? = null

    private val current: TextTarget get() = override ?: primary

    override fun commitText(text: String) = current.commitText(text)

    override fun setComposingText(text: String) = current.setComposingText(text)

    override fun deleteBackward() = current.deleteBackward()

    override fun performEditorAction(actionId: Int) = current.performEditorAction(actionId)

    override fun sendKey(keyCode: Int) = current.sendKey(keyCode)

    override fun finishComposing() = current.finishComposing()

    override fun cursorCapsMode(inputType: Int): Int = current.cursorCapsMode(inputType)
}
