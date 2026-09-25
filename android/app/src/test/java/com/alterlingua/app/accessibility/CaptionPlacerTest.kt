package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPlacerTest {

    /** The conversation area of a 1000 px wide screen: below the title bar, above the text box. */
    private val area = Bounds(0, 200, 1000, 1800)
    private val sizes = CaptionSizes(lineHeightPx = 40, compactLineHeightPx = 24, marginPx = 16, minWidthPx = 200, sideGapPx = 40)

    private fun message(text: String, top: Int, bottom: Int, left: Int = 40, right: Int = 700) = ChatMessage(text, Bounds(left, top, right, bottom))

    private fun place(vararg messages: ChatMessage, translate: Map<String, String>): List<Caption> =
        CaptionPlacer.place(Conversation(messages.toList().sortedBy { it.bounds.top }, area), translate::get, sizes)

    private fun everything(vararg texts: String) = texts.associateWith { "T:$it" }

    private fun rect(c: Caption): Bounds =
        Bounds(c.left, c.top, c.left + c.width, c.top + c.maxLines * (if (c.compact) sizes.compactLineHeightPx else sizes.lineHeightPx))

    private fun overlaps(a: Bounds, b: Bounds) = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    // ---- under the message -------------------------------------------------------------------------------------

    @Test
    fun withRoomBelow_aCaptionStartsAtTheBottomLeftOfItsMessage() {
        val caption = place(message("Salut", 400, 460), translate = mapOf("Salut" to "Hi")).single()
        assertEquals(Caption("Hi", left = 40, top = 460, width = 660, maxLines = 3), caption)
    }

    @Test
    fun theCaptionIsLimitedToTheRoomBeforeTheNextMessage() {
        val result = place(message("A", 400, 460), message("B", 580, 640), translate = mapOf("A" to "a"))
        assertEquals(3, result.single().maxLines) // 120 px of room = three lines
        val middle = place(message("A", 400, 460), message("B", 545, 605), translate = mapOf("A" to "a"))
        assertEquals(2, middle.single().maxLines) // 85 px of room = two lines
    }

    @Test
    fun aMessageWithNoTranslation_getsNoCaption() {
        val result = place(message("Salut", 400, 460), message("Bonjour", 900, 960), translate = mapOf("Bonjour" to "Hello"))
        assertEquals(listOf("Hello"), result.map { it.text })
    }

    @Test
    fun aNarrowMessage_getsAtLeastTheMinimumWidth_andStaysOnScreen() {
        val caption = place(message("Ok", 400, 460, left = 900, right = 960), translate = mapOf("Ok" to "Ok!")).single()
        assertEquals(200, caption.width)
        assertTrue(caption.left + caption.width <= area.right - 16)
    }

    @Test
    fun aWideMessage_isNotWiderThanTheScreenMinusItsMargins() {
        val caption = place(message("Long", 400, 460, left = 0, right = 1000), translate = mapOf("Long" to "x")).single()
        assertEquals(968, caption.width)
        assertEquals(16, caption.left)
    }

    // ---- it must never cover another message (the owner's requirement) --------------------------------------------

    @Test
    fun tightBubbles_neverGetACaptionThatCoversTheNextMessage() {
        // Consecutive bubbles from the same sender: only ~20 px between one text and the next (less than a line).
        val a = message("Hola", 500, 540, left = 500, right = 700)
        val b = message("¿Quién eres?", 560, 600, left = 400, right = 700)
        val c = message("Bien je vous", 620, 660, left = 400, right = 700)
        val captions = place(a, b, c, translate = everything("Hola", "¿Quién eres?", "Bien je vous"))
        val protectedRects = listOf(a, b, c).map { it.bounds }
        for (caption in captions) {
            for (m in protectedRects) assertFalse("a caption covers a message", overlaps(rect(caption), m))
        }
    }

    @Test
    fun withNoRoomUnder_theCaptionGoesBesideTheMessage_onTheSideWithMoreRoom() {
        // Outgoing-style message hugging the right edge, next message only 20 px below: nowhere to go but the left.
        val a = message("Hola", 500, 540, left = 700, right = 940)
        val b = message("Next", 560, 600, left = 700, right = 940)
        val caption = place(a, b, translate = mapOf("Hola" to "Hello")).single()
        assertTrue("beside, not under", caption.top == 500)
        assertTrue("left of the bubble with a gap", caption.left + caption.width <= 700 - 40)
        assertEquals("Hello", caption.text)
    }

    @Test
    fun anIncomingStyleMessage_getsItsCaptionBesideItOnTheRight() {
        val a = message("Hello", 500, 540, left = 40, right = 240)
        val b = message("Next", 555, 595, left = 40, right = 240)
        val caption = place(a, b, translate = mapOf("Hello" to "Bonjour")).single()
        assertEquals(500, caption.top)
        assertTrue(caption.left >= 240 + 40)
    }

    @Test
    fun whenNeitherUnderNorBesideFits_aCompactLineIsUsed_ifThatFitsUnder() {
        // A wide message (no side room) with 30 px below: less than a 40 px line, more than a 24 px compact line.
        val a = message("Wide message", 500, 540, left = 20, right = 980)
        val b = message("Next", 570, 610, left = 20, right = 980)
        val caption = place(a, b, translate = mapOf("Wide message" to "Big")).single()
        assertTrue(caption.compact)
        assertEquals(1, caption.maxLines)
        assertEquals(540, caption.top)
    }

    @Test
    fun whenNothingFits_thereIsNoCaptionAtAll() {
        // A wide message with only 10 px below: not even a compact line; no room at the sides either.
        val a = message("Wide message", 500, 540, left = 20, right = 980)
        val b = message("Next", 550, 590, left = 20, right = 980)
        assertTrue(place(a, b, translate = mapOf("Wide message" to "Big")).isEmpty())
    }

    @Test
    fun aCaptionNeverEntersTheTextBoxArea() {
        // The last message sits right above the text box: the room ends at the area's bottom.
        val a = message("Last", 1740, 1780)
        val captions = place(a, translate = mapOf("Last" to "Dernier"))
        for (caption in captions) assertTrue(rect(caption).bottom <= area.bottom)
        assertEquals(1, captions.single().maxLines) // 20 px of room is less than a line: only compact or beside can be used
    }

    @Test
    fun captionsDoNotOverlapEachOther() {
        val messages = (0 until 8).map { message("m$it", 300 + it * 70, 340 + it * 70, left = if (it % 2 == 0) 40 else 500, right = if (it % 2 == 0) 400 else 960) }
        val captions = place(*messages.toTypedArray(), translate = everything(*messages.map { it.text }.toTypedArray()))
        val rects = captions.map(::rect)
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("captions overlap", overlaps(rects[i], rects[j]))
        for (caption in captions) for (m in messages) assertFalse("a caption covers a message", overlaps(rect(caption), m.bounds))
    }

    @Test
    fun aCaptionNeverCoversAQuoteOrNameThatIsPartOfTheScreen() {
        // Anything the extractor kept is protected, whatever it is: here a sender name sitting just below.
        val a = message("Message", 500, 540)
        val name = message("Marie", 545, 585)
        val captions = place(a, name, translate = mapOf("Message" to "M"))
        for (caption in captions) assertFalse(overlaps(rect(caption), name.bounds))
    }

    @Test
    fun aMessageOnAnEmptyConversation_orAnEmptyArea_givesNothing() {
        assertTrue(CaptionPlacer.place(Conversation(emptyList(), area), { "x" }, sizes).isEmpty())
        assertTrue(CaptionPlacer.place(Conversation(listOf(message("A", 400, 460)), Bounds(0, 0, 0, 0)), { "x" }, sizes).isEmpty())
    }

    @Test
    fun captionTextIsNeverPrintedByToString() {
        assertFalse(place(message("Salut", 400, 460), translate = mapOf("Salut" to "Secret")).single().toString().contains("Secret"))
    }
}
