package com.alterlingua.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenMessagesTest {

    private var now = 1_000L

    @Test
    fun aMessageIsNewOnlyTheFirstTime() {
        val seen = SeenMessages(clock = { now })
        assertTrue(seen.firstTime("chat", "Marie", "Tu viens demain ?", 10))
        assertFalse(seen.firstTime("chat", "Marie", "Tu viens demain ?", 10))
    }

    @Test
    fun theSameWordsAtAnotherTimeOrFromAnotherPersonAreDifferentMessages() {
        val seen = SeenMessages(clock = { now })
        assertTrue(seen.firstTime("chat", "Marie", "ok", 10))
        assertTrue(seen.firstTime("chat", "Marie", "ok", 20)) // "ok" again later
        assertTrue(seen.firstTime("chat", "Paul", "ok", 10))
        assertTrue(seen.firstTime("other chat", "Marie", "ok", 10))
    }

    @Test
    fun aRepostedNotificationOnlyOffersItsNewMessage() {
        val seen = SeenMessages(clock = { now })
        seen.firstTime("chat", "Marie", "one", 1)
        seen.firstTime("chat", "Marie", "two", 2)
        val offered = listOf(1L to "one", 2L to "two", 3L to "three").filter { (time, text) -> seen.firstTime("chat", "Marie", text, time) }
        assertEquals(listOf(3L to "three"), offered)
    }

    @Test
    fun aForgottenMessageCanBeTriedAgain() {
        val seen = SeenMessages(clock = { now })
        seen.firstTime("chat", "Marie", "hi", 1)
        seen.forget("chat", "Marie", "hi", 1)
        assertTrue(seen.firstTime("chat", "Marie", "hi", 1))
    }

    @Test
    fun oldEntriesExpire() {
        val seen = SeenMessages(ttlMillis = 1_000, clock = { now })
        seen.firstTime("chat", "Marie", "hi", 1)
        now += 1_500
        assertTrue(seen.firstTime("chat", "Marie", "hi", 1))
    }

    @Test
    fun theMemoryIsBounded() {
        val seen = SeenMessages(maxEntries = 10, clock = { now })
        repeat(100) { seen.firstTime("chat", "Marie", "message $it", it.toLong()) }
        assertTrue(seen.size <= 10)
    }

    @Test
    fun onlyAHashIsKept_neverTheText() {
        val seen = SeenMessages(clock = { now })
        seen.firstTime("chat-key", "Marie", "very private words 4200 euros", 5)
        val field = SeenMessages::class.java.getDeclaredField("seen").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val keys = (field.get(seen) as Map<String, Long>).keys
        for (key in keys) {
            assertTrue(key.matches(Regex("[0-9a-f]{64}")))
            assertFalse("private" in key || "Marie" in key || "4200" in key)
        }
    }

    @Test
    fun clearForgetsEverything() {
        val seen = SeenMessages(clock = { now })
        seen.firstTime("chat", "Marie", "hi", 1)
        seen.clear()
        assertEquals(0, seen.size)
        assertTrue(seen.firstTime("chat", "Marie", "hi", 1))
    }
}
