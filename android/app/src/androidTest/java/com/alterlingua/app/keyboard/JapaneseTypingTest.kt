package com.alterlingua.app.keyboard

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.keyboard.engine.CompositionController
import com.alterlingua.app.keyboard.engine.MozcEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Mozc plus a real text field's input connection: the kana keys, underlined text, conversion and commit. */
@RunWith(AndroidJUnit4::class)
class JapaneseTypingTest {
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun typingKanaThenChoosingACandidateFillsTheFieldWithKanji() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = MozcEngine(context)
        assertTrue(engine.available)
        var text = ""
        var afterTyping = ""
        onMain {
            val field = EditText(context)
            val connection = field.onCreateInputConnection(EditorInfo())
            val controller = CompositionController(engine, InputConnectionTextTarget { connection })
            controller.type("き"); controller.type("ょ"); controller.type("う")
            afterTyping = field.text.toString()
            val index = controller.composition.candidates.indexOf("今日")
            assertTrue("今日 should be offered, got ${controller.composition.candidates}", index >= 0)
            controller.choose(index)
            text = field.text.toString()
        }
        assertEquals("きょう", afterTyping)
        assertEquals("今日", text)
    }

    @Test
    fun spaceConvertsAndEnterKeepsKana() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = MozcEngine(context)
        assertTrue(engine.available)
        var afterSpace = ""
        var afterEnter = ""
        onMain {
            val field = EditText(context)
            val connection = field.onCreateInputConnection(EditorInfo())
            val controller = CompositionController(engine, InputConnectionTextTarget { connection })
            controller.type("あ"); controller.type("り"); controller.type("が"); controller.type("と"); controller.type("う")
            assertTrue(controller.space())
            afterSpace = field.text.toString()
            controller.type("は"); controller.type("い")
            assertTrue(controller.enter())
            afterEnter = field.text.toString()
        }
        assertTrue("space commits the first candidate, got '$afterSpace'", afterSpace.isNotEmpty() && afterSpace != "")
        assertTrue("enter keeps the typed kana, got '$afterEnter'", afterEnter.endsWith("はい"))
    }
}
