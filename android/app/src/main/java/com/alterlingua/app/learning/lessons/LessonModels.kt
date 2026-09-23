package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey

/** A word gets a word card; a phrase or fixed expression gets a phrase card. */
enum class CardKind { WORD_CARD, PHRASE_CARD }

/**
 * Where the learner met the item, without any of the message: how often, what kind of message last, when, and why the
 * item was chosen for today. Never a sender, never the text around it.
 */
/** The learner-facing reasons an item is in today's lesson. Saved by name, so they can be worded in any language. */
enum class LessonReason { ASKED_TRANSLATION, USEFUL, RECENT, STILL_LEARNING, NEW, WORTH_REVIEW, TIME_TO_REVIEW }

data class LessonContext(
    val timesMet: Int,
    val lastMetIn: InteractionKind?,
    val lastSeenMillis: Long,
    val helpRequests: Int,
    /** Why this item was chosen, as codes; the screen turns them into words in the app language. */
    val reasons: List<LessonReason>,
)

data class LessonCard(
    val key: UnitKey,
    /** The word or phrase as the learner first met it. */
    val term: String,
    val kind: CardKind,
    /** What it means in the learner's language; null if it could not be found (for example when offline). */
    val meaning: String?,
    val meaningLanguage: String?,
    val context: LessonContext,
    val masteryState: MasteryStatus,
)

/**
 * The day's lesson. It is made once per day and language and then kept, so reopening the app shows the same lesson and
 * where the learner had got to. [encountered] lists the cards already recorded as a lesson encounter, so going back and
 * forward never counts a card twice.
 */
data class DailyLesson(
    val date: String,
    val language: String,
    val cards: List<LessonCard>,
    val position: Int = 0,
    val encountered: Set<Int> = emptySet(),
    val completed: Boolean = false,
) {
    val current: LessonCard get() = cards[position.coerceIn(0, cards.lastIndex)]
}

sealed interface LessonState {
    /** Nothing has been met yet that is worth a lesson. The lesson appears once messages have been translated. */
    data object NoLesson : LessonState

    data class InProgress(val lesson: DailyLesson) : LessonState

    data class Completed(val lesson: DailyLesson) : LessonState
}
