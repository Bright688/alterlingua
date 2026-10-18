package com.alterlingua.app.learning.writing

import com.alterlingua.app.learning.curriculum.CurriculumApi
import com.alterlingua.app.learning.curriculum.CurriculumPageResult
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One writing prompt: a word from the standard curriculum (see [CurriculumApi]) and its meaning, so the learner
 * knows what to write about without this exercise needing any content of its own. */
data class WritingPrompt(val word: String, val meaning: String?)

/** What the Writing exercise shows. */
sealed interface WritingSessionState {
    data object Loading : WritingSessionState

    /** [position] is the prompt currently shown. */
    data class InProgress(val prompts: List<WritingPrompt>, val position: Int) : WritingSessionState {
        val current: WritingPrompt get() = prompts[position.coerceIn(0, prompts.lastIndex)]
        val isLast: Boolean get() = position == prompts.lastIndex
    }

    data object Finished : WritingSessionState
    data object Unavailable : WritingSessionState
}

private const val SESSION_SIZE = 5
private const val MAX_PAGE_SIZE = 100 // matches the backend's own cap (backend/app/vocabulary/router.py: le=100)

/**
 * Writing practice: a prompt word (from the same standard curriculum Vocabulary already uses -- no separate
 * content exists or is needed) to write a sentence about. Deliberately has no automated grading or model-answer
 * reveal: no such scoring exists anywhere in this app (CLAUDE.md: no AI Learning Engine yet), and faking one would
 * be exactly the fabricated-content problem this app otherwise avoids. What is here is real: a genuine prompt to
 * produce the target language, self-assessed by the learner, the same honest shape as Reading and Listening.
 */
class WritingLessonService(
    private val api: CurriculumApi,
    private val meanings: MeaningProvider,
    private val languages: suspend () -> LessonLanguages,
    private val randomOffset: (range: IntRange) -> Int = { it.random() },
) {
    suspend fun start(): WritingSessionState {
        val langs = languages()
        val code = langs.learning.code
        val probe = api.fetchPage(code, 0, 1)
        val total = (probe as? CurriculumPageResult.Success)?.total ?: return WritingSessionState.Unavailable
        if (total == 0) return WritingSessionState.Unavailable

        val size = SESSION_SIZE.coerceAtMost(total).coerceAtMost(MAX_PAGE_SIZE)
        val maxOffset = total - size
        val offset = if (maxOffset > 0) randomOffset(0..maxOffset) else 0
        val page = api.fetchPage(code, offset, size)
        val words = (page as? CurriculumPageResult.Success)?.words ?: return WritingSessionState.Unavailable
        if (words.isEmpty()) return WritingSessionState.Unavailable

        val prompts = coroutineScope {
            words.map { word -> async { WritingPrompt(word, meanings.meaningOf(word, langs.learning, langs.native)) } }.awaitAll()
        }
        return WritingSessionState.InProgress(prompts, position = 0)
    }

    fun next(state: WritingSessionState.InProgress): WritingSessionState =
        if (state.isLast) WritingSessionState.Finished else state.copy(position = state.position + 1)
}
