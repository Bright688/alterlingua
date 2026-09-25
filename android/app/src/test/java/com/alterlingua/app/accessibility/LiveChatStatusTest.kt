package com.alterlingua.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveChatStatusTest {

    @Test
    fun beforeAnyReading_thereIsNothing() {
        assertNull(LiveChatStatus().last.value)
    }

    @Test
    fun aReading_recordsCountsAndBumpsTheReadCounter() {
        val status = LiveChatStatus()
        status.read(items = 40, textBoxes = 1, messages = 6, captions = 0)
        status.read(items = 42, textBoxes = 1, messages = 7, captions = 3)
        assertEquals(LiveChatReading(reads = 2, skipped = 0, items = 42, textBoxes = 1, messages = 7, captions = 3), status.last.value)
    }

    @Test
    fun skippedReadings_areCounted_withoutErasingTheLastRealReading() {
        val status = LiveChatStatus()
        status.read(items = 40, textBoxes = 1, messages = 6, captions = 2)
        status.skipped()
        status.skipped()
        val last = status.last.value!!
        assertEquals(2, last.skipped)
        assertEquals(1, last.reads)
        assertEquals(6, last.messages)
    }

    @Test
    fun aSkipBeforeAnyReading_isStillVisible() {
        val status = LiveChatStatus()
        status.skipped()
        assertEquals(LiveChatReading(skipped = 1), status.last.value)
    }

    @Test
    fun captionsDrawn_updatesOnlyTheCaptionCount() {
        val status = LiveChatStatus()
        status.read(items = 40, textBoxes = 1, messages = 6, captions = 0)
        status.captionsDrawn(5)
        assertEquals(5, status.last.value!!.captions)
        assertEquals(6, status.last.value!!.messages)
    }

    @Test
    fun clear_forgetsEverything() {
        val status = LiveChatStatus()
        status.read(items = 1, textBoxes = 1, messages = 1, captions = 1)
        status.clear()
        assertNull(status.last.value)
    }
}
