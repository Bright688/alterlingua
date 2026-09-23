package com.alterlingua.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingPresenter(private var canPostValue: Boolean = true) : TranslationPresenter {
    var shown: TranslatedConversation? = null
    var removedKey: String? = null

    override fun canPost() = canPostValue
    fun setCanPost(value: Boolean) {
        canPostValue = value
    }

    override fun show(conversation: TranslatedConversation) {
        shown = conversation
    }

    override fun remove(key: String) {
        removedKey = key
    }
}

class CompositeTranslationPresenterTest {

    private fun conversation(key: String = "c1") =
        TranslatedConversation(key, "Marie", false, listOf(TranslatedLine("Marie", "hi", "fr")), openIntent = null)

    @Test
    fun canPost_isTrueIfAnyDelegateCan() {
        val blocked = RecordingPresenter(canPostValue = false)
        val working = RecordingPresenter(canPostValue = true)
        assertTrue(CompositeTranslationPresenter(listOf(blocked, working)).canPost())
    }

    @Test
    fun canPost_isFalseOnlyIfNoDelegateCan() {
        val a = RecordingPresenter(canPostValue = false)
        val b = RecordingPresenter(canPostValue = false)
        assertFalse(CompositeTranslationPresenter(listOf(a, b)).canPost())
    }

    @Test
    fun canPost_isFalseWithNoDelegatesAtAll() {
        assertFalse(CompositeTranslationPresenter(emptyList()).canPost())
    }

    @Test
    fun show_reachesEveryDelegate_evenOneThatCannotPostItself() {
        val a = RecordingPresenter(canPostValue = false) // a real presenter guards itself inside show(); the composite does not second-guess it
        val b = RecordingPresenter(canPostValue = true)
        val convo = conversation()
        CompositeTranslationPresenter(listOf(a, b)).show(convo)
        assertEquals(convo, a.shown)
        assertEquals(convo, b.shown)
    }

    @Test
    fun remove_reachesEveryDelegate() {
        val a = RecordingPresenter()
        val b = RecordingPresenter()
        CompositeTranslationPresenter(listOf(a, b)).remove("c1")
        assertEquals("c1", a.removedKey)
        assertEquals("c1", b.removedKey)
    }
}
