package com.alterlingua.app.learning.reading

/** A page of one language's standard Reading practice sentences, fetched from the backend (see
 * backend/app/lessons/reading/service.py and its data/SOURCE.md for where these sentences come from). */
sealed interface ReadingPageResult {
    data class Success(val sentences: List<String>, val total: Int) : ReadingPageResult

    /** The page could not be fetched (offline, the backend is unreachable, or the language is not covered). */
    data object Unavailable : ReadingPageResult
}

/**
 * The standard Reading practice: a large, shared sentence list per language, kept on the backend and fetched as
 * needed rather than bundled once into the app -- the Reading counterpart to [com.alterlingua.app.learning.curriculum.CurriculumApi].
 */
interface ReadingApi {
    /** Up to [limit] sentences of [languageCode]'s reading practice, in order, starting at [offset]. */
    suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): ReadingPageResult
}
