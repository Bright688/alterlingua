package com.alterlingua.app.learning.reading

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider

/** One sentence of a Reading session and its meaning in the learner's own language -- null until [ReadingLessonService.reveal]
 * is asked for it, so the translation is only fetched if the learner actually wants it. */
data class ReadingCard(val sentence: String, val translation: String?)

/** What the Reading exercise shows. */
sealed interface ReadingSessionState {
    data object Loading : ReadingSessionState

    /** [position] is the sentence currently shown; [language] is what it (and its Listen button) is in. */
    data class InProgress(val cards: List<ReadingCard>, val position: Int, val language: Language) : ReadingSessionState {
        val current: ReadingCard get() = cards[position.coerceIn(0, cards.lastIndex)]
        val isLast: Boolean get() = position == cards.lastIndex
    }

    /** The session's sentences are all done; starting again picks a fresh set. */
    data object Finished : ReadingSessionState

    /** The reading practice could not be fetched right now (offline, or the backend is unreachable). */
    data object Unavailable : ReadingSessionState
}

private const val SESSION_SIZE = 8

// Matches the real backend's own page-size cap (backend/app/lessons/reading/router.py: Query(..., le=50)) -- a
// request for more than that must be rejected, not silently honored, exactly like the real server would.
private const val MAX_PAGE_SIZE = 50

/**
 * The standard Reading practice: real sentences (see [ReadingApi] and backend/app/lessons/reading/data/SOURCE.md
 * for where they come from), independent of any one user's own conversations -- the Reading counterpart to
 * [com.alterlingua.app.learning.curriculum.CurriculumLessonService]. Deliberately simpler: no daily goal, no
 * Personal Language Map mastery tracking and no entitlement gate yet (every tier gets the same access) -- those
 * are Vocabulary-specific concepts this first pass does not invent for a different exercise type uninvited; see
 * docs/entitlements.md.
 */
class ReadingLessonService(
    private val api: ReadingApi,
    private val meanings: MeaningProvider,
    private val languages: suspend () -> LessonLanguages,
    /** A random offset in [range], so a fresh session is not the exact same sentences every time. Overridden in
     * tests for a deterministic pick. */
    private val randomOffset: (range: IntRange) -> Int = { it.random() },
) {
    /** A fresh set of [SESSION_SIZE] sentences, starting from a random point in the language's reading list. */
    suspend fun start(): ReadingSessionState {
        val learning = languages().learning
        val code = learning.code
        val probe = api.fetchPage(code, 0, 1)
        val total = (probe as? ReadingPageResult.Success)?.total ?: return ReadingSessionState.Unavailable
        if (total == 0) return ReadingSessionState.Unavailable

        val size = SESSION_SIZE.coerceAtMost(total).coerceAtMost(MAX_PAGE_SIZE)
        val maxOffset = total - size
        val offset = if (maxOffset > 0) randomOffset(0..maxOffset) else 0
        val page = api.fetchPage(code, offset, size)
        val sentences = (page as? ReadingPageResult.Success)?.sentences ?: return ReadingSessionState.Unavailable
        if (sentences.isEmpty()) return ReadingSessionState.Unavailable
        return ReadingSessionState.InProgress(sentences.map { ReadingCard(it, translation = null) }, position = 0, language = learning)
    }

    /** The learner asked to see the current sentence's meaning. Fetched once per sentence; asking again while it is
     * already known (or still being looked up) is a no-op. */
    suspend fun reveal(state: ReadingSessionState.InProgress): ReadingSessionState.InProgress {
        if (state.current.translation != null) return state
        val langs = languages()
        val translation = meanings.meaningOf(state.current.sentence, langs.learning, langs.native).orEmpty()
        val cards = state.cards.toMutableList().apply { this[state.position] = this[state.position].copy(translation = translation) }
        return state.copy(cards = cards)
    }

    /** The learner moved past the current sentence. */
    fun next(state: ReadingSessionState.InProgress): ReadingSessionState =
        if (state.isLast) ReadingSessionState.Finished else state.copy(position = state.position + 1)
}
