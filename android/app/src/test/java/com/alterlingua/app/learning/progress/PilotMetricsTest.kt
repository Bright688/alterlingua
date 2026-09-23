package com.alterlingua.app.learning.progress

import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.lessons.InMemoryDailyLessonStore
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.LessonState
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The pilot's metrics: how each one is counted, and that the report holds counts and nothing else. */
class PilotMetricsTest {
    private var now = LocalDate.of(2026, 8, 3).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli() // a Monday
    private val week = 7L * 24 * 60 * 60 * 1000
    private var mode = AssistanceMode.FULL_SUPPORT
    private val mapStore = InMemoryLanguageMapStore()
    private val progressStore = InMemoryProgressStore()
    private val log = ProgressLog(progressStore, { now }, { ZoneOffset.UTC }, { mode })
    private val map = LanguageMapService(mapStore, clock = { now }, progress = log)

    private fun key(text: String) = UnitKey("fr", text, UnitType.WORD)
    private suspend fun days() = progressStore.days("fr")
    private suspend fun total(pick: (DailyActivity) -> Int) = days().sumOf(pick)

    private suspend fun expose(text: String, times: Int = 1) {
        repeat(times) {
            val candidate = LearningCandidate(text, text, UnitType.WORD, "fr", "en", Usefulness(0.5, emptyList()), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, "fr", "en", listOf(candidate)))
        }
    }

    // ---- translation interactions ----

    @Test fun eachKindOfTranslationIsCountedOnItsOwn() = runTest {
        repeat(3) { log.translation("fr", InteractionKind.OUTGOING_TEXT) }
        repeat(2) { log.translation("fr", InteractionKind.OUTGOING_VOICE) }
        repeat(5) { log.translation("fr", InteractionKind.INCOMING_MESSAGE) }
        log.translation("fr", InteractionKind.INCOMING_VOICE)
        val d = days().single()
        assertEquals(listOf(3, 2, 5, 1), listOf(d.translationsOutgoingText, d.translationsOutgoingVoice, d.translationsIncomingText, d.translationsIncomingVoice))
        assertEquals(11, d.translations)
        assertTrue(d.hasActivity)
    }

    @Test fun theMeteredRecorderCountsTheKind_andPassesTheInteractionOnUnchanged() = runTest {
        val passed = mutableListOf<TranslationInteraction>()
        val recorder = MeteredLearningRecorder(LearningRecorder { passed += it }, log, { "fr" }, backgroundScope)
        val interaction = TranslationInteraction(InteractionKind.OUTGOING_TEXT, "a private sentence", "fr")
        recorder.record(interaction)
        runCurrent()
        assertEquals(listOf(interaction), passed)
        assertEquals(1, total { it.translationsOutgoingText })
        assertFalse("no text reaches the progress data", days().toString().contains("private"))
    }

    // ---- vocabulary exposures and help ----

    @Test fun vocabularyExposuresAreTheWordsMetInConversations() = runTest {
        expose("devis", 3); expose("midi")
        assertEquals(4, total { it.wordsMet })
    }

    @Test fun translationHelpRequestsAreCounted() = runTest {
        map.recordHelpRequest(key("devis")); map.recordHelpRequest(key("midi"))
        assertEquals(2, total { it.helpRequests })
    }

    // ---- state transitions ----

    @Test fun unknownToLearning_isCountedOnce_whenTheScoreReachesLearning() = runTest {
        expose("devis", 19)
        assertEquals(0, total { it.toLearning })
        expose("devis") // the 20th exposure: 10 points
        assertEquals(1, total { it.toLearning })
        expose("devis", 5)
        assertEquals("staying in LEARNING adds nothing", 1, total { it.toLearning })
    }

    @Test fun learningToFamiliar_andFamiliarToMastered_areCountedOnTheirOwn() = runTest {
        expose("devis", 20)
        map.recordLessonEncounter(key("devis")); map.recordLessonEncounter(key("devis"))
        map.recordRecognition(key("devis"), true)
        assertEquals(0, total { it.toFamiliar })
        map.recordRecognition(key("devis"), true) // two correct recognitions: FAMILIAR
        assertEquals(1, total { it.toFamiliar })
        assertEquals(0, total { it.toMastered })
        map.recordRecognition(key("devis"), true); map.recordRecognition(key("devis"), true) // four: MASTERED
        assertEquals(1, total { it.toMastered })
        assertEquals("each boundary is counted once", listOf(1, 1, 1), listOf(total { it.toLearning }, total { it.toFamiliar }, total { it.toMastered }))
    }

    @Test fun aWordThatJumpsTwoStates_countsBothBoundaries() = runTest {
        log.stateChanged("fr", MasteryStatus.UNKNOWN, MasteryStatus.FAMILIAR)
        assertEquals(listOf(1, 1, 0), listOf(total { it.toLearning }, total { it.toFamiliar }, total { it.toMastered }))
        log.stateChanged("fr", MasteryStatus.LEARNING, MasteryStatus.MASTERED)
        assertEquals(listOf(1, 2, 1), listOf(total { it.toLearning }, total { it.toFamiliar }, total { it.toMastered }))
    }

    @Test fun movingDown_isNeverCountedAsProgress() = runTest {
        log.stateChanged("fr", MasteryStatus.MASTERED, MasteryStatus.FAMILIAR)
        log.stateChanged("fr", MasteryStatus.LEARNING, MasteryStatus.LEARNING)
        assertTrue(days().isEmpty())
    }

    @Test fun aMasteredWordThatIsAskedAbout_dropsBackWithoutCountingAsAnAdvance() = runTest {
        mapStore.update(key("devis")) { com.alterlingua.app.learning.map.LanguageMapItem("fr", "devis", UnitType.WORD, "devis", exposureCount = 12, lessonEncounters = 4, correctRecognitions = 5, lastSeen = now, masteryState = MasteryStatus.MASTERED) }
        map.recordHelpRequest(key("devis"))
        assertEquals(0, total { it.toMastered })
    }

    // ---- lessons and pronunciation ----

    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning"
    }

    private fun lessonService() = LessonService(
        map, meanings, InMemoryDailyLessonStore(), { LessonLanguages(Languages.French, Languages.English) },
        clock = { now }, zone = ZoneOffset.UTC, progress = log,
    )

    @Test fun aLessonCompletedIsCountedOnce_whenTheLastCardIsDone() = runTest {
        for (w in listOf("devis", "midi", "acompte")) expose(w, 3)
        val service = lessonService()
        var state = service.today()
        assertTrue(state is LessonState.InProgress)
        val cards = (state as LessonState.InProgress).lesson.cards.size
        repeat(cards - 1) { service.next() }
        assertEquals("not finished yet", 0, total { it.lessonsCompleted })
        service.next()
        assertEquals(1, total { it.lessonsCompleted })
        service.next() // pressing Next after the end changes nothing
        assertEquals(1, total { it.lessonsCompleted })
        assertEquals(cards, total { it.lessonCards })
    }

    @Test fun pronunciationAttemptsAndSuccessesAreCounted() = runTest {
        map.recordPronunciation(key("devis"), true); map.recordPronunciation(key("devis"), false)
        assertEquals(listOf(2, 1), listOf(total { it.practiceTries }, total { it.practiceGood }))
    }

    // ---- assistance modes ----

    @Test fun eachActionIsCountedUnderTheModeSelectedAtTheTime() = runTest {
        log.translation("fr", InteractionKind.INCOMING_MESSAGE)
        mode = AssistanceMode.ADAPTIVE
        log.translation("fr", InteractionKind.OUTGOING_TEXT); map.recordHelpRequest(key("devis"))
        mode = AssistanceMode.ON_DEMAND
        map.recordHelpRequest(key("midi"))
        val d = days().single()
        assertEquals(listOf(1, 2, 1), listOf(d.actionsFullSupport, d.actionsAdaptive, d.actionsOnDemand))
    }

    // ---- the report ----

    private suspend fun report(participant: String? = "P07") = PilotReport.build(participant, "0.1.0", "fr", days(), now, ZoneOffset.UTC)

    private fun keys(o: JSONObject) = o.keys().asSequence().toSet()

    @Test fun theReportHasExactlyTheDocumentedKeys() = runTest {
        expose("devis", 25); log.translation("fr", InteractionKind.INCOMING_MESSAGE)
        val r = report()
        assertEquals(setOf("schema", "participant", "app_version", "generated_on", "language", "dependence_trend", "weeks"), keys(r))
        assertEquals("alterlingua.pilot.v1", r.getString("schema"))
        val week = r.getJSONArray("weeks").getJSONObject(0)
        assertEquals(PilotMetrics.WEEK_KEYS, keys(week))
        assertEquals(setOf("outgoing_text", "outgoing_voice", "incoming_text", "incoming_voice", "total"), keys(week.getJSONObject("translations")))
        assertEquals(setOf("unknown_to_learning", "learning_to_familiar", "familiar_to_mastered"), keys(week.getJSONObject("word_state_advances")))
        assertEquals(setOf("full_support", "adaptive", "on_demand"), keys(week.getJSONObject("mode_actions")))
    }

    @Test fun everyValueIsANumber_aDate_aCode_orNull_neverFreeText() = runTest {
        expose("acompte", 3); map.recordHelpRequest(key("acompte")); log.translation("fr", InteractionKind.OUTGOING_TEXT)
        val strings = mutableListOf<Pair<String, String>>()
        fun walk(name: String, value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { walk(it, value.get(it)) }
                is JSONArray -> for (i in 0 until value.length()) walk(name, value.get(i))
                is String -> strings += name to value
                else -> Unit
            }
        }
        walk("root", report())
        val allowed = mapOf(
            "schema" to Regex("alterlingua\\.pilot\\.v1"), "participant" to Regex("P\\d{2,3}"), "app_version" to Regex("\\d+\\.\\d+\\.\\d+"),
            "generated_on" to Regex("\\d{4}-\\d{2}-\\d{2}"), "start" to Regex("\\d{4}-\\d{2}-\\d{2}"), "language" to Regex("[a-z]{2}"), "status" to Regex("no_data|collecting|ready"),
        )
        for ((name, value) in strings) assertTrue("$name=$value", allowed[name]?.matches(value) == true)
    }

    @Test fun noWordFromTheMapReachesTheReport() = runTest {
        expose("acompte", 25); map.recordHelpRequest(key("acompte")); map.recordLessonEncounter(key("acompte"))
        assertFalse(report().toString().contains("acompte"))
    }

    @Test fun theReportCarriesTheTrendAndTheWeeklyDependence() = runTest {
        val monday = LocalDate.of(2026, 8, 3)
        progressStore.update(monday.toString(), "fr") { DailyActivity(it.date, "fr", wordsMet = 100, wordsAssisted = 94, translationsIncomingText = 7, toLearning = 4) }
        progressStore.update(monday.plusWeeks(3).toString(), "fr") { DailyActivity(it.date, "fr", wordsMet = 100, wordsAssisted = 71, lessonsCompleted = 5, toFamiliar = 2) }
        now = monday.plusWeeks(7).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        progressStore.update(monday.plusWeeks(7).toString(), "fr") { DailyActivity(it.date, "fr", wordsMet = 100, wordsAssisted = 49, toMastered = 1, actionsAdaptive = 9) }
        val r = report()
        val trend = r.getJSONObject("dependence_trend")
        assertEquals("ready", trend.getString("status"))
        assertEquals(listOf(1, 94, 8, 49, -45), listOf(trend.getInt("first_week"), trend.getInt("first_percent"), trend.getInt("latest_week"), trend.getInt("latest_percent"), trend.getInt("change_points")))
        val weeks = r.getJSONArray("weeks")
        assertEquals(8, weeks.length())
        assertEquals(94, weeks.getJSONObject(0).getInt("translation_dependence_percent"))
        assertTrue(weeks.getJSONObject(1).isNull("translation_dependence_percent"))
        assertEquals(7, weeks.getJSONObject(0).getJSONObject("translations").getInt("incoming_text"))
        assertEquals(5, weeks.getJSONObject(3).getInt("lessons_completed"))
        assertEquals(9, weeks.getJSONObject(7).getJSONObject("mode_actions").getInt("adaptive"))
    }

    @Test fun withNoHistory_theReportSaysSo_andInventsNothing() = runTest {
        val r = report(participant = null)
        assertTrue(r.isNull("participant"))
        assertEquals("no_data", r.getJSONObject("dependence_trend").getString("status"))
        val week = r.getJSONArray("weeks").getJSONObject(0)
        assertTrue(week.isNull("translation_dependence_percent"))
        assertEquals(0, week.getInt("vocabulary_exposures"))
        assertNotNull(week)
    }

    @Test fun theEventNamesAreStableIdentifiers() {
        val names = listOf(
            PilotMetrics.Event.TRANSLATION_COMPLETED, PilotMetrics.Event.VOCABULARY_EXPOSURE, PilotMetrics.Event.TRANSLATION_HELP_REQUESTED,
            PilotMetrics.Event.WORD_STATE_ADVANCED, PilotMetrics.Event.LESSON_CARD_COMPLETED, PilotMetrics.Event.LESSON_COMPLETED,
            PilotMetrics.Event.PRONUNCIATION_ATTEMPTED, PilotMetrics.Event.ASSISTANCE_MODE_ACTION,
        )
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.matches(Regex("[a-z]+(_[a-z]+)+")) })
    }
}
