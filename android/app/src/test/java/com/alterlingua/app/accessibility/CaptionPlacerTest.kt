package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPlacerTest {

    private val screen = Bounds(0, 0, 1000, 2000)
    private val line = 40

    private fun message(text: String, top: Int, bottom: Int, left: Int = 40, right: Int = 700) = ChatMessage(text, Bounds(left, top, right, bottom))

    private fun place(messages: List<ChatMessage>, translations: Map<String, String>) = CaptionPlacer.place(
        messages = messages,
        translations = translations::get,
        screen = screen,
        lineHeightPx = line,
        marginPx = 16,
        minWidthPx = 200,
    )

    @Test
    fun aCaptionStartsAtTheBottomEdgeOfItsOwnMessage_atItsLeftEdge() {
        val caption = place(listOf(message("Salut", 400, 460)), mapOf("Salut" to "Hi")).single()
        assertEquals("Hi", caption.text)
        assertEquals(460, caption.top)
        assertEquals(40, caption.left)
        assertEquals(660, caption.width)
    }

    @Test
    fun aMessageWithNoTranslation_getsNoCaption() {
        val result = place(listOf(message("Salut", 400, 460), message("Bonjour", 700, 760)), mapOf("Bonjour" to "Hello"))
        assertEquals(listOf("Hello"), result.map { it.text })
    }

    @Test
    fun theCaptionIsLimitedToTheRoomBeforeTheNextMessage_butAlwaysHasOneLine() {
        val tight = place(listOf(message("A", 400, 460), message("B", 470, 530)), mapOf("A" to "a", "B" to "b"))
        assertEquals(1, tight.first().maxLines) // 10 px of room: still one line, never zero
        val roomy = place(listOf(message("A", 400, 460), message("B", 620, 680)), mapOf("A" to "a"))
        assertEquals(3, roomy.single().maxLines) // 160 px of room: capped at three lines
        val middle = place(listOf(message("A", 400, 460), message("B", 540, 600)), mapOf("A" to "a"))
        assertEquals(2, middle.single().maxLines) // 80 px of room = two lines
    }

    @Test
    fun aNarrowMessage_getsAtLeastTheMinimumWidth_andStaysOnScreen() {
        val caption = place(listOf(message("Ok", 400, 460, left = 900, right = 960)), mapOf("Ok" to "Ok!")).single()
        assertEquals(200, caption.width)
        assertTrue(caption.left + caption.width <= screen.right - 16)
    }

    @Test
    fun aWideMessage_isNotWiderThanTheScreenMinusItsMargins() {
        val caption = place(listOf(message("Long", 400, 460, left = 0, right = 1000)), mapOf("Long" to "x")).single()
        assertEquals(968, caption.width)
        assertEquals(16, caption.left)
    }

    @Test
    fun aMessageAtTheVeryBottom_withNoRoomForALine_getsNoCaption() {
        assertTrue(place(listOf(message("Last", 1940, 1990)), mapOf("Last" to "x")).isEmpty())
    }

    @Test
    fun captionTextIsNeverPrintedByToString() {
        assertTrue(!place(listOf(message("Salut", 400, 460)), mapOf("Salut" to "Secret")).single().toString().contains("Secret"))
    }
}
