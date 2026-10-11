package com.alterlingua.app.learning.curriculum

/** A page of one language's standard curriculum, fetched from the backend (see CurriculumLessonService). */
sealed interface CurriculumPageResult {
    data class Success(val words: List<String>, val total: Int) : CurriculumPageResult

    /** The page could not be fetched (offline, the backend is unreachable, or the language is not covered). */
    data object Unavailable : CurriculumPageResult
}

/**
 * The standard curriculum: a large, shared word list per language, kept on the backend and fetched as needed rather
 * than bundled once into the app, so it can grow or be corrected without an app update (see CLAUDE.md's "it should be
 * gotten online and always accessible").
 */
interface CurriculumApi {
    /** Up to [limit] words of [languageCode]'s curriculum, in order, starting at [offset]. */
    suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): CurriculumPageResult
}
