package com.alterlingua.app.learning.progress

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.engine.UsefulnessSignal
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapItem
import com.alterlingua.app.learning.map.LanguageMapService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** How real activity turns into the daily counts, and from them into Translation Dependence. */
class ProgressRecordingTest {
    private val day = 24L * 60 * 60 * 1000
    private var now = LocalDate.of(2026, 8, 3).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli() // a Monday
    private val mapStore = InMemoryLanguageMapStore()
    private val progressStore = InMemoryProgressStore()
    private val log = ProgressLog(progressStore, { now }, { ZoneOffset.UTC })
    private val map = LanguageMapService(mapStore, clock = { now }, progress = log)
    private val service = ProgressService(map, log, { now }, { ZoneOffset.UTC })

    private fun key(text: String, type: UnitType = UnitType.WORD) = UnitKey("fr", text, type)

    private suspend fun met(vararg words: String, type: UnitType = UnitType.WORD, kind: InteractionKind = InteractionKind.INCOMING_MESSAGE) {
        val candidates = words.map { LearningCandidate(it, it, type, "fr", "en", Usefulness(0.5, listOf(UsefulnessSignal.CONTENT_WORD)), Exposure(1, now, now)) }
        map.recordLearningEvent(LearningEvent("e", now, kind, "fr", "en", candidates))
    }

    private suspend fun master(text: String) {
        mapStore.update(key(text)) { LanguageMapItem("fr", text, UnitType.WORD, text, "m", "en", exposureCount = 12, lessonEncounters = 4, correctRecognitions = 5, lastSeen = now) }
    }


    // ---- words met and assisted ----

    @Test fun aNewWord_isMet_assisted_andNew() = runTest {
        met("devis")
        val d = progressStore.days("fr").last()
        assertEquals(1, d.wordsMet); assertEquals(1, d.wordsAssisted); assertEquals(1, d.newWords)
    }

    @Test fun aWordMetAgainButNotMastered_isAssistedAgain_butNotNew() = runTest {
        met("devis"); met("devis")
        val d = progressStore.days("fr").last()
        assertEquals(2, d.wordsMet); assertEquals(2, d.wordsAssisted); assertEquals(1, d.newWords)
    }

    @Test fun aMasteredWord_isMetWithoutNeedingAssistance() = runTest {
        master("devis")
        met("devis", "acompte")
        val d = progressStore.days("fr").last()
        assertEquals(2, d.wordsMet)
        assertEquals("only acompte needed help", 1, d.wordsAssisted)
        assertEquals(1, d.newWords)
    }

    @Test fun aMasteredWordUnseenForLong_needsAssistanceAgain() = runTest {
        master("devis")
        now += 200 * day
        met("devis")
        assertEquals(1, progressStore.days("fr").last().wordsAssisted)
    }

    @Test fun aWordJustAskedAbout_needsAssistanceEvenIfItWasMastered() = runTest {
        master("devis")
        map.recordHelpRequest(key("devis"))
        met("devis")
        assertEquals(1, progressStore.days("fr").last().wordsAssisted)
    }

    @Test fun phrasesAreNotCounted_soWordsInsideThemAreNotCountedTwice() = runTest {
        met("avant midi", type = UnitType.PHRASE)
        met("avant", "midi")
        assertEquals(2, progressStore.days("fr").last().wordsMet)
    }

    @Test fun eventsInEveryKindOfCommunicationCount() = runTest {
        for (kind in InteractionKind.entries) met("mot", kind = kind)
        assertEquals(InteractionKind.entries.size, progressStore.days("fr").last().wordsMet)
    }

