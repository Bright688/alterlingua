package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningPipelineTest {

    private var now = 1_000L
    private val store = InMemoryExposureStore()
    private var learning: Language = Languages.French
    private var native: Language = Languages.English
    private var enabled = true
    private var ids = 0

    private fun pipeline(registry: AnalyzerRegistry = AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = Icu4jWordBreaker)))) = LearningPipeline(
        analyzers = registry,
        context = { LearningContext(learning, native, enabled) },
        store = store,
        clock = { now },
        newId = { "event-${ids++}" },
    )

    private fun interaction(text: String, language: Language, kind: InteractionKind = InteractionKind.OUTGOING_TEXT) =
        TranslationInteraction(kind, text, language.code)

    private fun recorded(result: PipelineResult) = (result as PipelineResult.Recorded).event

    private val devis = "Je vais vous envoyer le devis avant midi."

    // ---- the pipeline ----

    @Test
    fun aTranslatedMessageInTheLearningLanguage_becomesAnEventOfUnits() = runTest {
        val event = recorded(pipeline().record(interaction(devis, Languages.French)))
        assertEquals("event-0", event.id)
        assertEquals(1_000, event.atMillis)
        assertEquals(InteractionKind.OUTGOING_TEXT, event.kind)
        assertEquals("fr", event.learningLanguage)
        assertEquals("en", event.meaningLanguage)
        assertTrue(event.candidates.map { it.normalized }.containsAll(listOf("devis", "envoyer", "avant midi")))
        assertTrue(event.candidates.all { it.learningLanguage == "fr" && it.meaningLanguage == "en" })
    }

    @Test
    fun theEventAndTheStoreHoldNoMessage_onlyShortUnits() = runTest {
        val event = recorded(pipeline().record(interaction(devis, Languages.French)))
        assertTrue(event.candidates.none { it.surface.equals(devis, ignoreCase = true) })
        assertTrue(event.candidates.all { it.surface.length < devis.length })
        val stored = store.entries("fr")
        assertTrue(stored.isNotEmpty())
        assertTrue(stored.none { it.surface.equals(devis, ignoreCase = true) })
        // Rebuilding the message from the stored units is not possible: they are a handful of short pieces.
        assertTrue(stored.size <= 12)
    }

    @Test
    fun theThreeKindsOfInteractionAreKept() = runTest {
        val p = pipeline()
        for (kind in InteractionKind.entries) {
            assertEquals(kind, recorded(p.record(interaction(devis, Languages.French, kind))).kind)
        }
    }

    // ---- when nothing is learned ----

    @Test
    fun switchedOff_nothingIsAnalysedOrStored() = runTest {
        enabled = false
        val result = pipeline().record(interaction(devis, Languages.French))
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.DISABLED), result)
        assertTrue(store.entries("fr").isEmpty())
    }

    @Test
    fun onlyTextInTheLearningLanguageTeachesIt() = runTest {
        // Learning Français: an English message, or a Spanish one, teaches nothing.
        val p = pipeline()
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.NOT_THE_LEARNING_LANGUAGE), p.record(interaction("Are you coming tomorrow?", Languages.English)))
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.NOT_THE_LEARNING_LANGUAGE), p.record(interaction("Te enviaré el presupuesto.", Languages.Spanish)))
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.NOT_THE_LEARNING_LANGUAGE), p.record(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, "hola", "xx")))
        assertTrue(store.entries("fr").isEmpty() && store.entries("es").isEmpty() && store.entries("en").isEmpty())
    }

    @Test
    fun aLanguageWithoutAnAnalyzer_isSkippedAsUnsupported_notAnalysedWrongly() = runTest {
        learning = Languages.Japanese
        val noDictionary = AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = null)))
        val result = pipeline(noDictionary).record(interaction("明日来ますか？", Languages.Japanese))
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.UNSUPPORTED_LANGUAGE), result)
        assertTrue(store.entries("ja").isEmpty())
    }

    @Test
    fun textWithNothingUsefulInIt_isSkipped() = runTest {
        val p = pipeline()
        assertEquals(PipelineResult.Skipped(PipelineResult.Reason.NOTHING_USEFUL), p.record(interaction("😀 !!! 12:30", Languages.French)))
        assertTrue(store.entries("fr").isEmpty())
    }

    // ---- each language has its own map ----

    @Test
    fun switchingTheLearningLanguage_analysesInThatLanguage_andKeepsTheOthersApart() = runTest {
        val p = pipeline()
        learning = Languages.French
        p.record(interaction(devis, Languages.French))
        learning = Languages.Spanish
        p.record(interaction("Te enviaré el presupuesto antes del mediodía.", Languages.Spanish))
        learning = Languages.Japanese
        p.record(interaction("正午までに見積書をお送りします。", Languages.Japanese))
        learning = Languages.Chinese
        p.record(interaction("你明天来吗？", Languages.Chinese))

        assertTrue(store.entries("fr").any { it.key.normalized == "devis" })
        assertTrue(store.entries("es").any { it.key.normalized == "presupuesto" })
        assertTrue(store.entries("ja").any { it.key.normalized == "見積書" })
        assertTrue(store.entries("zh").any { it.key.normalized == "明天" })
        // Knowing a word in one language says nothing about another.
        assertTrue(store.entries("es").none { it.key.normalized == "devis" })
        assertTrue(store.entries("fr").none { it.key.normalized == "presupuesto" })
    }

    // ---- exposure tracking ----

    @Test
    fun meetingTheSameUnitAgain_countsAnExposure_andUpdatesLastSeen() = runTest {
        val p = pipeline()
        p.record(interaction(devis, Languages.French))
        now = 5_000
        p.record(interaction("Le devis est prêt.", Languages.French))
        now = 9_000
        p.record(interaction("J'ai relu le devis.", Languages.French, InteractionKind.INCOMING_MESSAGE))

        val entry = store.entries("fr").first { it.key.normalized == "devis" }
        assertEquals(3, entry.exposure.count)
        assertEquals(1_000, entry.exposure.firstSeenMillis)
        assertEquals(9_000, entry.exposure.lastSeenMillis)
        assertEquals("devis", store.entries("fr").first().key.normalized) // most often met first
    }

    @Test
    fun theStoreIsBounded_andForgetsTheLeastRecentlySeen() = runTest {
        val small = InMemoryExposureStore(maxUnitsPerLanguage = 3)
        val event = { at: Long, word: String ->
            LearningEvent("e", at, InteractionKind.OUTGOING_TEXT, "fr", "en", listOf(candidate(word, at)))
        }
        small.record(event(1, "un")); small.record(event(2, "deux")); small.record(event(3, "trois")); small.record(event(4, "quatre"))
        assertEquals(setOf("deux", "trois", "quatre"), small.entries("fr").map { it.key.normalized }.toSet())
    }

    @Test
    fun aMeaningCanBeFilledInLater_withoutChangingTheKey() {
        val original = candidate("devis", 1)
        val enriched = original.copy(meaning = "quotation", lemma = "devis")
        assertEquals(original.key, enriched.key)
        assertNull(original.meaning)
        assertNotNull(enriched.meaning)
    }

    @Test
    fun theRecorderNeverLetsAFailureEscape() = runTest {
        val broken = LearningPipeline(AnalyzerRegistry(emptyList()), { error("settings unavailable") }, store)
        PipelineRecorder(broken, this).record(interaction(devis, Languages.French)) // must not throw
        testScheduler.advanceUntilIdle()
        assertFalse(store.entries("fr").isNotEmpty())
    }

    private fun candidate(word: String, at: Long) = LearningCandidate(
        surface = word, normalized = word, type = UnitType.WORD, learningLanguage = "fr", meaningLanguage = "en",
        usefulness = Usefulness(0.4, listOf(UsefulnessSignal.CONTENT_WORD)), exposure = Exposure(1, at, at),
    )
}
