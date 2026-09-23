package com.alterlingua.app.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the app's text field asked for (the parts of `EditorInfo` the keyboard needs). Holds no text. */
data class EditorTraits(val inputType: Int = 0, val imeOptions: Int = 0) {
    private val inputClass get() = inputType and InputType.TYPE_MASK_CLASS

    /** A field with no rich text input; it only understands key events. */
    val isKeyEventOnly get() = inputType == InputType.TYPE_NULL

    /** Numbers, phone numbers and dates start on the symbols page, where the digits are. */
    val prefersNumbers
        get() = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME

    /** Password fields: their text must never be sent anywhere. */
    val isPassword
        get() = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> inputType and InputType.TYPE_MASK_VARIATION in
                setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            InputType.TYPE_CLASS_NUMBER -> inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }

    val isMultiLine get() = inputClass == InputType.TYPE_CLASS_TEXT && inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    /** The field wants automatic capitals (sentence starts, words or all characters). */
    val wantsAutoCaps
        get() = inputClass == InputType.TYPE_CLASS_TEXT &&
            inputType and (
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                ) != 0

    val initialPage get() = if (prefersNumbers) KeyboardPage.SYMBOLS_1 else KeyboardPage.LETTERS
}

/** What the Enter key does in a given field. */
sealed interface EnterBehavior {
    data object Newline : EnterBehavior
    data object KeyEvent : EnterBehavior

    /** Runs the field's own action (Go, Search, Next, Done...). */
    data class Action(val id: Int) : EnterBehavior

    /** Does nothing. */
    data object Nothing : EnterBehavior
}

/**
 * AlterLingua never sends a message for the user (CLAUDE.md sections 3 and 18), so the Enter key never runs a
 * "Send" action: it adds a line break in multi-line fields (like the WhatsApp message box) and does nothing in
 * single-line ones. The user presses the app's own Send button.
 */
fun enterBehavior(traits: EditorTraits): EnterBehavior {
    if (traits.isKeyEventOnly) return EnterBehavior.KeyEvent
    val action = traits.imeOptions and EditorInfo.IME_MASK_ACTION
    val noEnterAction = traits.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
    return when {
        noEnterAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED ->
            EnterBehavior.Newline
        action == EditorInfo.IME_ACTION_SEND -> if (traits.isMultiLine) EnterBehavior.Newline else EnterBehavior.Nothing
        else -> EnterBehavior.Action(action)
    }
}
