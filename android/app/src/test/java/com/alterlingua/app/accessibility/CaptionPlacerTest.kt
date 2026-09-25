package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPlacerTest {

    /** A 1000 px wide screen: the conversation area is below the title bar and above the text box. */
    private val area = Bounds(0, 200, 1000, 1800)

    /** Texts starting with "LONG" need 5 lines, everything else one, so truncation can be tested without fonts. */
    private val sizes = CaptionSizes(
        lineHeightPx = 40, compactLineHeightPx = 24, marginPx = 16, minWidthPx = 200, sideMinWidthPx = 120, sideGapPx = 40,
        measureLines = { text, _, _ -> if (text.startsWith("LONG")) 5 else 1 },
    )

    private fun message(text: String, top: Int, bottom: Int, left: Int = 40, right: Int = 700) = ChatMessage(text, Bounds(left, top, right, bottom))

    private fun place(vararg messages: ChatMessage, translate: Map<String, String>, inArea: Bounds = area, with: CaptionSizes = sizes): List<Caption> =
        CaptionPlacer.place(Conversation(messages.toList().sortedBy { it.bounds.top }, inArea), translate::get, with)

    private fun everything(vararg texts: String) = texts.associateWith { "T:$it" }

    private fun rect(c: Caption, s: CaptionSizes = sizes): Bounds =
        Bounds(c.left, c.top, c.left + c.width, c.top + c.maxLines * (if (c.compact) s.compactLineHeightPx else s.lineHeightPx))

    private fun overlaps(a: Bounds, b: Bounds) = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    private fun assertCoversNothing(captions: List<Caption>, messages: List<ChatMessage>, s: CaptionSizes = sizes, within: Bounds = area) {
        for (c in captions) {
            for (m in messages) assertFalse("a caption covers a message", overlaps(rect(c, s), m.bounds))
            assertTrue("a caption leaves the conversation area", rect(c, s).bottom <= within.bottom && rect(c, s).top >= within.top)
        }
        val rects = captions.map { rect(it, s) }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("captions overlap each other", overlaps(rects[i], rects[j]))
    }

    // ---- under the message -------------------------------------------------------------------------------------

    @Test
    fun withRoomBelow_aCaptionStartsAtTheBottomLeftOfItsMessage_andUsesOnlyTheLinesItNeeds() {
        val caption = place(message("Salut", 400, 460), translate = mapOf("Salut" to "Hi")).single()
        assertEquals(Caption("Hi", left = 40, top = 460, width = 660, maxLines = 1), caption)
    }

    @Test
    fun aLongTranslation_isLimitedToTheRoomBeforeTheNextMessage() {
        val roomy = place(message("A", 400, 460), message("B", 580, 640), translate = mapOf("A" to "LONG one"))
        assertEquals(3, roomy.single().maxLines) // 120 px of room = three lines (five needed: cut with an ellipsis)
        val middle = place(message("A", 400, 460), message("B", 545, 605), translate = mapOf("A" to "LONG one"))
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
        val a = message("Hola", 500, 540, left = 500, right = 700)
        val b = message("¿Quién eres?", 560, 600, left = 400, right = 700)
        val c = message("Bien je vous", 620, 660, left = 400, right = 700)
        val captions = place(a, b, c, translate = everything("Hola", "¿Quién eres?", "Bien je vous"))
        assertCoversNothing(captions, listOf(a, b, c))
    }

    @Test
    fun withNoRoomUnder_theCaptionGoesBesideTheMessage_onTheSideWithMoreRoom() {
        // Outgoing-style message hugging the right edge, next message only 20 px below: nowhere to go but the left.
        val a = message("Hola", 500, 540, left = 700, right = 940)
        val b = message("Next", 560, 600, left = 700, right = 940)
        val caption = place(a, b, translate = mapOf("Hola" to "Hello")).single()
        assertEquals("beside, level with the message", 500, caption.top)
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
    fun aRowOnlyAsTallAsACompactLine_getsACompactCaptionBesideIt() {
        // 30 px pitch between rows: too tight for a 40 px line beside the bubble, enough for a 24 px compact one.
        val a = message("Hola", 500, 530, left = 700, right = 900)
        val b = message("Adios", 530, 560, left = 400, right = 900)
        val caption = place(a, b, translate = mapOf("Hola" to "Hello")).single()
        assertTrue(caption.compact)
        assertEquals(500, caption.top)
        assertCoversNothing(listOf(caption), listOf(a, b))
    }

    @Test
    fun whenNothingFits_thereIsNoCaptionAtAll() {
        val a = message("Wide message", 500, 540, left = 20, right = 980)
        val b = message("Next", 550, 590, left = 20, right = 980)
        assertTrue(place(a, b, translate = mapOf("Wide message" to "Big")).isEmpty())
    }

    @Test
    fun aCaptionNeverEntersTheTextBoxArea() {
        val a = message("Last", 1740, 1780)
        val captions = place(a, translate = mapOf("Last" to "Dernier"))
        assertCoversNothing(captions, listOf(a))
    }

    @Test
    fun captionsDoNotOverlapEachOther() {
        val messages = (0 until 8).map { message("m$it", 300 + it * 70, 340 + it * 70, left = if (it % 2 == 0) 40 else 500, right = if (it % 2 == 0) 400 else 960) }
        val captions = place(*messages.toTypedArray(), translate = everything(*messages.map { it.text }.toTypedArray()))
        assertCoversNothing(captions, messages)
    }

    @Test
    fun aCaptionNeverCoversAQuoteOrNameThatIsPartOfTheScreen() {
        val a = message("Message", 500, 540)
        val name = message("Marie", 545, 585)
        assertCoversNothing(place(a, name, translate = mapOf("Message" to "M")), listOf(a, name))
    }

    @Test
    fun anEmptyConversation_orAnEmptyArea_givesNothing() {
        assertTrue(CaptionPlacer.place(Conversation(emptyList(), area), { "x" }, sizes).isEmpty())
        assertTrue(CaptionPlacer.place(Conversation(listOf(message("A", 400, 460)), Bounds(0, 0, 0, 0)), { "x" }, sizes).isEmpty())
    }

    // ---- the owner's real screen: a run of consecutive bubbles (regression) --------------------------------------

    /**
     * From the owner's phone (720 px wide, 2.0 density: caption line 41 px, compact 28 px, bubbles ~50 px apart).
     * Earlier versions reserved a caption's *maximum* height, so those phantom reservations blocked every later caption
     * in a run of bubbles and only the first and last got one.
     */
    @Test
    fun aRunOfConsecutiveBubbles_allGetACaption_andNothingIsCovered() {
        val phone = CaptionSizes(
            lineHeightPx = 41, compactLineHeightPx = 28, marginPx = 16, minWidthPx = 280, sideMinWidthPx = 192, sideGapPx = 32,
            measureLines = { _, _, _ -> 1 },
        )
        val screen = Bounds(0, 160, 720, 1300)
        val a = message("Comment ça va ?", 500, 540, left = 84, right = 300)
        val hola = message("Hola", 600, 640, left = 390, right = 440)
        val quien = message("¿Quién eres?", 650, 690, left = 282, right = 441)
        val ben = message("Ben je goed?", 700, 740, left = 282, right = 441)
        val hoop = message("Hoop dat het goed met je gaat", 750, 790, left = 170, right = 550)
        val naar = message("Naar waar ga je?", 850, 890, left = 250, right = 441)
        val all = listOf(a, hola, quien, ben, hoop, naar)
        val captions = place(*all.toTypedArray(), translate = everything(*all.map { it.text }.toTypedArray()), inArea = screen, with = phone)
        assertEquals("every message in the run has a caption", all.map { "T:" + it.text }.toSet(), captions.map { it.text }.toSet())
        assertCoversNothing(captions, all, phone, screen)
    }

    @Test
    fun captionTextIsNeverPrintedByToString() {
        assertFalse(place(message("Salut", 400, 460), translate = mapOf("Salut" to "Secret")).single().toString().contains("Secret"))
    }
}