    @Test fun countsAreKeptPerLanguage() = runTest {
        met("devis")
        map.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, "es", "en",
            listOf(LearningCandidate("presupuesto", "presupuesto", UnitType.WORD, "es", "en", Usefulness(0.5, emptyList()), Exposure(1, now, now)))))
        assertEquals(1, progressStore.days("fr").sumOf { it.wordsMet })
        assertEquals(1, progressStore.days("es").sumOf { it.wordsMet })
    }

    // ---- learning actions ----

    @Test fun helpRequestsLessonCardsAndPracticeAreCounted() = runTest {
        map.recordHelpRequest(key("devis"))
        map.recordLessonEncounter(key("devis"))
        map.recordPronunciation(key("devis"), understood = true)
        map.recordPronunciation(key("devis"), understood = false)
        val d = progressStore.days("fr").last()
        assertEquals(1, d.helpRequests); assertEquals(1, d.lessonCards); assertEquals(2, d.practiceTries); assertEquals(1, d.practiceGood)
        assertEquals("these are not conversations", 0, d.wordsMet)
    }

    // ---- snapshots ----

    @Test fun eachChangeSavesTodaysStateCounts() = runTest {
        met("devis", "midi")
        assertEquals(MasterySnapshot(encountered = 2, learning = 0, familiar = 0, mastered = 0), progressStore.days("fr").last().snapshot)
        master("acompte")
        map.recordHelpRequest(key("devis"))
        val snapshot = progressStore.days("fr").last().snapshot!!
        assertEquals(3, snapshot.encountered)
        assertEquals("the recent help request holds acompte's neighbour, but acompte itself is mastered", 1, snapshot.mastered)
    }

    @Test fun theSnapshotShowsStatesAsTheyStandToday_includingTheFallForTimeUnseen() = runTest {
        master("devis")
        met("midi") // writes a snapshot with devis mastered
        assertEquals(1, progressStore.days("fr").last().snapshot!!.mastered)
        now += 200 * day
        met("midi")
        assertEquals(0, progressStore.days("fr").last().snapshot!!.mastered)
    }

    // ---- robustness and privacy ----

    @Test fun ifTheProgressStoreFails_learningStillWorks() = runTest {
        val failing = object : ProgressStore {
            override suspend fun update(date: String, language: String, change: (DailyActivity) -> DailyActivity) = throw IllegalStateException("disk full")
            override suspend fun days(language: String) = emptyList<DailyActivity>()
            override suspend fun deleteLanguage(language: String) = Unit
            override suspend fun deleteAll() = Unit
        }
        val safe = LanguageMapService(mapStore, clock = { now }, progress = ProgressLog(failing, { now }, { ZoneOffset.UTC }))
        safe.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, "fr", "en",
            listOf(LearningCandidate("devis", "devis", UnitType.WORD, "fr", "en", Usefulness(0.5, emptyList()), Exposure(1, now, now)))))
        assertEquals(1, mapStore.items("fr").size)
    }

    @Test fun withoutAProgressLog_theMapWorksAsBefore() = runTest {
        val plain = LanguageMapService(InMemoryLanguageMapStore(), clock = { now })
        plain.recordHelpRequest(key("devis"))
        assertEquals(1, plain.item(key("devis"))!!.helpRequests)
    }

    @Test fun theProgressRecordHoldsNoWordsOrText() = runTest {
        met("acompte")
        val row = progressStore.days("fr").last()
        assertTrue("no word appears in the record", !row.toString().contains("acompte"))
    }

    // ---- end to end: dependence falls as words become mastered ----

    private suspend fun week(index: Int, words: List<String>) {
        now = LocalDate.of(2026, 8, 3).plusWeeks(index.toLong()).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repeat(4) { met(*words.toTypedArray()) } // 5 words x 4 messages = 20 words met
    }

    @Test fun dependenceFalls_asWordsBecomeMastered_andOnlyWithRealHistory() = runTest {
        val words = listOf("devis", "midi", "acompte", "demain", "client")
        week(0, words)
        for (w in words.take(3)) master(w) // learning over the following weeks
        week(1, words)
        for (w in words.drop(3)) master(w)
        week(2, words)
        val report = service.report(Languages.French)
        val ready = report.dependence as DependenceStatus.Ready
        assertEquals(listOf(1, 2, 3), ready.points.map { it.weekNumber })
        assertEquals(listOf(100, 40, 0), ready.points.map { it.dependencePercent })
        assertTrue(report.masteryTrendReady)
    }

    @Test fun withoutAnyActivity_thereIsNoReportData() = runTest {
        val report = service.report(Languages.French)
        assertEquals(DependenceStatus.NoData, report.dependence)
        assertEquals(ProgressTotals(0, 0, 0, 0, 0), report.totals)
    }

    @Test fun totalsUseTheStatesOfToday_andEachLanguageHasItsOwn() = runTest {
        master("devis")
        mapStore.update(key("midi")) { LanguageMapItem("fr", "midi", UnitType.WORD, "midi", exposureCount = 22, lessonEncounters = 2, lastSeen = now) }
        mapStore.update(UnitKey("es", "presupuesto", UnitType.WORD)) { LanguageMapItem("es", "presupuesto", UnitType.WORD, "presupuesto", lastSeen = now) }
        val fr = service.report(Languages.French).totals
        assertEquals(2, fr.encountered); assertEquals(1, fr.mastered); assertEquals(1, fr.learning)
        assertEquals(1, service.report(Languages.Spanish).totals.encountered)
        now += 200 * day
        assertEquals("a long-unseen mastered word is no longer counted as mastered", 0, service.report(Languages.French).totals.mastered)
    }

    @Test fun onlyTheLanguageBeingLearnedAppearsInItsReport() = runTest {
        met("devis")
        assertNull(service.report(Languages.Spanish).dependence.let { if (it == DependenceStatus.NoData) null else it })
    }
}
