package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.entitlements.CurriculumAccess
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
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

/**
 * The curriculum languages open to the learner right now: [current] is the one shown, [available] is everything they
 * could switch to (more than one only for Premium; see [EntitlementService.canPickAnyLearningLanguage]).
 */
data class CurriculumLanguageChoice(val current: Language, val available: List<Language>)

/** What the Standard/Premium lessons screen shows. */
sealed interface CurriculumSessionState {
    data object Loading : CurriculumSessionState

    /** [position] is the card currently shown. */
    data class InProgress(
        val languages: CurriculumLanguageChoice,
        val tier: Tier,
        val cards: List<CurriculumCard>,
        val position: Int,
        val goalToday: Int,
    ) : CurriculumSessionState {
        val current: CurriculumCard get() = cards[position.coerceIn(0, cards.lastIndex)]
        val isLast: Boolean get() = position == cards.lastIndex
    }

    /** Today's goal (including anything rolled over from earlier) is met; more becomes available tomorrow. */
    data class DoneForToday(val languages: CurriculumLanguageChoice, val tier: Tier, val goalToday: Int) : CurriculumSessionState

    /** The FREE trial's word count has been reached; [limit] is how many it allowed. */
    data class TrialLimitReached(val limit: Int) : CurriculumSessionState

    /** The curriculum could not be fetched right now (offline, or the backend is unreachable). */
    data object Unavailable : CurriculumSessionState
}

/**
 * The curriculum: a large, shared, online word list per language (see CurriculumApi) that Standard and Premium
 * lessons are drawn from, separate from and in addition to the free daily micro-lesson drawn from real conversations
 * (see [com.alterlingua.app.learning.lessons.LessonService]). A session is today's remaining words (the daily goal
 * plus any backlog, see [CurriculumProgressService]); finishing a card records it in the Personal Language Map, the
 * same mastery signal a conversation-derived lesson card gives, so a word learned here is recognised later in
 * conversation too.
 *
 * Premium may pick any language it is learning (not just the active target); Standard and the FREE trial are always
 * the active target language only (CLAUDE.md section 6.21's per-language independence, applied to this feature).
 */
class CurriculumLessonService(
    private val api: CurriculumApi,
    private val meanings: MeaningProvider,
    private val progress: CurriculumProgressService,
    private val entitlements: EntitlementService,
    private val map: LanguageMapService,
    private val languages: suspend () -> LessonLanguages,
    /** Every language the learner is learning (the active target plus any other it has recorded a level for). */
    private val learningLanguages: suspend () -> List<Language> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * A fresh session for [languageOverride] if given and allowed (Premium, and a language the learner is actually
     * learning), otherwise the active target language. Nothing here is saved beyond the progress counters.
     */
    suspend fun today(languageOverride: Language? = null): CurriculumSessionState {
        val langs = languages()
        val tier = entitlements.tier()
        val canPickLanguage = entitlements.canPickAnyLearningLanguage(tier)
        val available = if (canPickLanguage) learningLanguages() else emptyList()
        val learning = languageOverride?.takeIf { canPickLanguage && it in available } ?: langs.learning
        val effective = LessonLanguages(learning, langs.native)
        val code = learning.code
        // Only Premium ever sees more than one entry here: that is what makes the language picker appear at all.
        val choice = CurriculumLanguageChoice(learning, available.ifEmpty { listOf(learning) })

        val access = entitlements.curriculumAccess(code)
        if (access is CurriculumAccess.TrialLimitReached) return CurriculumSessionState.TrialLimitReached(access.limit)

        val current = progress.progressToday(code)
        val goal = progress.dailyGoal().coerceAtMost(entitlements.maxCurriculumDailyGoal(tier))
        val remaining = current.remainingToday(goal)
        if (remaining <= 0) return CurriculumSessionState.DoneForToday(choice, tier, goal)

        val page = api.fetchPage(code, current.cursor, remaining)
        val words = (page as? CurriculumPageResult.Success)?.words ?: return CurriculumSessionState.Unavailable
        // The whole list has been learned (a large but finite list): nothing more until it is extended.
        if (words.isEmpty()) return CurriculumSessionState.DoneForToday(choice, tier, goal)

        return CurriculumSessionState.InProgress(choice, tier, buildCards(words, effective), position = 0, goalToday = goal)
    }

    /** The learner marked the current card as learned: records it in the Personal Language Map and moves on. */
    suspend fun next(state: CurriculumSessionState.InProgress): CurriculumSessionState {
        val language = state.languages.current
        val card = state.current
        val key = UnitKey(language.code, card.term.lowercase(Locale.ROOT), UnitType.WORD)
        map.recordLessonEncounter(key, card.term, clock())
        progress.recordCompleted(language.code)
        return if (state.isLast) {
            val goal = progress.dailyGoal().coerceAtMost(entitlements.maxCurriculumDailyGoal(state.tier))
            CurriculumSessionState.DoneForToday(state.languages, state.tier, goal)
        } else {
            state.copy(position = state.position + 1)
        }
    }

    private suspend fun buildCards(words: List<String>, langs: LessonLanguages): List<CurriculumCard> = coroutineScope {
        words.map { word -> async { CurriculumCard(word, meanings.meaningOf(word, langs.learning, langs.native)) } }.awaitAll()
    }
}
