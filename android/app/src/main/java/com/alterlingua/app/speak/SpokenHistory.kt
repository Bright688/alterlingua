package com.alterlingua.app.speak

import com.alterlingua.app.learning.Language
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One past translated voice message (CLAUDE.md milestone 22; added 2026-10-18 at the owner's explicit request to
 * see history of past messages). [id] is 0 for an entry not yet saved.
 *
 * PRIVACY NOTE (CLAUDE.md section 25 normally prefers discarding full message text): this is a deliberate,
 * explicitly-requested exception -- the owner asked to be able to see history, not just counts. It is kept local
 * only (never sent to the backend for storage), encrypted at rest like the rest of the app's local databases, and
 * the user can delete one entry or all of them at any time (section 52), including through the existing
 * "Erase learning data" control in Settings.
 */
data class SpokenHistoryEntry(
    val id: Long = 0,
    val createdAtMillis: Long,
    val sourceLanguage: Language?,
    val targetLanguage: Language,
    val transcript: String,
    val translation: String,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "SpokenHistoryEntry(id=$id, redacted)"
}

interface SpokenHistoryStore {
    suspend fun add(entry: SpokenHistoryEntry)
    suspend fun recent(limit: Int = MAX_KEPT): List<SpokenHistoryEntry>
    suspend fun delete(id: Long)
    suspend fun deleteAll()

    companion object {
        /** Older entries beyond this are pruned when a new one is added, so history cannot grow without bound. */
        const val MAX_KEPT = 200

        /** For flows/tests that do not want history kept at all. */
        val None: SpokenHistoryStore = object : SpokenHistoryStore {
            override suspend fun add(entry: SpokenHistoryEntry) = Unit
            override suspend fun recent(limit: Int) = emptyList<SpokenHistoryEntry>()
            override suspend fun delete(id: Long) = Unit
            override suspend fun deleteAll() = Unit
        }
    }
}

class InMemorySpokenHistoryStore : SpokenHistoryStore {
    private val lock = Mutex()
    private var rows = listOf<SpokenHistoryEntry>()
    private var nextId = 1L

    override suspend fun add(entry: SpokenHistoryEntry) = lock.withLock {
        rows = (rows + entry.copy(id = nextId++)).takeLast(SpokenHistoryStore.MAX_KEPT)
    }

    override suspend fun recent(limit: Int): List<SpokenHistoryEntry> = lock.withLock {
        rows.sortedByDescending { it.createdAtMillis }.take(limit)
    }

    override suspend fun delete(id: Long) = lock.withLock {
        rows = rows.filterNot { it.id == id }
    }

    override suspend fun deleteAll() = lock.withLock { rows = emptyList() }
}
