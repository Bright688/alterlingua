package com.alterlingua.app.keyboard.engine

import com.alterlingua.app.keyboard.TextTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tiny stand-in dictionary: it only exists to test the controller, not to convert real text. */
private class FakeEngine(private val table: Map<String, List<String>>) : CandidateEngine {
    private var typed = ""
    var resets = 0

    private fun state() = Composition(typed, table[typed].orEmpty())

    override fun append(input: String): Composition { typed += input; return state() }

    override fun deleteLast(): Composition { typed = typed.dropLast(1); return state() }

    override fun choose(index: Int): Choice {
        val word = state().candidates[index]
        typed = ""
        return Choice(word)
    }

    override fun reset() { typed = ""; resets++ }
}

private class RecordingTarget : TextTarget {
    val field = StringBuilder()
    var composing: String = ""

    override fun commitText(text: String) { composing = ""; field.append(text) }
    override fun setComposingText(text: String) { composing = text }
    override fun deleteBackward() { if (field.isNotEmpty()) field.deleteCharAt(field.length - 1) }
    override fun performEditorAction(actionId: Int) = Unit
    override fun sendKey(keyCode: Int) = Unit
    override fun finishComposing() = Unit
    override fun cursorCapsMode(inputType: Int) = 0
}

class CompositionControllerTest {
    private val table = mapOf("ni" to listOf("你", "尼"), "nihao" to listOf("你好", "你号"))
    private val engine = FakeEngine(table)
    private val target = RecordingTarget()
    private val controller = CompositionController(engine, target)

    @Test
    fun typedInputIsShownUnderlinedAndCandidatesAreOffered() {
        controller.type("n")
        controller.type("i")
        assertEquals("ni", target.composing)
        assertEquals(listOf("你", "尼"), controller.composition.candidates)
        assertEquals("", target.field.toString())
        assertTrue(controller.isComposing)
    }

    @Test
    fun choosingACandidateReplacesTheUnderlinedTextAndEndsComposition() {
        "nihao".forEach { controller.type(it.toString()) }
        controller.choose(0)
        assertEquals("你好", target.field.toString())
        assertEquals("", target.composing)
        assertFalse(controller.isComposing)
    }

    @Test
    fun spaceChoosesTheFirstCandidateOnlyWhileComposing() {
        assertFalse("not composing: space is an ordinary key", controller.space())
        controller.type("n"); controller.type("i")
        assertTrue(controller.space())
        assertEquals("你", target.field.toString())
    }

    @Test
    fun spaceWithNoCandidatesKeepsWhatWasTyped() {
        controller.type("z")
        assertTrue(controller.space())
        assertEquals("z", target.field.toString())
    }

    @Test
    fun enterKeepsTheTypedLettersAndReportsNothingToSendWhenNotComposing() {
        assertFalse(controller.enter())
        controller.type("n"); controller.type("i")
        assertTrue(controller.enter())
        assertEquals("ni", target.field.toString())
        assertFalse(controller.isComposing)
    }

    @Test
    fun backspaceRemovesComposingInputFirst_thenTheFieldsOwnBackspaceApplies() {
        assertFalse(controller.backspace())
        controller.type("n"); controller.type("i")
        assertTrue(controller.backspace())
        assertEquals("n", target.composing)
        assertTrue(controller.backspace())
        assertEquals("", target.composing)
        assertFalse(controller.backspace())
    }

    @Test
    fun choosingAnUnknownCandidateDoesNothing() {
        controller.type("n"); controller.type("i")
        controller.choose(5)
        assertEquals("", target.field.toString())
        assertEquals("ni", target.composing)
    }

    @Test
    fun cancelForgetsTheCompositionWithoutTouchingTheField() {
        target.field.append("abc")
        controller.type("n")
        controller.cancel()
        assertEquals("abc", target.field.toString())
        assertFalse(controller.isComposing)
        assertEquals(1, engine.resets)
    }

    @Test
    fun theCandidateBarIsToldAboutEveryChange() {
        val seen = mutableListOf<Composition>()
        controller.onChanged = { seen += it }
        controller.type("n"); controller.type("i"); controller.choose(1)
        assertEquals(listOf("n", "ni", ""), seen.map { it.preedit })
        assertNull(seen.firstOrNull { it.preedit == "ni" && it.candidates.isEmpty() })
    }
}
