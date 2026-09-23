package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.Language
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
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.MasteryCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

private class FakeMeanings(val known: Map<String, String> = emptyMap()) : MeaningProvider {
    var offline = false
    var hang = false
    val asked = mutableListOf<String>()
    override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? {
        asked += unit
        if (hang) awaitCancellation()
        return if (offline) null else known[unit] ?: "meaning of $unit in ${native.code}"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LessonServiceTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private var now = 10_000L * day + 9 * hour // 09:00 UTC
    private val mapStore = InMemoryLanguageMapStore()
    private val map = LanguageMapService(mapStore, clock = { now })
    private val meanings = FakeMeanings()
    private val store = InMemoryDailyLessonStore()
    private var learning: Language = Languages.French
    private var native: Language = Languages.English

    private fun service(meaningProvider: MeaningProvider = meanings, dailyStore: DailyLessonStore = store) = LessonService(
        map = map, meanings = meaningProvider, store = dailyStore, languages = { LessonLanguages(learning, native) },
        clock = { now }, zone = ZoneOffset.UTC, meaningTimeoutMillis = 10_000,
    )

    private suspend fun met(language: String, text: String, type: UnitType = UnitType.WORD, usefulness: Double = 0.4, kind: InteractionKind = InteractionKind.OUTGOING_TEXT, times: Int = 1) {
        repeat(times) {
            val candidate = LearningCandidate(text, text, type, language, "en", Usefulness(usefulness, listOf(UsefulnessSignal.CONTENT_WORD)), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, kind, language, "en", listOf(candidate)))
        }
    }

