package com.alterlingua.app.keyboard

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * A small pretend text editor behind Android's real `InputConnection` interface, to check that [InputConnectionComposer]
 * uses the supported calls correctly, including when a field cannot give its whole text at once.
 */
private class FakeEditor(var text: String, var selStart: Int = text.length, var selEnd: Int = text.length) {
    var supportsExtracted = true
    var supportsSurrounding = true
    var rejectSetSelection = false
    var rejectCommit = false
    var batchDepth = 0
    val calls = mutableListOf<String>()

    private fun select(a: Int, b: Int) {
        selStart = minOf(a, b).coerceIn(0, text.length)
        selEnd = maxOf(a, b).coerceIn(0, text.length)
    }

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        calls += method.name
        when (method.name) {
            "beginBatchEdit" -> { batchDepth++; true }
            "endBatchEdit" -> { batchDepth--; true }
            "finishComposingText" -> true
            "getExtractedText" -> if (supportsExtracted) ExtractedText().also { it.text = text; it.selectionStart = selStart; it.selectionEnd = selEnd } else null
            "getTextBeforeCursor" -> if (supportsSurrounding) text.substring((selStart - args[0] as Int).coerceAtLeast(0), selStart) else null
            "getTextAfterCursor" -> if (supportsSurrounding) text.substring(selEnd, (selEnd + args[0] as Int).coerceAtMost(text.length)) else null
            "getSelectedText" -> if (selStart == selEnd) null else text.substring(selStart, selEnd)
            "setSelection" -> if (rejectSetSelection) false else { select(args[0] as Int, args[1] as Int); true }
            "performContextMenuAction" -> { select(0, text.length); true }
            "commitText" -> if (rejectCommit) false else {
                val inserted = args[0].toString()
                text = text.substring(0, selStart) + inserted + text.substring(selEnd)
                selStart = selStart + inserted.length
                selEnd = selStart
                true
            }
            "toString" -> "FakeEditor"
            "hashCode" -> 1
            "equals" -> false
            else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else if (method.returnType == Int::class.javaPrimitiveType) 0 else null
        }
    } as InputConnection
}

class InputConnectionComposerTest {

    private fun composer(editor: FakeEditor) = InputConnectionComposer(editor.connection)

    @Test
    fun readsTheWholeTextAndTheSelection() {
        val editor = FakeEditor("Are you coming tomorrow?", 4, 7)
        assertEquals(ComposerRead.Text("Are you coming tomorrow?", 4, 7), composer(editor).read(5_000))
    }

    @Test
    fun readsUnicodeAndEmojiWithoutBreakingThem() {
        val text = "日本語 🙂 café\nline two"
        assertEquals(ComposerRead.Text(text, text.length, text.length), composer(FakeEditor(text)).read(5_000))
    }

    @Test
    fun aFieldThatCannotGiveItsWholeText_isReadAroundTheCursor() {
        val editor = FakeEditor("Hello world, how are you", 6, 11).apply { supportsExtracted = false }
        assertEquals(ComposerRead.Text("Hello world, how are you", 6, 11), composer(editor).read(5_000))

        editor.selStart = 12
        editor.selEnd = 12
        assertEquals(ComposerRead.Text("Hello world, how are you", 12, 12), composer(editor).read(5_000))
    }

    @Test
    fun aFieldThatCanBeReadNeitherWay_isUnsupported() {
        val editor = FakeEditor("secret").apply { supportsExtracted = false; supportsSurrounding = false }
        assertEquals(ComposerRead.Unsupported, composer(editor).read(5_000))
    }

    @Test
    fun tooMuchText_isReportedNotCut() {
        assertEquals(ComposerRead.TooLong, composer(FakeEditor("a".repeat(101))).read(100))
        assertEquals(ComposerRead.TooLong, composer(FakeEditor("a".repeat(101)).apply { supportsExtracted = false }).read(100))
        assertTrue(composer(FakeEditor("a".repeat(100))).read(100) is ComposerRead.Text)
    }

    @Test
    fun replacingSelectsEverything_thenTypesTheNewTextAsOneBatch() {
        val editor = FakeEditor("Are you coming tomorrow?", 4, 7)
        assertTrue(composer(editor).replaceAll(24, "¿Vienes mañana?", 15, 15))
        assertEquals("¿Vienes mañana?", editor.text)
        assertEquals(15, editor.selStart)
        assertEquals(0, editor.batchDepth) // every begin has its end
        assertTrue("beginBatchEdit" in editor.calls && "commitText" in editor.calls)
    }

    @Test
    fun replacingWorksForTextInTheMiddleOfTheCursorAndForSelections() {
        val editor = FakeEditor("one two three", 4, 4)
        composer(editor).replaceAll(13, "uno dos tres", 0, 3)
        assertEquals("uno dos tres", editor.text)
        assertEquals(0, editor.selStart)
        assertEquals(3, editor.selEnd)
    }

    @Test
    fun ifSettingTheSelectionIsRefused_selectAllIsUsedInstead() {
        val editor = FakeEditor("hello").apply { rejectSetSelection = true }
        assertTrue(composer(editor).replaceAll(5, "hola", 4, 4))
        assertEquals("hola", editor.text)
        assertTrue("performContextMenuAction" in editor.calls)
    }

    @Test
    fun ifTheFieldRefusesTheText_replaceReportsFailure_andStillEndsTheBatch() {
        val editor = FakeEditor("hello").apply { rejectCommit = true }
        assertFalse(composer(editor).replaceAll(5, "hola", 4, 4))
        assertEquals(0, editor.batchDepth)
        assertEquals("hello", editor.text)
    }

    @Test
    fun theComposerNeverCallsAnythingThatCouldSendOrPress() {
        val editor = FakeEditor("hello")
        val composer = composer(editor)
        composer.read(5_000)
        composer.replaceAll(5, "hola", 4, 4)
        val allowed = setOf(
            "getExtractedText", "getTextBeforeCursor", "getTextAfterCursor", "getSelectedText",
            "beginBatchEdit", "endBatchEdit", "finishComposingText", "setSelection", "performContextMenuAction", "commitText",
        )
        assertTrue("unexpected calls: ${editor.calls - allowed}", editor.calls.all { it in allowed })
        assertFalse("performEditorAction" in editor.calls || "sendKeyEvent" in editor.calls)
    }
}
