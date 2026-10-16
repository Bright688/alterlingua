package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.entitlements.CurriculumAccess
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.MasteryStatus
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
data class CurriculumCard(val term: String, val meaning: String?) {
    /** This word's identity in the Personal Language Map, for [languageCode] (the language it is shown in). */
    fun key(languageCode: String): UnitKey = UnitKey(languageCode, term.lowercase(Locale.ROOT), UnitType.WORD)
}

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

    /**
     * A specific unit was practiced to completion (The Forge: browsing a level/unit directly, not today's
     * auto-advancing session) -- never affects the daily goal or cursor, so this is deliberately distinct from
     * [DoneForToday], which carries "today" framing that would be wrong here.
     */
    data class UnitPracticeComplete(val level: CefrLevel, val unitNumber: Int) : CurriculumSessionState

    /** The curriculum could not be fetched right now (offline, or the backend is unreachable). */
    data object Unavailable : CurriculumSessionState
}

/** One CEFR-proxy level, as shown in The Forge's level list: whether it is where the learner's own cursor is. */
data class ForgeLevelSummary(val range: CurriculumLevelRange, val isCurrent: Boolean)

/** What The Forge's level list looks like for the resolved language. */
sealed interface ForgeState {
    data object Loading : ForgeState
    data class Levels(val languages: CurriculumLanguageChoice, val tier: Tier, val levels: List<ForgeLevelSummary>) : ForgeState
    data object Unavailable : ForgeState
}

/** One unit within an opened level: its own word list (so practicing it needs no second fetch) and how many of
 * those words are already MASTERED in the Personal Language Map. */
data class ForgeUnitSummary(val range: CurriculumUnitRange, val words: List<String>, val masteredCount: Int)

/** What The Forge shows once a level has been opened. */
sealed interface ForgeLevelState {
    data object Loading : ForgeLevelState
    data class Loaded(val level: CurriculumLevelRange, val units: List<ForgeUnitSummary>) : ForgeLevelState
    data object Unavailable : ForgeLevelState
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
private const val MAX_PAGE_SIZE = 100 // matches the backend's own cap (backend/app/vocabulary/router.py: le=100)

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
    private data class Resolved(val learning: Language, val effective: LessonLanguages, val tier: Tier, val choice: CurriculumLanguageChoice)

    /** Which language is actually in play (Premium may override; Standard/FREE always get the active target). */
    private suspend fun resolve(languageOverride: Language?): Resolved {
        val langs = languages()
        val tier = entitlements.tier()
        val canPickLanguage = entitlements.canPickAnyLearningLanguage(tier)
        val available = if (canPickLanguage) learningLanguages() else emptyList()
        val learning = languageOverride?.takeIf { canPickLanguage && it in available } ?: langs.learning
        // Only Premium ever sees more than one entry here: that is what makes the language picker appear at all.
        val choice = CurriculumLanguageChoice(learning, available.ifEmpty { listOf(learning) })
        return Resolved(learning, LessonLanguages(learning, langs.native), tier, choice)
    }

    /**
     * A fresh session for [languageOverride] if given and allowed (Premium, and a language the learner is actually
     * learning), otherwise the active target language. Nothing here is saved beyond the progress counters.
     */
    suspend fun today(languageOverride: Language? = null): CurriculumSessionState {
        val (learning, effective, tier, choice) = resolve(languageOverride)
        val code = learning.code

        val access = entitlements.curriculumAccess(code)
        if (access is CurriculumAccess.TrialLimitReached) return CurriculumSessionState.TrialLimitReached(access.limit)

        val current = progress.progressToday(code)
        val goal = progress.dailyGoal().coerceAtMost(entitlements.maxCurriculumDailyGoal(tier))
        val remaining = current.remainingToday(goal)
        if (remaining <= 0) return CurriculumSessionState.DoneForToday(choice, tier, goal)

        val collected = mutableListOf<String>()
        var probe = current.cursor
        var skippedMastered = 0
        while (collected.size < remaining) {
            val fetchLimit = ((remaining - collected.size) * 2 + 10).coerceAtMost(MAX_PAGE_SIZE)
            val page = api.fetchPage(code, probe, fetchLimit)
            val words = (page as? CurriculumPageResult.Success)?.words
                ?: return if (collected.isEmpty()) {
                    CurriculumSessionState.Unavailable
                } else {
                    if (skippedMastered > 0) progress.recordSkipped(code, skippedMastered)
                    CurriculumSessionState.InProgress(choice, tier, buildCards(collected, effective), position = 0, goalToday = goal)
                }
            // The whole list has been learned (a large but finite list): nothing more until it is extended.
            if (words.isEmpty()) break
            for (word in words) {
                probe++
                // A word already MASTERED (any tier -- CLAUDE.md's "demonstrated mastery" recommendation) is skipped
                // for free: it does not cost today's goal, and the cursor moves past it without showing it again.
                if (map.item(UnitKey(code, word.lowercase(Locale.ROOT), UnitType.WORD))?.masteryState == MasteryStatus.MASTERED) {
                    skippedMastered++
                } else {
                    collected += word
                    if (collected.size == remaining) break
                }
            }
        }
        if (skippedMastered > 0) progress.recordSkipped(code, skippedMastered)
        if (collected.isEmpty()) return CurriculumSessionState.DoneForToday(choice, tier, goal)

        return CurriculumSessionState.InProgress(choice, tier, buildCards(collected, effective), position = 0, goalToday = goal)
    }

