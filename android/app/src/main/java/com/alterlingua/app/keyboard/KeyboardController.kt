package com.alterlingua.app.keyboard

import android.view.KeyEvent

/**
 * The keyboard's brain: turns key presses into text in the app's field and keeps track of the page and shift.
 * It has no Android views, so it is unit tested directly.
 */
class KeyboardController(
    private val target: TextTarget,
    private val switchKeyboard: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var state = KeyboardState()
        private set

    /** Called whenever [state] changes so the view can redraw. */
    var onStateChanged: ((KeyboardState) -> Unit)? = null

    private var fieldTraits = EditorTraits()

    /** Used instead of the field's own traits while text is being edited somewhere else (the voice panel). */
    var traitsOverride: EditorTraits? = null

    private val traits: EditorTraits get() = traitsOverride ?: fieldTraits

    /** What the current text field asked for. */
    val editorTraits: EditorTraits get() = fieldTraits
    private var lastShiftTapAt = 0L

    /** A text field was focused (or restarted). */
    fun start(newTraits: EditorTraits, restarting: Boolean) {
        fieldTraits = newTraits
        if (!restarting) {
            target.finishComposing()
            lastShiftTapAt = 0L
            update(KeyboardState(page = traits.initialPage))
        }
        refreshAutoCaps()
    }

    fun finish() {
        fieldTraits = EditorTraits()
    }

    /** Puts [text] into the field (for example a voice translation) as if typed, then updates automatic capitals. */
    fun insertText(text: String) {
        target.commitText(text)
        refreshAutoCaps()
    }

    fun onKey(key: KeySpec) {
        when (key) {
            is KeySpec.Spacer -> Unit
            is KeySpec.Character -> typeCharacter(key)
            is KeySpec.Function -> runFunction(key.action)
        }
    }

    private fun typeCharacter(key: KeySpec.Character) {
        target.commitText(key.typed(state))
        if (state.shift == ShiftState.ONCE) update(state.copy(shift = ShiftState.OFF))
        refreshAutoCaps()
    }

    private fun runFunction(action: KeyAction) {
        when (action) {
            KeyAction.SPACE -> {
                target.commitText(" ")
                refreshAutoCaps()
            }
            KeyAction.BACKSPACE -> {
                if (traits.isKeyEventOnly) target.sendKey(KeyEvent.KEYCODE_DEL) else target.deleteBackward()
                refreshAutoCaps()
            }
            KeyAction.ENTER -> {
                when (val behavior = enterBehavior(traits)) {
                    EnterBehavior.Newline -> target.commitText("\n")
                    EnterBehavior.KeyEvent -> target.sendKey(KeyEvent.KEYCODE_ENTER)
                    is EnterBehavior.Action -> target.performEditorAction(behavior.id)
                    EnterBehavior.Nothing -> Unit
                }
                refreshAutoCaps()
            }
            KeyAction.SHIFT -> tapShift()
            KeyAction.SHOW_LETTERS -> {
                update(state.copy(page = KeyboardPage.LETTERS))
                refreshAutoCaps()
            }
            KeyAction.SHOW_SYMBOLS -> update(state.copy(page = KeyboardPage.SYMBOLS_1))
            KeyAction.SHOW_MORE_SYMBOLS -> update(state.copy(page = KeyboardPage.SYMBOLS_2))
            KeyAction.SWITCH_KEYBOARD -> switchKeyboard()
            KeyAction.HANDWRITING -> Unit // opened by the keyboard service, which owns the drawing pad
        }
    }

    /** Tap: next letter capital. Tap again quickly: caps lock. Tap while caps lock is on: off. */
    private fun tapShift() {
        val now = clock()
        val next = when (state.shift) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (now - lastShiftTapAt <= DOUBLE_TAP_MILLIS) ShiftState.CAPS_LOCK else ShiftState.OFF
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        lastShiftTapAt = now
        update(state.copy(shift = next))
    }

    /** At the start of a sentence (where the field asks for it), the next letter is a capital. */
    private fun refreshAutoCaps() {
        if (state.shift == ShiftState.CAPS_LOCK || state.page != KeyboardPage.LETTERS || !traits.wantsAutoCaps) return
        val capital = target.cursorCapsMode(traits.inputType) != 0
        update(state.copy(shift = if (capital) ShiftState.ONCE else ShiftState.OFF))
    }

    private fun update(new: KeyboardState) {
        if (new == state) return
        state = new
        onStateChanged?.invoke(new)
    }

    private companion object {
        const val DOUBLE_TAP_MILLIS = 350L
    }
}
