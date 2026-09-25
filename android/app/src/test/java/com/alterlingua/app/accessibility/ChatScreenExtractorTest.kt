package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScreenExtractorTest {

    /** A 1000 x 2000 window: the top 11% (220 px) is the title bar; the composer starts at y = 1850. */
    private val screen = Bounds(0, 0, 1000, 2000)

    private fun node(text: String, top: Int, bottom: Int = top + 60, editable: Boolean = false, left: Int = 40, right: Int = 700) =
        ScreenNode(text, editable, Bounds(left, top, right, bottom))

    private fun extract(vararg nodes: ScreenNode) = ChatScreenExtractor.extract(ScreenSnapshot(screen, nodes.toList())).messages

    private val composer = node("", top = 1850, bottom = 1950, editable = true)

    @Test
    fun messages_areKeptWithTheirPositions_topToBottom() {
        val result = extract(node("Bonjour", 900), node("Tu viens demain ?", 400), composer)
        assertEquals(listOf("Tu viens demain ?", "Bonjour"), result.map { it.text })
        assertEquals(Bounds(40, 400, 700, 460), result.first().bounds)
    }

    @Test
    fun theTextBoxTheUserTypesIn_isNeverAMessage() {
        val result = extract(node("Are you coming tomorrow?", 1860, editable = true), node("Salut", 500))
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun anythingAtOrBelowTheTextBox_isNotAMessage() {
        // A "Send" label or an attachment hint sitting beside/below the composer.
        val result = extract(node("Type a message", 1860), node("Salut", 500), composer)
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun theTitleBarRegion_isNotPartOfTheConversation() {
        val result = extract(node("Marie", 80), node("last seen today at 9", 150), node("Salut", 500), composer)
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun clockTimes_areNotMessages() {
        val result = extract(node("12:34", 500), node("9:05 PM", 600), node("21.07", 700), node("10:00 a.m.", 800), node("Salut", 900), composer)
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun textWithNoLetter_isNotWorthTranslating() {
        val result = extract(node("✓✓", 500), node("12", 600), node("...", 700), node("👍", 800), node("Ok", 900), composer)
        assertEquals(listOf("Ok"), result.map { it.text })
    }

    @Test
    fun emptyAndZeroSizedNodes_areIgnored() {
        val result = extract(node("  ", 500), node("Ghost", 600, bottom = 600), node("Real", 700), composer)
        assertEquals(listOf("Real"), result.map { it.text })
    }

    @Test
    fun theSameTextAtTheSamePlace_isKeptOnce_butTheSameWordInTwoMessagesIsKeptTwice() {
        val result = extract(node("Ok", 500), node("Ok", 500), node("Ok", 800), composer)
        assertEquals(2, result.size)
    }

    @Test
    fun aVeryLongText_isSkipped() {
        val result = extract(node("a".repeat(2_001), 500), node("Salut", 700), composer)
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun withoutAVisibleTextBox_theBottomEdgeIsLeftAlone() {
        val result = extract(node("Salut", 600), node("Nav", 1900))
        assertEquals(listOf("Salut"), result.map { it.text })
    }

    @Test
    fun theConversationArea_runsFromBelowTheTitleBarToTheTopOfTheTextBox() {
        val area = ChatScreenExtractor.extract(ScreenSnapshot(screen, listOf(node("Salut", 500), composer))).area
        assertEquals(Bounds(0, 220, 1000, 1850), area)
    }

    @Test
    fun withoutATextBox_theAreaEndsAboveTheBottomEdge() {
        val area = ChatScreenExtractor.extract(ScreenSnapshot(screen, listOf(node("Salut", 500)))).area
        assertEquals(Bounds(0, 220, 1000, 1840), area)
    }

    @Test
    fun anEmptyWindow_givesNothing() {
        assertTrue(ChatScreenExtractor.extract(ScreenSnapshot(Bounds(0, 0, 0, 0), emptyList())).messages.isEmpty())
    }

    @Test
    fun messageTextIsNeverPrintedByToString() {
        val message = extract(node("Secret words", 500), composer).single()
        assertTrue(!message.toString().contains("Secret"))
        assertTrue(!node("Secret words", 500).toString().contains("Secret"))
    }
}
