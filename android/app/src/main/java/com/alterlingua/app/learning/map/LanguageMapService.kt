package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.LanguageMapSummary
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.ExposureStore
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.progress.MasterySnapshot
import com.alterlingua.app.learning.progress.ProgressLog
import com.alterlingua.app.learning.engine.UnitKey
import kotlinx.coroutines.flow.Flow

/**
 * The Personal Language Map: what the learner has met and how well they know it, per language.
 *
 * Every change (an exposure from a translated message, a translation-help request, a lesson encounter, a recognition
 * right or wrong) updates one item's counts and then recomputes its mastery with the [MasteryCalculator]. The map holds
 * units and counts only, never messages.
 */
class LanguageMapService(
    private val store: LanguageMapStore,
    private val calculator: MasteryCalculator = MasteryCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the Progress screen's daily counts go (numbers only). Optional: the map works without it. */
    private val progress: ProgressLog? = null,
    /** A word met at or above this state counts as known when measuring translation dependence (the Adaptive threshold). */
    private val independentAt: MasteryStatus = MasteryStatus.MASTERED,
) {
    /** Adds the units of a learning event: each one is one more exposure. */
    suspend fun recordLearningEvent(event: LearningEvent) {
        // Measure the words before this event changes them: how many did the learner already know when they met them?
        var met = 0
        var assisted = 0
        var new = 0
        if (progress != null) {
            for (candidate in event.candidates.filter { it.type == UnitType.WORD }) {
                val existing = store.get(candidate.key)
                met++
                if (existing == null) new++
                val state = existing?.let { calculator.evaluate(it.evidence, clock()).state }
                if (state == null || state.ordinal < independentAt.ordinal) assisted++
            }
        }
        for (candidate in event.candidates) {
            change(candidate.key, candidate.surface, candidate.meaningLanguage) { item ->
                item.copy(
                    exposureCount = item.exposureCount + candidate.exposure.count,
                    firstSeen = if (item.firstSeen == 0L) candidate.exposure.firstSeenMillis else minOf(item.firstSeen, candidate.exposure.firstSeenMillis),
                    lastSeen = maxOf(item.lastSeen, candidate.exposure.lastSeenMillis),
                    meaning = item.meaning ?: candidate.meaning,
                    usefulness = maxOf(item.usefulness, candidate.usefulness.score),
                    lastContext = if (candidate.exposure.lastSeenMillis >= item.lastSeen) event.kind else item.lastContext,
                )
            }
        }
        note(event.learningLanguage) { wordsMet(event.learningLanguage, met, assisted, new) }
    }

    /** The user asked for a translation of this unit: a sign they did not know it. */
    suspend fun recordHelpRequest(key: UnitKey, displayForm: String = key.normalized, atMillis: Long = clock()): LanguageMapItem? {
        val changed = change(key, displayForm, null) { it.copy(helpRequests = it.helpRequests + 1, lastHelpAt = maxOf(it.lastHelpAt, atMillis), lastSeen = maxOf(it.lastSeen, atMillis), firstSeen = firstSeenOr(it, atMillis)) }
        note(key.language) { helpRequest(key.language) }
        return changed
    }

    /**
     * The learner practised saying the unit. [understood] is true when the speech recognizer heard it as that unit. A miss
     * is counted as an attempt but costs nothing: trying is how pronunciation is learned.
     */
    suspend fun recordPronunciation(key: UnitKey, understood: Boolean, displayForm: String = key.normalized, atMillis: Long = clock()): LanguageMapItem? {
        val changed = change(key, displayForm, null) {
            it.copy(
                pronunciationTries = it.pronunciationTries + 1,
                pronunciationGood = it.pronunciationGood + if (understood) 1 else 0,
                lastSeen = maxOf(it.lastSeen, atMillis),
                firstSeen = firstSeenOr(it, atMillis),
            )
        }
        note(key.language) { practice(key.language, understood) }
        return changed
    }

    /** The unit came up in a lesson. */
    suspend fun recordLessonEncounter(key: UnitKey, displayForm: String = key.normalized, atMillis: Long = clock()): LanguageMapItem? {
        val changed = change(key, displayForm, null) {
            it.copy(
                lessonEncounters = it.lessonEncounters + 1,
                lastSeen = maxOf(it.lastSeen, atMillis),
                lastLessonAt = maxOf(it.lastLessonAt, atMillis),
                firstSeen = firstSeenOr(it, atMillis),
            )
        }
        note(key.language) { lessonCard(key.language) }
        return changed
    }

    /** The learner recognised the unit ([correct]) or did not. */
    suspend fun recordRecognition(key: UnitKey, correct: Boolean, displayForm: String = key.normalized, atMillis: Long = clock()) =
        change(key, displayForm, null) {
            it.copy(
                correctRecognitions = it.correctRecognitions + if (correct) 1 else 0,
                incorrectRecognitions = it.incorrectRecognitions + if (correct) 0 else 1,
                lastSeen = maxOf(it.lastSeen, atMillis),
                firstSeen = firstSeenOr(it, atMillis),
            )
        }

    /** Fills in what a unit means, in the learner's language. Does not change mastery. */
    suspend fun setMeaning(key: UnitKey, meaning: String, meaningLanguage: String): LanguageMapItem? =
        store.update(key) { it?.copy(meaning = meaning, meaningLanguage = meaningLanguage) }

    suspend fun item(key: UnitKey): LanguageMapItem? = store.get(key)

    suspend fun items(language: String, state: MasteryStatus? = null): List<LanguageMapItem> =
        store.items(language).filter { state == null || it.masteryState == state }

    fun observe(language: String): Flow<List<LanguageMapItem>> = store.observe(language)

    /** How many items of a language are in each state (the shape the Words and Progress screens use). */
    suspend fun summary(language: String): LanguageMapSummary {
        val items = store.items(language)
        fun count(state: MasteryStatus) = items.count { it.masteryState == state }
        return LanguageMapSummary(count(MasteryStatus.UNKNOWN), count(MasteryStatus.LEARNING), count(MasteryStatus.FAMILIAR), count(MasteryStatus.MASTERED))
    }

    /** The mastery of [item] as of [nowMillis], including the fall for time unseen (the stored value is as of its last change). */
    fun evaluate(item: LanguageMapItem, nowMillis: Long = clock()): MasteryEvaluation = calculator.evaluate(item.evidence, nowMillis)

    suspend fun deleteLanguage(language: String) = store.deleteLanguage(language)

    suspend fun deleteAll() = store.deleteAll()

    private suspend fun change(key: UnitKey, displayForm: String, meaningLanguage: String?, apply: (LanguageMapItem) -> LanguageMapItem): LanguageMapItem? {
        var before: MasteryStatus? = null
        val updated = store.update(key) { existing ->
            before = existing?.masteryState
            val base = existing ?: LanguageMapItem(key.language, key.normalized, key.type, displayForm, meaningLanguage = meaningLanguage)
            val changed = apply(base).let { if (it.meaningLanguage == null && meaningLanguage != null) it.copy(meaningLanguage = meaningLanguage) else it }
            val result = calculator.evaluate(changed.evidence, clock())
            changed.copy(masteryScore = result.score, masteryState = result.state)
        }
        if (updated != null) noteStateChange(key.language, before ?: MasteryStatus.UNKNOWN, updated.masteryState)
        return updated
    }

    /**
     * Tells the progress log something happened, then saves today's state counts. Progress is a record about learning; a
     * failure to write it must never stop the learning itself.
     */
    private suspend fun note(language: String, record: suspend ProgressLog.() -> Unit) {
        val log = progress ?: return
        try {
            log.record()
            log.snapshot(language, currentSnapshot(language))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    /** Counts an upward move between states for the pilot metrics. Never lets a failure reach the learning. */
    private suspend fun noteStateChange(language: String, from: MasteryStatus, to: MasteryStatus) {
        val log = progress ?: return
        try {
            log.stateChanged(language, from, to)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    /** The state counts of a language as of now, with the fall for time unseen applied. */
    suspend fun currentSnapshot(language: String): MasterySnapshot {
        val states = store.items(language).map { calculator.evaluate(it.evidence, clock()).state }
        return MasterySnapshot(
            encountered = states.size,
            learning = states.count { it == MasteryStatus.LEARNING },
            familiar = states.count { it == MasteryStatus.FAMILIAR },
            mastered = states.count { it == MasteryStatus.MASTERED },
        )
    }

    private fun firstSeenOr(item: LanguageMapItem, atMillis: Long) = if (item.firstSeen == 0L) atMillis else item.firstSeen
}

/** Lets the learning pipeline write into the map: each event's units become exposures. */
class LanguageMapExposureStore(private val service: LanguageMapService) : ExposureStore {
    override suspend fun record(event: LearningEvent) = service.recordLearningEvent(event)
}
