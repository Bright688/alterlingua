package com.alterlingua.app.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what the keyboard did to the text field. */
private class FakeTextTarget : TextTarget {
    val typed = StringBuilder()
    val calls = mutableListOf<String>()
    var capsMode = 0
    var capsQueries = 0

    override fun commitText(text: String) {
        typed.append(text)
        calls += "commit"
    }

    override fun deleteBackward() {
        if (typed.isNotEmpty()) typed.deleteCharAt(typed.length - 1)
        calls += "delete"
    }

    override fun performEditorAction(actionId: Int) {
        calls += "action:$actionId"
    }

    override fun sendKey(keyCode: Int) {
        calls += "key:$keyCode"
    }

    override fun finishComposing() {
        calls += "finishComposing"
    }

    override fun cursorCapsMode(inputType: Int): Int {
        capsQueries++
        return capsMode
    }
}

class KeyboardControllerTest {

    private val target = FakeTextTarget()
    private var switched = 0
    private var now = 1_000L
    private val controller = KeyboardController(target, { switched++ }, { now })

    private val plainText = EditorTraits(InputType.TYPE_CLASS_TEXT)
    private val sentenceText = EditorTraits(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

    private fun key(text: String) = KeySpec.Character(text)
    private fun fn(action: KeyAction) = KeySpec.Function(action, 1f)
    private fun type(text: String) = text.forEach { controller.onKey(key(it.toString())) }

    // ---- typing ----

    @Test
    fun lettersAreTypedIntoTheField() {
        controller.start(plainText, restarting = false)
        type("hello")
        assertEquals("hello", target.typed.toString())
    }

    @Test
    fun spaceAndPunctuationAreTyped() {
        controller.start(plainText, restarting = false)
        type("hi")
        controller.onKey(key(","))
        controller.onKey(fn(KeyAction.SPACE))
        type("you")
        controller.onKey(key("?"))
        assertEquals("hi, you?", target.typed.toString())
    }

    @Test
    fun unicodeCharactersAreTypedWhole() {
        controller.start(plainText, restarting = false)
        controller.onKey(key("€"))
        controller.onKey(key("£"))
        assertEquals("€£", target.typed.toString())
    }

    @Test
    fun spacersDoNothing() {
        controller.start(plainText, restarting = false)
        controller.onKey(KeySpec.Spacer(0.5f))
        assertEquals("", target.typed.toString())
    }

    // ---- shift ----

    @Test
    fun shiftOnce_capitalisesOnlyTheNextLetter() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        assertEquals(ShiftState.ONCE, controller.state.shift)
        type("ab")
        assertEquals("Ab", target.typed.toString())
        assertEquals(ShiftState.OFF, controller.state.shift)
    }