    /**
     * The learner moved past the current card, having said whether they already knew it: records the lesson
     * encounter (the same mastery signal a conversation-derived lesson card gives) and, separately, the recognition
     * itself ([alreadyKnew] — the same self-reported recall signal Adaptive mode's MASTERED gate needs, that until
     * now only the daily micro-lesson's own recall check supplied).
     */
    suspend fun next(state: CurriculumSessionState.InProgress, alreadyKnew: Boolean): CurriculumSessionState {
        val language = state.languages.current
        val card = state.current
        val key = card.key(language.code)
        map.recordLessonEncounter(key, card.term, clock())
        map.recordRecognition(key, alreadyKnew, card.term, clock())
        progress.recordCompleted(language.code)
        return if (state.isLast) {
            val goal = progress.dailyGoal().coerceAtMost(entitlements.maxCurriculumDailyGoal(state.tier))
            CurriculumSessionState.DoneForToday(state.languages, state.tier, goal)
        } else {
            state.copy(position = state.position + 1)
        }
    }

    /**
     * The Forge's level list (CLAUDE.md's pasted "Final decision": one shared curriculum, organized as
     * Levels -> Units -> Lessons, for every plan). The banding is a frequency-based CEFR proxy (see [CurriculumStructure]);
     * only levels the language's word list actually has words for are returned.
     */
    suspend fun forge(languageOverride: Language? = null): ForgeState {
        val (learning, _, tier, choice) = resolve(languageOverride)
        val page = api.fetchPage(learning.code, 0, 1) // one word is enough to read the list's total size
        val total = (page as? CurriculumPageResult.Success)?.total ?: return ForgeState.Unavailable
        val cursor = progress.progressToday(learning.code).cursor
        val levels = CurriculumStructure.levels(total).map { range ->
            ForgeLevelSummary(range, isCurrent = cursor in range.startIndex until range.endIndex)
        }
        return ForgeState.Levels(choice, tier, levels)
    }

    /** One level's units, each with its own word list and how many of those words are already mastered. */
    suspend fun forgeLevel(range: CurriculumLevelRange, languageOverride: Language? = null): ForgeLevelState {
        val (learning, _, _, _) = resolve(languageOverride)
        val code = learning.code
        // The backend caps a single page at MAX_PAGE_SIZE words (router.py: Query(..., le=100)); a level can be
        // larger than that (e.g. 500 words), so it is fetched in pages and concatenated, not in one request.
        val words = mutableListOf<String>()
        var offset = range.startIndex
        while (offset < range.endIndex) {
            val page = api.fetchPage(code, offset, (range.endIndex - offset).coerceAtMost(MAX_PAGE_SIZE))
            val pageWords = (page as? CurriculumPageResult.Success)?.words ?: return ForgeLevelState.Unavailable
            if (pageWords.isEmpty()) break // the whole list has been learned: nothing more until it is extended
            words += pageWords
            offset += pageWords.size
        }
        val units = CurriculumStructure.units(range).map { unitRange ->
            val unitWords = words.subList((unitRange.startIndex - range.startIndex).coerceIn(0, words.size), (unitRange.endIndex - range.startIndex).coerceIn(0, words.size))
            val mastered = unitWords.count { map.item(UnitKey(code, it.lowercase(Locale.ROOT), UnitType.WORD))?.masteryState == MasteryStatus.MASTERED }
            ForgeUnitSummary(unitRange, unitWords, mastered)
        }
        return ForgeLevelState.Loaded(range, units)
    }

    /**
     * Opens [unit] as a practice session: its own words (already fetched by [forgeLevel], so no second request),
     * shown with the same flashcard flow as today's session. Still respects the FREE trial limit -- browsing
     * directly to a later unit must not bypass it -- but does not touch the saved cursor or daily goal; see
     * [practiceNext].
     */
    suspend fun practiceUnit(unit: ForgeUnitSummary, languageOverride: Language? = null): CurriculumSessionState {
        val (_, effective, tier, choice) = resolve(languageOverride)
        val access = entitlements.curriculumAccess(choice.current.code)
        if (access is CurriculumAccess.TrialLimitReached) return CurriculumSessionState.TrialLimitReached(access.limit)
        if (unit.words.isEmpty()) return CurriculumSessionState.UnitPracticeComplete(unit.range.level, unit.range.unitNumber)
        return CurriculumSessionState.InProgress(choice, tier, buildCards(unit.words, effective), position = 0, goalToday = unit.words.size)
    }

    /**
     * Advances a unit-practice session (see [practiceUnit]): records the exact same Personal Language Map signal as
     * [next], but deliberately never calls [CurriculumProgressService.recordCompleted] -- practicing a unit the
     * learner has already reached, or is previewing ahead of where they are, must never cost or grant anything
     * against today's goal.
     */
    suspend fun practiceNext(state: CurriculumSessionState.InProgress, unit: CurriculumUnitRange, alreadyKnew: Boolean): CurriculumSessionState {
        val language = state.languages.current
        val card = state.current
        val key = card.key(language.code)
        map.recordLessonEncounter(key, card.term, clock())
        map.recordRecognition(key, alreadyKnew, card.term, clock())
        return if (state.isLast) {
            CurriculumSessionState.UnitPracticeComplete(unit.level, unit.unitNumber)
        } else {
            state.copy(position = state.position + 1)
        }
    }

    private suspend fun buildCards(words: List<String>, langs: LessonLanguages): List<CurriculumCard> = coroutineScope {
        words.map { word -> async { CurriculumCard(word, meanings.meaningOf(word, langs.learning, langs.native)) } }.awaitAll()
    }
}
