package com.alterlingua.app.learning.progress

import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import java.time.Instant
import java.time.ZoneId

/**
 * Writes the daily counts that the Progress screen is drawn from. Everything it records is a number; it never sees a
 * message or a word (CLAUDE.md 25).
 */
class ProgressLog(
    private val store: ProgressStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** The assistance mode selected right now, so each action can be counted under it. */
    private val mode: suspend () -> AssistanceMode = { AssistanceMode.FULL_SUPPORT },
) {
    private fun today(): String = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toString()

    /** Words met in real communication: how many, how many were not mastered, how many were new. */
    suspend fun wordsMet(language: String, met: Int, assisted: Int, new: Int) {
        if (met <= 0) return
        store.update(today(), language) { it.copy(wordsMet = it.wordsMet + met, wordsAssisted = it.wordsAssisted + assisted, newWords = it.newWords + new) }
    }

    suspend fun helpRequest(language: String) {
        val selected = mode()
        store.update(today(), language) { it.copy(helpRequests = it.helpRequests + 1).underMode(selected) }
    }

    /** A translation the user received or used: one count, by kind and by the mode selected. Never its text. */
    suspend fun translation(language: String, kind: InteractionKind) {
        val selected = mode()
        store.update(today(), language) {
            when (kind) {
                InteractionKind.OUTGOING_TEXT -> it.copy(translationsOutgoingText = it.translationsOutgoingText + 1)
                InteractionKind.OUTGOING_VOICE -> it.copy(translationsOutgoingVoice = it.translationsOutgoingVoice + 1)
                InteractionKind.INCOMING_MESSAGE -> it.copy(translationsIncomingText = it.translationsIncomingText + 1)
                InteractionKind.INCOMING_VOICE -> it.copy(translationsIncomingVoice = it.translationsIncomingVoice + 1)
            }.underMode(selected)
        }
    }

    /** A word moved up: counts each boundary it crossed (UNKNOWN to LEARNING, LEARNING to FAMILIAR, FAMILIAR to MASTERED). */
    suspend fun stateChanged(language: String, from: MasteryStatus, to: MasteryStatus) {
        if (to.ordinal <= from.ordinal) return
        fun crossed(low: MasteryStatus, high: MasteryStatus) = from.ordinal <= low.ordinal && to.ordinal >= high.ordinal
        val toLearning = if (crossed(MasteryStatus.UNKNOWN, MasteryStatus.LEARNING)) 1 else 0
        val toFamiliar = if (crossed(MasteryStatus.LEARNING, MasteryStatus.FAMILIAR)) 1 else 0
        val toMastered = if (crossed(MasteryStatus.FAMILIAR, MasteryStatus.MASTERED)) 1 else 0
        store.update(today(), language) { it.copy(toLearning = it.toLearning + toLearning, toFamiliar = it.toFamiliar + toFamiliar, toMastered = it.toMastered + toMastered) }
    }

    suspend fun lessonCompleted(language: String) = store.update(today(), language) { it.copy(lessonsCompleted = it.lessonsCompleted + 1) }

    private fun DailyActivity.underMode(selected: AssistanceMode) = when (selected) {
        AssistanceMode.FULL_SUPPORT -> copy(actionsFullSupport = actionsFullSupport + 1)
        AssistanceMode.ADAPTIVE -> copy(actionsAdaptive = actionsAdaptive + 1)
        AssistanceMode.ON_DEMAND -> copy(actionsOnDemand = actionsOnDemand + 1)
    }

    suspend fun lessonCard(language: String) = store.update(today(), language) { it.copy(lessonCards = it.lessonCards + 1) }

    suspend fun practice(language: String, good: Boolean) =
        store.update(today(), language) { it.copy(practiceTries = it.practiceTries + 1, practiceGood = it.practiceGood + if (good) 1 else 0) }

    suspend fun snapshot(language: String, snapshot: MasterySnapshot) = store.update(today(), language) { it.copy(snapshot = snapshot) }

    suspend fun days(language: String): List<DailyActivity> = store.days(language)

    suspend fun deleteLanguage(language: String) = store.deleteLanguage(language)

    suspend fun deleteAll() = store.deleteAll()
}
