package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.map.LanguageMapService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

/** The two languages a lesson needs: the one being learned, and the learner's own (for meanings). */
data class LessonLanguages(val learning: Language, val native: Language)

/**
 * The daily micro-lesson: chooses about three items from the Personal Language Map, builds their cards, keeps the day's
 * lesson, and records the learner's progress through it as a mastery signal (one lesson encounter per card).
 *
 * The lesson is made once per local day and language. Nothing about the messages the items came from is used beyond what the
 * map holds (counts and the kind of message), so a lesson can never show a private message.
 */
class LessonService(
    private val map: LanguageMapService,
    private val meanings: MeaningProvider,
    private val store: DailyLessonStore,
    private val languages: suspend () -> LessonLanguages,
    private val selector: LessonSelector = LessonSelector(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val meaningTimeoutMillis: Long = 10_000,
    /** Counts a finished lesson for the pilot metrics (a number only). */
    private val progress: com.alterlingua.app.learning.progress.ProgressLog? = null,
) {
    private val lock = Mutex()

    /** Today's lesson: the saved one, or a new one if there is none for today in the language being learned. */
    suspend fun today(): LessonState = lock.withLock { current(languages()) }

    /** The learner finished the card they were on: record it, and move on (or finish the lesson). */
    suspend fun next(): LessonState = lock.withLock {
        val langs = languages()
        val state = current(langs)
        val lesson = (state as? LessonState.InProgress)?.lesson ?: return state
        val card = lesson.cards[lesson.position]
        if (lesson.position !in lesson.encountered) {
            map.recordLessonEncounter(card.key, card.term, clock()) // the mastery signal
        }
        val encountered = lesson.encountered + lesson.position
        val finished = lesson.position >= lesson.cards.lastIndex
        val updated = lesson.copy(position = if (finished) lesson.position else lesson.position + 1, encountered = encountered, completed = finished)
        store.save(updated)
        if (finished) {
            try {
                progress?.lessonCompleted(langs.learning.code)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            LessonState.Completed(updated)
        } else {
            LessonState.InProgress(updated)
        }
    }

    /** Back to the previous card. Going back records nothing. */
    suspend fun previous(): LessonState = lock.withLock {
        val state = current(languages())
        val lesson = (state as? LessonState.InProgress)?.lesson ?: return state
        if (lesson.position == 0) return state
        val updated = lesson.copy(position = lesson.position - 1)
        store.save(updated)
        LessonState.InProgress(updated)
    }

    private suspend fun current(langs: LessonLanguages): LessonState {
        val date = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate().toString()
        val saved = store.load()
        if (saved != null && saved.date == date && saved.language == langs.learning.code) {
            return if (saved.completed) LessonState.Completed(saved) else LessonState.InProgress(saved)
        }
        val created = create(date, langs) ?: return LessonState.NoLesson
        store.save(created)
        return LessonState.InProgress(created)
    }

    private suspend fun create(date: String, langs: LessonLanguages): DailyLesson? {
        val now = clock()
        val chosen = selector.select(map.items(langs.learning.code), now).chosen
        if (chosen.isEmpty()) return null
        val cards = coroutineScope {
            chosen.map { candidate ->
                async {
                    val item = candidate.item
                    val meaning = item.meaning ?: withTimeoutOrNull(meaningTimeoutMillis) { meanings.meaningOf(item.displayForm, langs.learning, langs.native) }
                    if (meaning != null && item.meaning == null) map.setMeaning(item.key, meaning, langs.native.code) // keep it: next time it is free
                    LessonCard(
                        key = item.key,
                        term = item.displayForm,
                        kind = if (item.type == com.alterlingua.app.learning.engine.UnitType.WORD) CardKind.WORD_CARD else CardKind.PHRASE_CARD,
                        meaning = meaning,
                        meaningLanguage = meaning?.let { langs.native.code },
                        context = LessonContext(item.exposureCount, item.lastContext, item.lastSeen, item.helpRequests, reasonsFor(candidate)),
                        masteryState = item.masteryState,
                    )
                }
            }.awaitAll()
        }
        return DailyLesson(date, langs.learning.code, cards)
    }

    /** Up to three short reasons, in the learner's words, from the strongest signals. */
    private fun reasonsFor(candidate: LessonCandidate): List<LessonReason> = candidate.signals
        .filter { it.points > 0 && it.kind != SignalKind.FREQUENCY }
        .sortedByDescending { it.points }
        .mapNotNull { learningReasonFor(it) }
        .distinct()
        .take(3)
}
