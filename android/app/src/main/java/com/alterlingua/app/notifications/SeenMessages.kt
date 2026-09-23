package com.alterlingua.app.notifications

import java.security.MessageDigest

/**
 * Remembers which messages were already translated, so a notification that is posted again (WhatsApp re-posts a
 * conversation's notification with every new message, and sometimes twice) is not translated twice.
 *
 * PRIVACY: only a one-way hash of each message is kept, never its text, and only in memory, for a limited time and
 * number. Nothing is written to disk.
 */
class SeenMessages(
    private val maxEntries: Int = 300,
    private val ttlMillis: Long = 2 * 60 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seen = LinkedHashMap<String, Long>()

    /** True the first time this message is offered, false for repeats. */
    @Synchronized
    fun firstTime(conversationKey: String, sender: String, text: String, timestamp: Long): Boolean {
        val now = clock()
        prune(now)
        val id = hash("$conversationKey\u0000$sender\u0000$timestamp\u0000$text")
        if (seen.containsKey(id)) return false
        seen[id] = now
        return true
    }

    /** Lets a message be tried again (used when translating it failed for a reason that may pass). */
    @Synchronized
    fun forget(conversationKey: String, sender: String, text: String, timestamp: Long) {
        seen.remove(hash("$conversationKey\u0000$sender\u0000$timestamp\u0000$text"))
    }

    @Synchronized
    fun clear() = seen.clear()

    val size: Int @Synchronized get() = seen.size

    private fun prune(now: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > ttlMillis) iterator.remove() else break // oldest first
        }
        while (seen.size >= maxEntries) seen.remove(seen.keys.first())
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
