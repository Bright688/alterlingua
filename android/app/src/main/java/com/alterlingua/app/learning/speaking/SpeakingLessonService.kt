package com.alterlingua.app.learning.speaking

import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.reading.ReadingApi
import com.alterlingua.app.learning.reading.ReadingPageResult
import com.alterlingua.app.learning.MasteryStatus
import java.util.Locale

/** What the Speaking exercise shows. */
sealed interface SpeakingSessionState {
    data object Loading : SpeakingSessionState

    /** [position] is the sentence currently being practiced. */
    data class InProgress(val cards: List<LessonCard>, val position: Int) : SpeakingSessionState {
        val current: LessonCard get() = cards[position.coerceIn(0, cards.lastIndex)]
        val isLast: Boolean get() = position == cards.lastIndex
    }

    data object Finished : SpeakingSessionState
    data object Unavailable : SpeakingSessionState
}

private const val SESSION_SIZE = 8
private const val MAX_PAGE_SIZE = 50 // matches the backend's own cap (backend/app/lessons/reading/router.py: le=50)

/**
 * Speaking practice: the same real sentences as Reading and Listening (see [ReadingApi]) -- no separate content or
 * backend endpoint of its own -- said aloud through the pronunciation engine that already existed in this codebase
 * ([com.alterlingua.app.learning.pronunciation.PronunciationPractice]) but had never been wired into any screen: on-device
 * text-to-speech for a model, the phone's own microphone to record a repeat, and real on-device speech-recognition
 * feedback (never a fabricated AI score -- see PracticePanel's own comment on this). Each sentence becomes a
 * [LessonCard] with no Personal Language Map history of its own (a fresh UNKNOWN card every time): this is drill
 * practice, not mastery tracking, the same "no daily goal or mastery" scope decision already stated for Reading.
 */
class SpeakingLessonService(
    private val api: ReadingApi,
    private val languages: suspend () -> LessonLanguages,
    private val randomOffset: (range: IntRange) -> Int = { it.random() },
) {
    suspend fun start(): SpeakingSessionState {
        val code = languages().learning.code
        val probe = api.fetchPage(code, 0, 1)
        val total = (probe as? ReadingPageResult.Success)?.total ?: return SpeakingSessionState.Unavailable
        if (total == 0) return SpeakingSessionState.Unavailable

        val size = SESSION_SIZE.coerceAtMost(total).coerceAtMost(MAX_PAGE_SIZE)
        val maxOffset = total - size
        val offset = if (maxOffset > 0) randomOffset(0..maxOffset) else 0
        val page = api.fetchPage(code, offset, size)
        val sentences = (page as? ReadingPageResult.Success)?.sentences ?: return SpeakingSessionState.Unavailable
        if (sentences.isEmpty()) return SpeakingSessionState.Unavailable

        val cards = sentences.map { sentence ->
            LessonCard(
                key = UnitKey(code, sentence.lowercase(Locale.ROOT), UnitType.PHRASE),
                term = sentence,
                kind = CardKind.PHRASE_CARD,
                meaning = null,
                meaningLanguage = null,
                context = LessonContext(timesMet = 0, lastMetIn = null, lastSeenMillis = 0L, helpRequests = 0, reasons = emptyList()),
                masteryState = MasteryStatus.UNKNOWN,
            )
        }
        return SpeakingSessionState.InProgress(cards, position = 0)
    }

    fun next(state: SpeakingSessionState.InProgress): SpeakingSessionState =
        if (state.isLast) SpeakingSessionState.Finished else state.copy(position = state.position + 1)
}
