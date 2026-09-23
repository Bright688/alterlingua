package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.MasteryStatus.FAMILIAR
import com.alterlingua.app.learning.MasteryStatus.LEARNING
import com.alterlingua.app.learning.MasteryStatus.MASTERED
import com.alterlingua.app.learning.MasteryStatus.UNKNOWN
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.Icu4jWordBreaker
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningContext
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.LearningPipeline
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.engine.UsefulnessSignal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageMapServiceTest {

    private var now = 10_000L * MasteryCalculator.DAY_MILLIS
    private val store = InMemoryLanguageMapStore()
    private val service = LanguageMapService(store, clock = { now })

    private val devis = UnitKey("fr", "devis", UnitType.WORD)

    private fun candidate(key: UnitKey, surface: String = key.normalized, native: String = "en", at: Long = now) = LearningCandidate(
        surface = surface, normalized = key.normalized, type = key.type, learningLanguage = key.language, meaningLanguage = native,
        usefulness = Usefulness(0.4, listOf(UsefulnessSignal.CONTENT_WORD)), exposure = Exposure(1, at, at),
    )

    private fun event(vararg keys: UnitKey, surface: String? = null) =
        LearningEvent("e", now, InteractionKind.OUTGOING_TEXT, keys.first().language, "en", keys.map { candidate(it, surface ?: it.normalized) })

    private suspend fun state(key: UnitKey = devis): MasteryStatus = service.item(key)!!.masteryState

    // ---- what an item holds ----

    @Test
    fun aLearningEvent_createsItemsWithAllTheirFields() = runTest {
        service.recordLearningEvent(LearningEvent("e", now, InteractionKind.OUTGOING_TEXT, "fr", "es", listOf(candidate(devis, surface = "Devis", native = "es"))))
        val item = service.item(devis)!!
        assertEquals("fr", item.language)
        assertEquals("devis", item.normalized)
        assertEquals("Devis", item.displayForm)
        assertEquals(UnitType.WORD, item.type)
        assertNull(item.meaning)
        assertEquals("es", item.meaningLanguage)
        assertEquals(1, item.exposureCount)
        assertEquals(0, item.helpRequests + item.lessonEncounters + item.correctRecognitions + item.incorrectRecognitions)
        assertEquals(now, item.firstSeen)
        assertEquals(now, item.lastSeen)
        assertEquals(0.5, item.masteryScore, 0.0001)
        assertEquals(UNKNOWN, item.masteryState)
    }

    @Test
    fun meetingAnItemAgain_addsAnExposure_keepsTheFirstDisplayForm_andMovesLastSeen() = runTest {
        val start = now
        service.recordLearningEvent(event(devis, surface = "Devis"))
        now += 3 * MasteryCalculator.DAY_MILLIS
        service.recordLearningEvent(LearningEvent("e2", now, InteractionKind.INCOMING_MESSAGE, "fr", "en", listOf(candidate(devis, surface = "DEVIS", at = now))))
        val item = service.item(devis)!!
        assertEquals(2, item.exposureCount)
        assertEquals("Devis", item.displayForm)
        assertEquals(start, item.firstSeen)
        assertEquals(now, item.lastSeen)
    }

    @Test
    fun theMeaningCanBeFilledInLater_withoutChangingMastery() = runTest {
        service.recordLearningEvent(event(devis))
        val before = service.item(devis)!!
        val after = service.setMeaning(devis, "quotation", "en")!!
        assertEquals("quotation", after.meaning)
        assertEquals("en", after.meaningLanguage)
        assertEquals(before.masteryScore, after.masteryScore, 0.0)
        assertEquals(before.exposureCount, after.exposureCount)
        assertNull(service.setMeaning(UnitKey("fr", "inconnu", UnitType.WORD), "x", "en")) // nothing to fill in
    }

    // ---- a journey through the service: UNKNOWN -> LEARNING -> FAMILIAR -> MASTERED ----

    @Test
    fun anItemProgressesFromUnknownToMastered_withEveryStepStored() = runTest {
        service.recordLearningEvent(event(devis))
        assertEquals(UNKNOWN, state()) // met once: 0.5

        repeat(3) { service.recordLessonEncounter(devis) }
        assertEquals(LEARNING, state()) // 0.5 + 12 = 12.5

        service.recordRecognition(devis, correct = true)
        assertEquals(LEARNING, state()) // 27.5, one correct recognition

        service.recordRecognition(devis, correct = true)
        assertEquals(FAMILIAR, state()) // 42.5, two correct recognitions

        service.recordRecognition(devis, correct = true)
        service.recordRecognition(devis, correct = true)
        assertEquals(FAMILIAR, state()) // 72.5, four correct but below 75

        service.recordLessonEncounter(devis) // lessons are capped at 16 (4 encounters), so this is the last lesson point
        assertEquals(MASTERED, state()) // 0.5 + 16 + 60 = 76.5, four correct, 100% accuracy
        assertEquals(76.5, service.item(devis)!!.masteryScore, 0.0001)
    }

    @Test
    fun theStoredResultAlwaysMatchesTheCalculator() = runTest {
        service.recordLearningEvent(event(devis))
        repeat(2) { service.recordLessonEncounter(devis) }
        service.recordHelpRequest(devis)
        service.recordRecognition(devis, correct = true)
        service.recordRecognition(devis, correct = false)
        val item = service.item(devis)!!
        val expected = MasteryCalculator().evaluate(item.evidence, now)
        assertEquals(expected.score, item.masteryScore, 0.0)
        assertEquals(expected.state, item.masteryState)
    }

    @Test
    fun aMasteredItemRegressesWhenTheLearnerGetsItWrong() = runTest {
        service.recordLearningEvent(event(devis))
        repeat(4) { service.recordLessonEncounter(devis) }
        repeat(5) { service.recordRecognition(devis, correct = true) }
        assertEquals(MASTERED, state()) // 0.5 + 16 + 75 = 91.5

        service.recordRecognition(devis, correct = false)
        assertEquals(MASTERED, state()) // 79.5, accuracy 5 of 6
        service.recordRecognition(devis, correct = false)
        assertEquals(FAMILIAR, state()) // 67.5, accuracy 5 of 7
        repeat(3) { service.recordRecognition(devis, correct = false) }
        assertEquals(LEARNING, state()) // 31.5
    }

    @Test
    fun translationHelpRequests_pullAFamiliarItemBack() = runTest {
        service.recordLearningEvent(event(devis))
        repeat(3) { service.recordLessonEncounter(devis) }
        repeat(2) { service.recordRecognition(devis, correct = true) }
        assertEquals(FAMILIAR, state()) // 0.5 + 12 + 30 = 42.5
        service.recordHelpRequest(devis)
        assertEquals(LEARNING, state()) // 39.5: needing the translation again counts against knowing it
        assertEquals(39.5, service.item(devis)!!.masteryScore, 0.0001)
        assertEquals(1, service.item(devis)!!.helpRequests)
    }

    @Test
    fun timeWithoutMeetingAnItem_lowersItsEvaluatedState_butNotWhatIsStored() = runTest {
        service.recordLearningEvent(event(devis))
        repeat(4) { service.recordLessonEncounter(devis) }
        repeat(5) { service.recordRecognition(devis, correct = true) }
        val item = service.item(devis)!!
        assertEquals(MASTERED, item.masteryState)

        assertEquals(MASTERED, service.evaluate(item, now + 60 * MasteryCalculator.DAY_MILLIS).state)
        assertEquals(FAMILIAR, service.evaluate(item, now + 121 * MasteryCalculator.DAY_MILLIS).state) // 91.5 - 30 (three 30-day blocks past 60 days)
        assertEquals(MASTERED, service.item(devis)!!.masteryState) // the stored value is as of the last change
    }

    @Test
    fun exposureAlone_neverGetsBeyondLearning() = runTest {
        repeat(200) { service.recordLearningEvent(event(devis)) }
        val item = service.item(devis)!!
        assertEquals(200, item.exposureCount)
        assertEquals(12.0, item.masteryScore, 0.0001)
        assertEquals(LEARNING, item.masteryState)
    }

    @Test
    fun anUnknownItem_askedForHelp_isCreatedAndStaysUnknown() = runTest {
        service.recordHelpRequest(devis, displayForm = "devis")
        val item = service.item(devis)!!
        assertEquals(1, item.helpRequests)
        assertEquals(0.0, item.masteryScore, 0.0001) // help points cannot go below 0
        assertEquals(UNKNOWN, item.masteryState)
        assertEquals(now, item.lastSeen)
    }

    // ---- languages, summary, deleting ----

    @Test
    fun knowingAWordInOneLanguage_saysNothingAboutAnother() = runTest {
        val spanish = UnitKey("es", "devis", UnitType.WORD)
        service.recordLearningEvent(event(devis))
        repeat(2) { service.recordRecognition(devis, correct = true) }
        service.recordLearningEvent(event(spanish))
        assertNotEquals(state(devis), state(spanish))
        assertEquals(UNKNOWN, state(spanish))
        assertEquals(1, service.items("fr").size)
        assertEquals(1, service.items("es").size)
        assertTrue(service.items("de").isEmpty())
    }

    @Test
    fun theSameWordAsWordAndAsPhrase_areDifferentItems() = runTest {
        service.recordLearningEvent(event(devis))
        service.recordLearningEvent(event(UnitKey("fr", "devis", UnitType.PHRASE)))
        assertEquals(2, service.items("fr").size)
    }

    @Test
    fun theSummaryCountsItemsPerState() = runTest {
        val a = UnitKey("fr", "a", UnitType.WORD); val b = UnitKey("fr", "b", UnitType.WORD); val c = UnitKey("fr", "c", UnitType.WORD)
        service.recordLearningEvent(event(a, b, c))
        repeat(3) { service.recordLessonEncounter(b) }            // LEARNING
        repeat(4) { service.recordLessonEncounter(c) }
        repeat(2) { service.recordRecognition(c, correct = true) } // 0.5 + 16 + 30 = 46.5: FAMILIAR
        val summary = service.summary("fr")
        assertEquals(1, summary.newCount)
        assertEquals(1, summary.learningCount)
        assertEquals(1, summary.familiarCount)
        assertEquals(0, summary.masteredCount)
        assertEquals(3, summary.total)
        assertEquals(listOf("c"), service.items("fr", FAMILIAR).map { it.normalized })
    }

    @Test
    fun deletingOneLanguageLeavesTheOthers_andDeleteAllClearsEverything() = runTest {
        service.recordLearningEvent(event(devis))
        service.recordLearningEvent(event(UnitKey("es", "presupuesto", UnitType.WORD)))
        service.deleteLanguage("fr")
        assertTrue(service.items("fr").isEmpty())
        assertEquals(1, service.items("es").size)
        service.deleteAll()
        assertTrue(service.items("es").isEmpty())
    }

    @Test
    fun theMapCanBeObserved() = runTest {
        service.recordLearningEvent(event(devis))
        assertEquals(listOf("devis"), service.observe("fr").first().map { it.normalized })
    }

    // ---- consistency ----

    @Test
    fun manyUpdatesAtOnce_areAllCounted() = runTest {
        (1..50).map { async { service.recordLearningEvent(event(devis)) } }.awaitAll()
        (1..20).map { async { service.recordRecognition(devis, correct = it % 2 == 0) } }.awaitAll()
        val item = service.item(devis)!!
        assertEquals(50, item.exposureCount)
        assertEquals(10, item.correctRecognitions)
        assertEquals(10, item.incorrectRecognitions)
    }

    @Test
    fun theSameSequenceOfEvents_alwaysGivesTheSameMap() = runTest {
        suspend fun run(): List<LanguageMapItem> {
            now = 10_000L * MasteryCalculator.DAY_MILLIS
            val s = LanguageMapService(InMemoryLanguageMapStore(), clock = { now })
            s.recordLearningEvent(event(devis)); s.recordLessonEncounter(devis); s.recordRecognition(devis, true); s.recordHelpRequest(devis)
            return s.items("fr")
        }
        assertEquals(run(), run())
    }

    // ---- fed by the learning pipeline ----

    @Test
    fun theLearningPipelineFeedsTheMap_inTheSelectedLanguageOnly_andKeepsNoMessage() = runTest {
        var learning = Languages.French
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = Icu4jWordBreaker))),
            context = { LearningContext(learning, Languages.English, enabled = true) },
            store = LanguageMapExposureStore(service),
            clock = { now },
        )
        val message = "Je vais vous envoyer le devis avant midi."
        pipeline.record(TranslationInteraction(InteractionKind.OUTGOING_TEXT, message, "fr"))
        pipeline.record(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, "Are you coming tomorrow?", "en")) // not the learning language
        learning = Languages.Japanese
        pipeline.record(TranslationInteraction(InteractionKind.OUTGOING_TEXT, "正午までに見積書をお送りします。", "ja"))

        val french = service.items("fr")
        assertTrue(french.any { it.normalized == "devis" && it.masteryState == UNKNOWN })
        assertTrue(french.any { it.normalized == "avant midi" && it.type == UnitType.PHRASE })
        assertTrue(service.items("ja").any { it.normalized == "見積書" })
        assertTrue(service.items("en").isEmpty())
        // No stored value is the message, or long enough to rebuild it.
        assertTrue(french.none { it.displayForm.equals(message, ignoreCase = true) })
        assertTrue(french.all { it.displayForm.length < message.length - 10 }) // every unit is a fragment, well short of the message
        val fields = LanguageMapItem::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it in setOf("text", "message", "body", "content", "sentence", "original") })
    }

    @Test
    fun anItemCannotBeMovedToAnotherKey() = runTest {
        service.recordLearningEvent(event(devis))
        var failed = false
        try {
            store.update(devis) { it!!.copy(normalized = "autre") }
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals("devis", service.item(devis)!!.normalized)
        assertEquals(setOf("devis"), service.items("fr").map { it.normalized }.toSet())
    }
}
