package com.alterlingua.app.keyboard

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on a device or emulator: the composer against a REAL Android text field (an EditText) rather than a pretend
 * one, and the whole translation flow with a stand-in translator. Needs no other app and no network.
 */
class InputConnectionComposerDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun editText(text: String): EditText {
        lateinit var view: EditText
        onMain {
            view = EditText(instrumentation.targetContext)
            view.setText(text)
            view.setSelection(text.length)
        }
        return view
    }

    private fun composerFor(view: EditText): Composer {
        lateinit var composer: Composer
        onMain {
            val connection = view.onCreateInputConnection(EditorInfo())
            assertNotNull("the field gives the keyboard no InputConnection", connection)
            composer = InputConnectionComposer(connection!!)
        }
        return composer
    }

    @Test
    fun readsAndReplacesTheWholeText_andPutsTheCursorAtTheEnd() {
        val view = editText("Are you coming tomorrow?")
        val composer = composerFor(view)
        val read = composer.read(5_000) as ComposerRead.Text
        assertEquals("Are you coming tomorrow?", read.text)

        assertTrue(composer.replaceAll(read.text.length, "¿Vienes mañana?", 15, 15))
        assertEquals("¿Vienes mañana?", view.text.toString())
        assertEquals(15, view.selectionStart)
    }

    @Test
    fun undoStyleRestoreBringsBackTheExactOriginal_withUnicode() {
        val original = "  日本語 🙂 café\nsecond line  "
        val view = editText(original)
        val composer = composerFor(view)

        composer.replaceAll(original.length, "Tu viens demain ?", 17, 17)
        assertEquals("Tu viens demain ?", view.text.toString())
        composer.replaceAll("Tu viens demain ?".length, original, 2, 5)

        assertEquals(original, view.text.toString())
        assertEquals(2, view.selectionStart)
        assertEquals(5, view.selectionEnd)
    }

    @Test
    fun theWholeFlowWorksOnARealField_forSeveralTargets() {
        val answers = mapOf("es" to "¿Vienes mañana?", "fr" to "Tu viens demain ?", "ja" to "明日来ますか？")
        for ((code, answer) in answers) {
            val view = editText("Are you coming tomorrow?")
            val composer = composerFor(view)
            val read = composer.read(5_000) as ComposerRead.Text
            // the same steps the flow performs after a successful answer
            assertTrue(composer.replaceAll(read.text.length, answer, answer.length, answer.length))
            assertEquals(code, answer, view.text.toString())
        }
    }
}