    private suspend fun fillFrench() {
        met("fr", "devis", usefulness = 0.4, times = 3)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8, kind = InteractionKind.INCOMING_MESSAGE)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        met("fr", "bonjour", usefulness = 0.3, times = 20)
    }

    private fun LessonState.lesson(): DailyLesson = when (this) {
        is LessonState.InProgress -> lesson
        is LessonState.Completed -> lesson
        LessonState.NoLesson -> error("expected a lesson")
    }

    // ---- making the lesson ----

    @Test
    fun withNothingMetYet_thereIsNoLesson_andNothingIsSaved() = runTest {
        assertEquals(LessonState.NoLesson, service().today())
        assertNull(store.load())
    }

    @Test
    fun aLessonHasAboutThreeCards_wordsGetWordCardsAndPhrasesGetPhraseCards() = runTest {
        fillFrench()
        val lesson = service().today().lesson()
        assertEquals(3, lesson.cards.size)
        assertEquals("fr", lesson.language)
        assertEquals(0, lesson.position)
        for (card in lesson.cards) {
            val expected = if (card.key.type == UnitType.WORD) CardKind.WORD_CARD else CardKind.PHRASE_CARD
            assertEquals(expected, card.kind)
        }
        // The very frequent, low-value word did not take a place ahead of the useful phrase and expression.
        val terms = lesson.cards.map { it.term }
        assertTrue("avant midi" in terms && "au courant" in terms)
        assertFalse("bonjour" in terms)
    }

    @Test
    fun eachCardHasItsMeaning_itsContext_andWhyItWasChosen() = runTest {
        fillFrench()
        map.recordHelpRequest(UnitKey("fr", "devis", UnitType.WORD))
        val cards = service().today().lesson().cards
        val devis = cards.first { it.term == "devis" }
        assertEquals("meaning of devis in en", devis.meaning)
        assertEquals("en", devis.meaningLanguage)
        assertEquals(3, devis.context.timesMet)
        assertEquals(1, devis.context.helpRequests)
        assertEquals(InteractionKind.OUTGOING_TEXT, devis.context.lastMetIn)
        assertTrue(com.alterlingua.app.learning.lessons.LessonReason.ASKED_TRANSLATION in devis.context.reasons)
        assertTrue(devis.context.reasons.size in 1..3)
        assertEquals(InteractionKind.INCOMING_MESSAGE, cards.first { it.term == "avant midi" }.context.lastMetIn)
    }

    @Test
    fun aMeaningIsLookedUpOnce_thenKeptInTheMap() = runTest {
        fillFrench()
        service().today()
        assertEquals("meaning of devis in en", map.item(UnitKey("fr", "devis", UnitType.WORD))!!.meaning)
        val asked = meanings.asked.size
        // Tomorrow the same unit needs no new lookup.
        now += day
        store.clear()
        service().today()
        assertTrue(meanings.asked.size <= asked) // nothing already known is asked again
    }

    @Test
    fun offline_theLessonStillWorks_withoutMeanings_andTheyAreTriedAgainLater() = runTest {
        fillFrench()
        meanings.offline = true
        val lesson = service().today().lesson()
        assertEquals(3, lesson.cards.size)
        assertTrue(lesson.cards.all { it.meaning == null && it.meaningLanguage == null })
        assertNull(map.item(UnitKey("fr", "devis", UnitType.WORD))!!.meaning)

        now += day
        meanings.offline = false
        store.clear()
        assertTrue(service().today().lesson().cards.all { it.meaning != null })
    }

    @Test
    fun aMeaningLookupThatNeverAnswers_isGivenUpOn() = runTest {
        fillFrench()
        meanings.hang = true
        var state: LessonState? = null
        backgroundScope.launch { state = service().today() }
        runCurrent()
        advanceTimeBy(10_001); runCurrent()
        assertTrue(state!!.lesson().cards.all { it.meaning == null })
    }

    // ---- one lesson a day ----

    @Test
    fun theLessonIsMadeOncePerDay_andStaysTheSameEvenWhenNewItemsArrive() = runTest {
        fillFrench()
        val first = service().today().lesson()
        met("fr", "réunion", usefulness = 0.95, times = 5) // a very attractive new item, but the day's lesson is fixed
        val again = service().today().lesson()
        assertEquals(first, again)
        assertFalse(again.cards.any { it.term == "réunion" })
    }

    @Test
    fun aNewDayGivesANewLesson_atMidnightLocalTime() = runTest {
        fillFrench()
        val lessonToday = service().today().lesson()
        now += 12 * hour // 21:00 the same day
        assertEquals(lessonToday.date, service().today().lesson().date)
        now += 4 * hour  // 01:00 the next day
        assertTrue(service().today().lesson().date > lessonToday.date)
    }

    @Test
    fun aFinishedLessonStaysFinished_fortheRestOfTheDay() = runTest {
        fillFrench()
        val s = service()
        repeat(3) { s.next() }
        assertTrue(s.today() is LessonState.Completed)
        met("fr", "réunion", usefulness = 0.95, times = 5)
        assertTrue(s.today() is LessonState.Completed) // no second lesson today
    }

    @Test
    fun reopeningTheApp_resumesWhereTheLearnerWas() = runTest {
        fillFrench()
        service().next()
        val resumed = service().today() // a fresh service over the same saved lesson
        assertEquals(1, resumed.lesson().position)
    }

    // ---- the lesson interaction is a mastery signal ----

    @Test
    fun finishingACard_recordsOneLessonEncounter_andUpdatesMastery() = runTest {
        fillFrench()
        val s = service()
        val first = s.today().lesson().cards[0]
        val before = map.item(first.key)!!
        s.next()
        val after = map.item(first.key)!!
        assertEquals(before.lessonEncounters + 1, after.lessonEncounters)
        assertEquals(now, after.lastLessonAt)
        assertEquals(now, after.lastSeen)
        assertEquals(MasteryCalculator().evaluate(after.evidence, now).score, after.masteryScore, 0.0001)
        assertTrue(after.masteryScore > before.masteryScore) // +4 points for the encounter
    }

    @Test
    fun goingThroughTheWholeLesson_recordsEachCardOnce_andCompletes() = runTest {
        fillFrench()
        val s = service()
        val cards = s.today().lesson().cards
        assertTrue(s.next() is LessonState.InProgress)
        assertTrue(s.next() is LessonState.InProgress)
        val done = s.next()
        assertTrue(done is LessonState.Completed)
        for (card in cards) assertEquals(1, map.item(card.key)!!.lessonEncounters)
        assertTrue(done.lesson().completed)
        assertEquals(setOf(0, 1, 2), done.lesson().encountered)
        // Pressing Next again after the end counts nothing more.
        s.next()
        for (card in cards) assertEquals(1, map.item(card.key)!!.lessonEncounters)
    }

    @Test
    fun goingBack_recordsNothing_andGoingForwardAgainDoesNotCountTheCardTwice() = runTest {
        fillFrench()
        val s = service()
        val cards = s.today().lesson().cards
        s.next()
        val back = s.previous()
        assertEquals(0, back.lesson().position)
        assertEquals(1, map.item(cards[0].key)!!.lessonEncounters)
        s.next()
        assertEquals(1, map.item(cards[0].key)!!.lessonEncounters) // still one
        assertEquals(1, s.today().lesson().position) // back on the second card
    }

    @Test
    fun goingBackFromTheFirstCard_doesNothing() = runTest {
        fillFrench()
        val s = service()
        s.today()
        assertEquals(0, s.previous().lesson().position)
    }

    @Test
    fun anItemTaughtToday_isNotTaughtAgainTheSameDay() = runTest {
        fillFrench()
        val s = service()
        val taught = s.today().lesson().cards.map { it.term }.toSet()
        repeat(3) { s.next() }
        // Even if a second lesson were made today (the saved one is removed), the taught items are left out.
        store.clear()
        val second = s.today().lesson().cards.map { it.term }
        assertTrue(second.none { it in taught })
        assertEquals(listOf("bonjour"), second) // the only item not taught yet
    }

    @Test
    fun lessonsOverSeveralDays_moveAnItemFromUnknownToLearning() = runTest {
        met("fr", "devis", usefulness = 0.5)
        val key = UnitKey("fr", "devis", UnitType.WORD)
        assertEquals(MasteryStatus.UNKNOWN, map.item(key)!!.masteryState) // 0.5
        repeat(3) {
            service().next()
            now += day
            store.clear() // a new day
        }
        val item = map.item(key)!!
        assertEquals(3, item.lessonEncounters)
        assertEquals(MasteryStatus.LEARNING, item.masteryState) // 0.5 + 12
    }

    // ---- languages ----

    @Test
    fun switchingTheLearningLanguage_makesALessonFromThatLanguagesMap() = runTest {
        fillFrench()
        met("es", "presupuesto", usefulness = 0.6)
        met("es", "antes del mediodía", UnitType.PHRASE, usefulness = 0.8)
        val french = service().today().lesson()
        learning = Languages.Spanish
        val spanish = service().today().lesson()
        assertEquals("es", spanish.language)
        assertTrue(spanish.cards.all { it.key.language == "es" })
        assertTrue(french.cards.all { it.key.language == "fr" })
        assertEquals(2, spanish.cards.size)
    }

    @Test
    fun theMeaningIsInTheLearnersOwnLanguage() = runTest {
        fillFrench()
        native = Languages.Japanese
        assertTrue(service().today().lesson().cards.all { it.meaning!!.endsWith("in ja") && it.meaningLanguage == "ja" })
    }

    // ---- privacy ----

    @Test
    fun aCardHoldsNoMessageText() = runTest {
        fillFrench()
        val fields = (LessonCard::class.java.declaredFields + LessonContext::class.java.declaredFields).map { it.name.lowercase() }
        assertTrue(fields.none { it in setOf("text", "message", "body", "sentence", "original", "sender") })
        val card = org.json.JSONObject(DailyLessonCodec.encode(service().today().lesson())).getJSONArray("cards").getJSONObject(0)
        val keys = card.keys().asSequence().toSet()
        assertEquals(
            setOf("language", "normalized", "type", "term", "kind", "meaning", "meaningLanguage", "timesMet", "lastMetIn", "lastSeen", "help", "reasons", "state"),
            keys,
        ) // exactly these: units, counts and reasons, nothing that could hold a message or a sender
    }
}
