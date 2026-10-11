package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.entitlements.CurriculumAccess
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.LanguageMapService
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One word of a curriculum session and what it means in the learner's own language (null if it could not be found). */
data class CurriculumCard(val term: String, val meaning: String?)

/** What the standard-curriculum screen shows. */
sealed interface CurriculumSessionState {
    data object Loading : CurriculumSessionState

    /** [position] is the card currently shown. */
    data class InProgress(val language: Language, val cards: List<CurriculumCard>, val position: Int, val goalToday: Int) : CurriculumSessionState {
        val current: CurriculumCard get() = cards[position.coerceIn(0, cards.lastIndex)]
        val isLast: Boolean get() = position == cards.lastIndex
    }

    /** Today's goal (including anything rolled over from earlier) is met; more becomes available tomorrow. */
    data class DoneForToday(val language: Language, val goalToday: Int) : CurriculumSessionState

    /** The FREE trial's word count has been reached; [limit] is how many it allowed. */
    data class TrialLimitReached(val limit: Int) : CurriculumSessionState

    /** The curriculum could not be fetched right now (offline, or the backend is unreachable). */
    data object Unavailable : CurriculumSessionState
}

/**
 * The standard curriculum: a large, shared, online word list per language (see CurriculumApi), separate from and in
 * addition to the daily micro-lesson drawn from real conversations (see
 * [com.alterlingua.app.learning.lessons.LessonService]). A session is today's remaining words (the daily goal plus
 * any backlog, see [CurriculumProgressService]); finishing a card records it in the Personal Language Map, the same
 * mastery signal a conversation-derived lesson card gives, so a word learned here is recognised later in conversation
 * too.
 */
class CurriculumLessonService(
    private val api: CurriculumApi,
    private val meanings: MeaningProvider,
    private val progress: CurriculumProgressService,
    private val entitlements: EntitlementService,
    private val map: LanguageMapService,
    private val languages: suspend () -> LessonLanguages,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A fresh session for whatever the learner is currently learning. Nothing here is saved beyond the progress counters. */
    suspend fun today(): CurriculumSessionState {
        val langs = languages()
        val code = langs.learning.code
        val access = entitlements.curriculumAccess(code)
        if (access is CurriculumAccess.TrialLimitReached) return CurriculumSessionState.TrialLimitReached(access.limit)

        val current = progress.progressToday(code)
        val goal = progress.dailyGoal()
        val remaining = current.remainingToday(goal)
        if (remaining <= 0) return CurriculumSessionState.DoneForToday(langs.learning, goal)

        val page = api.fetchPage(code, current.cursor, remaining)
        val words = (page as? CurriculumPageResult.Success)?.words ?: return CurriculumSessionState.Unavailable
        // The whole list has been learned (a large but finite list): nothing more until it is extended.
        if (words.isEmpty()) return CurriculumSessionState.DoneForToday(langs.learning, goal)

        return CurriculumSessionState.InProgress(langs.learning, buildCards(words, langs), position = 0, goalToday = goal)
    }

    /** The learner marked the current card as learned: records it in the Personal Language Map and moves on. */
    suspend fun next(state: CurriculumSessionState.InProgress): CurriculumSessionState {
        val langs = languages()
        val card = state.current
        val key = UnitKey(langs.learning.code, card.term.lowercase(Locale.ROOT), UnitType.WORD)
        map.recordLessonEncounter(key, card.term, clock())
        progress.recordCompleted(langs.learning.code)
        return if (state.isLast) {
            val goal = progress.dailyGoal()
            CurriculumSessionState.DoneForToday(state.language, goal)
        } else {
            state.copy(position = state.position + 1)
        }
    }

    private suspend fun buildCards(words: List<String>, langs: LessonLanguages): List<CurriculumCard> = coroutineScope {
        words.map { word -> async { CurriculumCard(word, meanings.meaningOf(word, langs.learning, langs.native)) } }.awaitAll()
    }
}