    @Test
    fun tappingShiftTwiceQuickly_isCapsLock_andThirdTapTurnsItOff() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        now += 200
        controller.onKey(fn(KeyAction.SHIFT))
        assertEquals(ShiftState.CAPS_LOCK, controller.state.shift)
        type("abc")
        assertEquals("ABC", target.typed.toString())
        assertEquals(ShiftState.CAPS_LOCK, controller.state.shift)
        now += 2_000
        controller.onKey(fn(KeyAction.SHIFT))
        assertEquals(ShiftState.OFF, controller.state.shift)
        type("d")
        assertEquals("ABCd", target.typed.toString())
    }

    @Test
    fun aSlowSecondShiftTap_turnsShiftOff() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        now += 1_000
        controller.onKey(fn(KeyAction.SHIFT))
        assertEquals(ShiftState.OFF, controller.state.shift)
    }

    @Test
    fun shiftDoesNotChangeSymbols() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        controller.onKey(key("@"))
        assertEquals("@", target.typed.toString())
    }

    // ---- automatic capitals ----

    @Test
    fun atTheStartOfASentence_theNextLetterIsCapital() {
        target.capsMode = 1
        controller.start(sentenceText, restarting = false)
        assertEquals(ShiftState.ONCE, controller.state.shift)
        target.capsMode = 0
        type("h")
        assertEquals("H", target.typed.toString())
        assertEquals(ShiftState.OFF, controller.state.shift)
    }

    @Test
    fun afterASentenceEnds_shiftComesBackOn() {
        controller.start(sentenceText, restarting = false)
        target.capsMode = 1 // the field now reports "sentence start" after ". "
        controller.onKey(fn(KeyAction.SPACE))
        assertEquals(ShiftState.ONCE, controller.state.shift)
    }

    @Test
    fun fieldsWithoutCapitalsAreNotAskedAndStayLowercase() {
        target.capsMode = 1
        controller.start(plainText, restarting = false)
        type("abc")
        assertEquals("abc", target.typed.toString())
        assertEquals(0, target.capsQueries)
    }

    @Test
    fun capsLockIsNotCancelledByAutoCaps() {
        controller.start(sentenceText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        now += 100
        controller.onKey(fn(KeyAction.SHIFT))
        target.capsMode = 0
        type("ab")
        assertEquals(ShiftState.CAPS_LOCK, controller.state.shift)
        assertEquals("AB", target.typed.toString())
    }

    // ---- pages ----

    @Test
    fun pagesSwitchBetweenLettersAndSymbols() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        assertEquals(KeyboardPage.SYMBOLS_1, controller.state.page)
        controller.onKey(fn(KeyAction.SHOW_MORE_SYMBOLS))
        assertEquals(KeyboardPage.SYMBOLS_2, controller.state.page)
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        assertEquals(KeyboardPage.SYMBOLS_1, controller.state.page)
        controller.onKey(fn(KeyAction.SHOW_LETTERS))
        assertEquals(KeyboardPage.LETTERS, controller.state.page)
    }

    @Test
    fun numberAndPhoneFieldsStartOnTheSymbolsPage() {
        for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)) {
            controller.start(EditorTraits(type), restarting = false)
            assertEquals(KeyboardPage.SYMBOLS_1, controller.state.page)
        }
        controller.start(plainText, restarting = false)
        assertEquals(KeyboardPage.LETTERS, controller.state.page)
    }

    @Test
    fun restartingTheSameFieldKeepsThePage() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        controller.start(plainText, restarting = true)
        assertEquals(KeyboardPage.SYMBOLS_1, controller.state.page)
    }

    @Test
    fun aNewFieldStartsFresh_andEndsLeftoverComposingText() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        controller.start(plainText, restarting = false)
        assertEquals(KeyboardState(), controller.state)
        assertEquals(2, target.calls.count { it == "finishComposing" })
        controller.start(plainText, restarting = true)
        assertEquals(2, target.calls.count { it == "finishComposing" })
    }

    // ---- backspace ----

    @Test
    fun backspaceDeletesBeforeTheCursor() {
        controller.start(plainText, restarting = false)
        type("abc")
        controller.onKey(fn(KeyAction.BACKSPACE))
        assertEquals("ab", target.typed.toString())
    }

    @Test
    fun backspaceInAKeyEventOnlyField_sendsTheDeleteKey() {
        controller.start(EditorTraits(InputType.TYPE_NULL), restarting = false)
        controller.onKey(fn(KeyAction.BACKSPACE))
        assertEquals(listOf("finishComposing", "key:${KeyEvent.KEYCODE_DEL}"), target.calls)
    }

    // ---- enter ----

    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    private fun pressEnter(inputType: Int, imeOptions: Int) {
        controller.start(EditorTraits(inputType, imeOptions), restarting = false)
        target.calls.clear()
        controller.onKey(fn(KeyAction.ENTER))
    }

    @Test
    fun enterInAMultiLineField_addsALineBreak() {
        pressEnter(multiLine, EditorInfo.IME_ACTION_UNSPECIFIED)
        assertEquals("\n", target.typed.toString())
    }

    @Test
    fun enterNeverSendsAMessage_evenWhenTheFieldsActionIsSend() {
        pressEnter(multiLine, EditorInfo.IME_ACTION_SEND)
        assertEquals("\n", target.typed.toString()) // WhatsApp with "Enter is send" turned on
        assertFalse(target.calls.any { it.startsWith("action:") })

        target.typed.clear()
        pressEnter(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND) // single line
        assertEquals("", target.typed.toString())
        assertFalse(target.calls.any { it.startsWith("action:") })
    }

    @Test
    fun enterRunsTheFieldsOwnAction_forGoSearchNextDone() {
        for (action in listOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE)) {
            pressEnter(InputType.TYPE_CLASS_TEXT, action)
            assertTrue("action $action", "action:$action" in target.calls)
        }
    }

    @Test
    fun noEnterActionFlag_meansALineBreak() {
        pressEnter(multiLine, EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        assertEquals("\n", target.typed.toString())
    }

    @Test
    fun enterInAKeyEventOnlyField_sendsTheEnterKey() {
        pressEnter(InputType.TYPE_NULL, 0)
        assertEquals(listOf("key:${KeyEvent.KEYCODE_ENTER}"), target.calls)
    }

    // ---- switching ----

    @Test
    fun theGlobeKeyAsksToSwitchKeyboard_andTypesNothing() {
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SWITCH_KEYBOARD))
        assertEquals(1, switched)
        assertEquals("", target.typed.toString())
    }

    @Test
    fun stateChangesAreReported() {
        val seen = mutableListOf<KeyboardState>()
        controller.onStateChanged = { seen += it }
        controller.start(plainText, restarting = false)
        controller.onKey(fn(KeyAction.SHIFT))
        controller.onKey(fn(KeyAction.SHOW_SYMBOLS))
        assertEquals(
            listOf(
                KeyboardState(shift = ShiftState.ONCE),
                KeyboardState(page = KeyboardPage.SYMBOLS_1, shift = ShiftState.ONCE),
            ),
            seen,
        )
    }
}
