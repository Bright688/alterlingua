package com.alterlingua.app.learning.progress

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where daily counts are kept. Each change is one atomic step, so two changes at once cannot overwrite each other. */
interface ProgressStore {
    suspend fun update(date: String, language: String, change: (DailyActivity) -> DailyActivity)

    /** Every day recorded for a language, oldest first. */
    suspend fun days(language: String): List<DailyActivity>

    suspend fun deleteLanguage(language: String)

    suspend fun deleteAll()
}

class InMemoryProgressStore : ProgressStore {
    private val lock = Mutex()
    private var rows = HashMap<Pair<String, String>, DailyActivity>()

    override suspend fun update(date: String, language: String, change: (DailyActivity) -> DailyActivity) = lock.withLock {
        val existing = rows[date to language] ?: DailyActivity(date, language)
        rows[date to language] = change(existing).copy(date = date, language = language)
    }

    override suspend fun days(language: String): List<DailyActivity> = lock.withLock {
        rows.values.filter { it.language == language }.sortedBy { it.date }
    }

    override suspend fun deleteLanguage(language: String) = lock.withLock {
        rows = HashMap(rows.filterValues { it.language != language })
    }

    override suspend fun deleteAll() = lock.withLock { rows = HashMap() }
}
