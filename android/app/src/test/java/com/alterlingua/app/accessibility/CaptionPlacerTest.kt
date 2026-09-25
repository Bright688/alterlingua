package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPlacerTest {

    /** A 1000 px wide screen: the conversation area is below the title bar and above the text box. */
    private val area = Bounds(0, 200, 1000, 1800)

    private val allowance = 8

    /**
     * Sizes 20 / 17 / 14 px with line heights 26 / 22 / 18 px. Texts starting with "LONG" need 6 / 4 / 2 lines at those
     * sizes, everything else one line, so truncation can be tested without fonts.
     */
    private val sizes = CaptionSizes(
        textSizesPx = listOf(20f, 17f, 14f),
        lineHeightPx = { (it * 1.2f).toInt() + 2 },
        marginPx = 16,
        minWidthPx = 200,
        overlapAllowancePx = allowance,
        measureLines = { text, _, size -> if (text.startsWith("LONG")) (if (size >= 20f) 6 else if (size >= 17f) 4 else 2) else 1 },
    )

    private fun message(text: String, top: Int, bottom: Int, left: Int = 40, right: Int = 700) = ChatMessage(text, Bounds(left, top, right, bottom))

    private fun place(vararg messages: ChatMessage, translate: Map<String, String>, inArea: Bounds = area, with: CaptionSizes = sizes): List<Caption> =
        CaptionPlacer.place(Conversation(messages.toList().sortedBy { it.bounds.top }, inArea), translate::get, with)

    private fun everything(vararg texts: String) = texts.associateWith { "T:$it" }

    private fun rect(c: Caption, s: CaptionSizes = sizes): Bounds = Bounds(c.left, c.top, c.left + c.width, c.top + c.maxLines * s.lineHeightPx(c.textSizePx))

    private fun overlaps(a: Bounds, b: Bounds) = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /** No caption covers a message (beyond the allowed reach into a text box's empty top padding), another caption, or leaves the area. */
    private fun assertCoversNothing(captions: List<Caption>, messages: List<ChatMessage>, s: CaptionSizes = sizes, within: Bounds = area) {
        for (c in captions) {
            val r = rect(c, s)
            for (m in messages) {
                val protectedPart = Bounds(m.bounds.left, m.bounds.top + s.overlapAllowancePx, m.bounds.right, m.bounds.bottom)
                assertFalse("a caption covers a message", overlaps(r, protectedPart))
            }
            assertTrue("a caption leaves the conversation area", r.bottom <= within.bottom && r.top >= within.top)
        }
        val rects = captions.map { rect(it, s) }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("captions overlap each other", overlaps(rects[i], rects[j]))
    }

    // ---- always directly under the message ---------------------------------------------------------------------

    @Test
    fun withPlentyOfRoom_aCaptionStartsAtTheBottomLeftOfItsMessage_atTheLargestSize() {
        val caption = place(message("Salut", 400, 460), translate = mapOf("Salut" to "Hi")).single()
        assertEquals(Caption("Hi", left = 40, top = 460, width = 660, maxLines = 1, textSizePx = 20f), caption)
    }

    @Test
    fun theCaptionTextGetsSmallerAsTheGapToTheNextMessageGetsSmaller() {
        fun sizeFor(gap: Int) = place(message("A", 400, 460), message("B", 460 + gap, 520 + gap), translate = mapOf("A" to "a")).single().textSizePx
        assertEquals(20f, sizeFor(20), 0f) // room 28: a 26 px line fits
        assertEquals(17f, sizeFor(15), 0f) // room 23: only a 22 px line fits
        assertEquals(14f, sizeFor(12), 0f) // room 20 (the gap in a run of messages from one sender): only an 18 px line
    }

    @Test
    fun aRunOfMessagesFromOneSender_eachGetsACaptionDirectlyUnderIt() {
        // Positions measured in WhatsApp on the owner's phone: text boxes 55 px tall, 12 px apart.
        val run = listOf(
            message("Hola", 299, 354, left = 310, right = 538),
            message("¿Quién eres?", 366, 421, left = 305, right = 538),
            message("Ben je goed?", 433, 488, left = 153, right = 678),
            message("Hoop dat het goed", 545, 600, left = 251, right = 541),
            message("Naar waar ga je?", 612, 667, left = 308, right = 541),
        )
        val captions = place(*run.toTypedArray(), translate = everything(*run.map { it.text }.toTypedArray()))
        assertEquals("every message has a caption", run.size, captions.size)
        run.zip(captions).forEach { (m, c) -> assertEquals("directly under its own message", m.bounds.bottom, c.top) }
        assertCoversNothing(captions, run)
    }

    @Test
    fun aLongTranslation_usesTheSmallestSizeThatShowsAllOfIt_andIsCutOnlyWhenNothingFits() {
        // Plenty of room: 108 px. At 20 px it needs 6 lines (only 3 allowed), at 17 px 4 lines, at 14 px 2 lines: fits.
        val roomy = place(message("A", 400, 460), message("B", 568, 628), translate = mapOf("A" to "LONG one")).single()
        assertEquals(14f, roomy.textSizePx, 0f)
        assertEquals(2, roomy.maxLines)
        // Tight: room 20 px only allows one 14 px line of the two it needs: cut with an ellipsis, never covering B.
        val tight = place(message("A", 400, 460), message("B", 472, 532), translate = mapOf("A" to "LONG one")).single()
        assertEquals(14f, tight.textSizePx, 0f)
        assertEquals(1, tight.maxLines)
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

    // ---- it must never cover the next message (the owner's requirement) -----------------------------------------

    @Test
    fun withNoRoomForEvenOneSmallLine_thereIsNoCaption_ratherThanCoveringTheNextMessage() {
        // 2 px gap + 8 px allowance = 10 px: less than the smallest 18 px line.
        assertTrue(place(message("A", 400, 460), message("B", 462, 522), translate = mapOf("A" to "a")).isEmpty())
        // Text boxes that touch or overlap: nothing at all.
        assertTrue(place(message("A", 400, 460), message("B", 455, 515), translate = mapOf("A" to "a")).isEmpty())
    }

    @Test
    fun theReachIntoTheNextTextBox_isOnlyTheAllowedAmount() {
        val a = message("A", 400, 460)
        val b = message("B", 472, 532) // 12 px gap
        val caption = place(a, b, translate = mapOf("A" to "a")).single()
        val bottom = rect(caption).bottom
        assertTrue("reaches at most $allowance px into the next text box", bottom <= b.bounds.top + allowance)
    }

    @Test
    fun aCaptionNeverEntersTheTextBoxArea() {
        // Last message: room ends at the area's bottom, with no allowance there.
        val fits = place(message("Last", 1740, 1780), translate = mapOf("Last" to "Dernier"))
        assertCoversNothing(fits, listOf(message("Last", 1740, 1780)))
        assertEquals(1, fits.size)
        assertTrue(place(message("Last", 1740, 1790), translate = mapOf("Last" to "Dernier")).isEmpty()) // 10 px: too little
    }

    @Test
    fun captionsInDifferentColumns_doNotOverlapEachOther() {
        val messages = (0 until 8).map { message("m$it", 300 + it * 70, 340 + it * 70, left = if (it % 2 == 0) 40 else 500, right = if (it % 2 == 0) 400 else 960) }
        val captions = place(*messages.toTypedArray(), translate = everything(*messages.map { it.text }.toTypedArray()))
        assertEquals(8, captions.size)
        assertCoversNothing(captions, messages)
    }

    @Test
    fun aCaptionNeverCoversANameOrQuoteThatIsPartOfTheScreen() {
        val a = message("Message", 500, 540)
        val name = message("Marie", 545, 585) // 5 px below: only 13 px of room
        assertCoversNothing(place(a, name, translate = mapOf("A" to "M", "Message" to "M")), listOf(a, name))
    }

    @Test
    fun anEmptyConversation_orAnEmptyArea_givesNothing() {
        assertTrue(CaptionPlacer.place(Conversation(emptyList(), area), { "x" }, sizes).isEmpty())
        assertTrue(CaptionPlacer.place(Conversation(listOf(message("A", 400, 460)), Bounds(0, 0, 0, 0)), { "x" }, sizes).isEmpty())
        assertNull(place(message("A", 400, 460), translate = emptyMap()).firstOrNull())
    }

    @Test
    fun captionTextIsNeverPrintedByToString() {
        assertFalse(place(message("Salut", 400, 460), translate = mapOf("Salut" to "Secret")).single().toString().contains("Secret"))
    }
}
