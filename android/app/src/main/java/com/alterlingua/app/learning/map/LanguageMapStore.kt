package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.engine.UnitKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the Personal Language Map is kept. The service works the same over any implementation. */
interface LanguageMapStore {
    /**
     * Reads the item for [key], lets [change] produce its new version (from null if it did not exist yet), and saves the
     * result as ONE step, so two updates at once cannot overwrite each other. Returning null from [change] leaves it as it was.
     */
    suspend fun update(key: UnitKey, change: (LanguageMapItem?) -> LanguageMapItem?): LanguageMapItem?

    suspend fun get(key: UnitKey): LanguageMapItem?

    /** Every item of one language, most recently seen first. */
    suspend fun items(language: String): List<LanguageMapItem>

    fun observe(language: String): Flow<List<LanguageMapItem>>

    /** Removes one language's whole map (the others are untouched). */
    suspend fun deleteLanguage(language: String)

    suspend fun deleteAll()
}

/** A [LanguageMapStore] in memory: for tests, and the reference for how any store must behave. */
class InMemoryLanguageMapStore : LanguageMapStore {
    private val lock = Mutex()
    private val state = MutableStateFlow<Map<UnitKey, LanguageMapItem>>(emptyMap())

    override suspend fun update(key: UnitKey, change: (LanguageMapItem?) -> LanguageMapItem?): LanguageMapItem? = lock.withLock {
        val existing = state.value[key]
        val updated = change(existing) ?: return existing
        require(updated.key == key) { "an update may not change which unit an item is" }
        state.value = state.value + (key to updated)
        updated
    }

    override suspend fun get(key: UnitKey): LanguageMapItem? = state.value[key]

    override suspend fun items(language: String): List<LanguageMapItem> = ofLanguage(state.value, language)

    override fun observe(language: String): Flow<List<LanguageMapItem>> = state.map { ofLanguage(it, language) }

    override suspend fun deleteLanguage(language: String) = lock.withLock {
        state.value = state.value.filterKeys { it.language != language }
    }

    override suspend fun deleteAll() = lock.withLock { state.value = emptyMap() }

    private fun ofLanguage(all: Map<UnitKey, LanguageMapItem>, language: String) =
        all.values.filter { it.language == language }.sortedByDescending { it.lastSeen }
}
