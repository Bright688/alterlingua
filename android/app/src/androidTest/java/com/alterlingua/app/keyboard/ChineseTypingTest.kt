package com.alterlingua.app.keyboard

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.keyboard.engine.CompositionController
import com.alterlingua.app.keyboard.engine.RimeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real librime plus a real text field's input connection: pinyin letters, underlined text, conversion and commit. */
@RunWith(AndroidJUnit4::class)
class ChineseTypingTest {
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun typingPinyinThenChoosingACandidateFillsTheFieldWithHanzi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = RimeEngine(context)
        assertTrue(engine.available)
        var afterTyping = ""
        var text = ""
        onMain {
            val field = EditText(context)
            val connection = field.onCreateInputConnection(EditorInfo())
            val controller = CompositionController(engine, InputConnectionTextTarget { connection })
            "nihao".forEach { controller.type(it.toString()) }
            afterTyping = field.text.toString()
            val index = controller.composition.candidates.indexOf("你好")
            assertTrue("你好 should be offered, got ${controller.composition.candidates}", index >= 0)
            controller.choose(index)
            text = field.text.toString()
        }
        assertEquals("ni hao", afterTyping)
        assertEquals("你好", text)
    }

    @Test
    fun spaceConvertsAndEnterKeepsThePinyinLetters() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = RimeEngine(context)
        assertTrue(engine.available)
        var afterSpace = ""
        var afterEnter = ""
        onMain {
            val field = EditText(context)
            val connection = field.onCreateInputConnection(EditorInfo())
            val controller = CompositionController(engine, InputConnectionTextTarget { connection })
            "xie".forEach { controller.type(it.toString()) }
            assertTrue(controller.space())
            afterSpace = field.text.toString()
            "zz".forEach { controller.type(it.toString()) }
            assertTrue(controller.enter())
            afterEnter = field.text.toString()
        }
        assertTrue("space commits the first candidate, got '$afterSpace'", afterSpace.isNotEmpty() && afterSpace.all { it.code > 127 })
        assertTrue("enter keeps the typed letters, got '$afterEnter'", afterEnter.endsWith("zz"))
    }
}
