package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatScreenExtractorTest {

    @Test
    fun aPlainMessage_isKept() {
        assertEquals(listOf("Tu viens demain ?"), ChatScreenExtractor.extract(listOf(ScreenNode("Tu viens demain ?", isEditable = false))))
    }

    @Test
    fun theComposeBoxTheUserIsTypingIntoIsNeverRead() {
        assertEquals(
            emptyList<String>(),
            ChatScreenExtractor.extract(listOf(ScreenNode("Are you coming tomorrow?", isEditable = true))),
        )
    }

    @Test
    fun blankOrEmojiOnlyTextIsSkipped() {
        for (text in listOf("", "   ", "👍", "12:30", "!!!")) {
            assertEquals(text, emptyList<String>(), ChatScreenExtractor.extract(listOf(ScreenNode(text, isEditable = false))))
        }
    }

    @Test
    fun aVeryLongNodeIsSkipped() {
        assertEquals(emptyList<String>(), ChatScreenExtractor.extract(listOf(ScreenNode("a".repeat(2_001), isEditable = false))))
    }

    @Test
    fun duplicateTextOnTheSameScreenIsKeptOnce() {
        val nodes = listOf(ScreenNode("Tu viens demain ?", isEditable = false), ScreenNode("Tu viens demain ?", isEditable = false))
        assertEquals(listOf("Tu viens demain ?"), ChatScreenExtractor.extract(nodes))
    }

    @Test
    fun messagesInAnyScriptAreKept() {
        for (text in listOf("明日来ますか？", "你明天来吗？", "Kommst du morgen?", "¿Vienes mañana?", "Kom je morgen?")) {
            assertEquals(text, listOf(text), ChatScreenExtractor.extract(listOf(ScreenNode(text, isEditable = false))))
        }
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(listOf("hello"), ChatScreenExtractor.extract(listOf(ScreenNode("  hello  \n", isEditable = false))))
    }

    @Test
    fun aMixOfEditableAndPlainNodes_keepsOnlyThePlainOnes() {
        val nodes = listOf(
            ScreenNode("Type a message", isEditable = true),
            ScreenNode("Tu viens demain ?", isEditable = false),
            ScreenNode("👍", isEditable = false),
        )
        assertEquals(listOf("Tu viens demain ?"), ChatScreenExtractor.extract(nodes))
    }

    @Test
    fun noNodesAtAll_givesNothing() {
        assertEquals(emptyList<String>(), ChatScreenExtractor.extract(emptyList()))
    }
}
