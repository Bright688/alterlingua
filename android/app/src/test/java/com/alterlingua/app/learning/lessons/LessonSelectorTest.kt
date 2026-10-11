package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.map.LanguageMapItem
import com.alterlingua.app.learning.map.MasteryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSelectorTest {

    private val selector = LessonSelector()
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 1_000L * day

    private fun item(
        text: String,
        type: UnitType = UnitType.WORD,
        exposures: Int = 1,
        usefulness: Double = 0.4,
        help: Int = 0,
        lessons: Int = 0,
        correct: Int = 0,
        incorrect: Int = 0,
        seenHoursAgo: Long = 1,
        taughtHoursAgo: Long? = null,
        language: String = "fr",
    ) = LanguageMapItem(
        language = language, normalized = text, type = type, displayForm = text, exposureCount = exposures, helpRequests = help,
        lessonEncounters = lessons, correctRecognitions = correct, incorrectRecognitions = incorrect,
        firstSeen = now - seenHoursAgo * hour, lastSeen = now - seenHoursAgo * hour, usefulness = usefulness,
        lastLessonAt = taughtHoursAgo?.let { now - it * hour } ?: 0,
    )

    private fun chosen(vararg items: LanguageMapItem, selector: LessonSelector = this.selector) =
        selector.select(items.toList(), now).chosen.map { it.item.normalized }

    private fun score(item: LanguageMapItem) = selector.select(listOf(item), now).ranked.single().score

    // ---- not simply the most frequent ----

    @Test
    fun theThreeMostFrequentWordsDoNotSimplyWin() {
        val items = arrayOf(
            item("bonjour", exposures = 40, usefulness = 0.30, correct = 5, lessons = 4), // very frequent, and already mastered
            item("merci", exposures = 30, usefulness = 0.30, seenHoursAgo = 72), // frequent but low value
            item("demain", exposures = 25, usefulness = 0.30), // frequent but low value
            item("avant midi", UnitType.PHRASE, exposures = 2, usefulness = 0.80, help = 2), // rare, useful, the user asked for it
            item("au courant", UnitType.EXPRESSION, exposures = 1, usefulness = 0.90),
            item("devis", exposures = 3, usefulness = 0.40, help = 1),
        )
        val lesson = chosen(*items)
        assertEquals(setOf("avant midi", "au courant", "devis"), lesson.toSet())
        assertFalse("the most frequent words were not chosen", lesson.any { it in setOf("bonjour", "merci", "demain") })
    }

    @Test
    fun frequencyCountsForLittle_itStopsAtTenPoints() {
        fun frequencyPoints(exposures: Int) = selector.select(listOf(item("a1", exposures = exposures)), now).ranked.single()
            .signals.single { it.kind == SignalKind.FREQUENCY }.points
        assertEquals(4.0, frequencyPoints(1), 0.0001)
        assertEquals(8.0, frequencyPoints(3), 0.0001)
        assertEquals(10.0, frequencyPoints(7), 0.0001) // 4 x log2(8) = 12, capped at 10
        assertEquals(10.0, frequencyPoints(1_000), 0.0001)
        assertEquals(score(item("a1", exposures = 24)), score(item("a1", exposures = 1_000)), 0.0001) // nothing more to gain
    }

    // ---- each signal, explained ----

    @Test
    fun everyChoiceCanBeExplained_signalBySignal() {
        val result = selector.select(listOf(item("avant midi", UnitType.PHRASE, exposures = 3, usefulness = 0.8, help = 2)), now)
        val candidate = result.chosen.single()
        assertEquals(SignalKind.entries.toSet(), candidate.signals.map { it.kind }.toSet())
        assertEquals(candidate.score, candidate.signals.sumOf { it.points }, 0.0001)
        val text = candidate.explain()
        assertTrue(text, "useful phrase" in text && "met 3 times" in text && "seen in the last day" in text && "new to you" in text)
        assertTrue("you asked for its translation 2 times" in text && "not taught in a lesson yet" in text)
    }

    @Test
    fun aUsefulItemOutranksAPlainOne() {
        assertTrue(score(item("phrase", UnitType.PHRASE, usefulness = 0.8)) > score(item("word", usefulness = 0.3)))
    }

    @Test
    fun translationHelpRequestsRaiseAnItem_upToACap() {
        val none = score(item("x"))
        val one = score(item("x", help = 1))
        val three = score(item("x", help = 3))
        val many = score(item("x", help = 30))
        assertEquals(8.0, one - none, 0.0001)
        assertEquals(24.0, three - none, 0.0001)
        assertEquals(three, many, 0.0001) // capped at 24
    }

    @Test
    fun recentItemsRankAboveOlderOnes() {
        val fresh = score(item("x", seenHoursAgo = 2))
        val threeDays = score(item("x", seenHoursAgo = 60))
        val week = score(item("x", seenHoursAgo = 150))
        val old = score(item("x", seenHoursAgo = 24 * 40))
        assertTrue(fresh > threeDays && threeDays > week && week > old)
        assertEquals(12.0, fresh - old, 0.0001)
    }

    @Test
    fun whatIsStillBeingLearnedComesBeforeWhatIsNew_andBothBeforeWhatIsFamiliar() {
        val learning = score(item("x", lessons = 3)) // 3 lessons: 12.5 points, LEARNING
        val unknown = score(item("x"))
        val familiar = score(item("x", lessons = 3, correct = 2)) // 42.5 points: FAMILIAR
        assertTrue("learning $learning > unknown $unknown", learning > unknown)
        assertTrue("unknown $unknown > familiar $familiar", unknown + 4 > familiar) // familiar earns 6 for need, but it is not taught yet either way
    }

    @Test
    fun anItemThatOnlyReachedLearningByBeingMetOften_isStillNewToTheLearner() {
        val often = selector.select(listOf(item("x", exposures = 24)), now).ranked.single() // 12 points: LEARNING by exposure alone
        val need = often.signals.single { it.kind == SignalKind.MASTERY_NEED }
        assertEquals(10.0, need.points, 0.0001)
        assertTrue("new to you" in need.note)
        val studied = selector.select(listOf(item("x", exposures = 24, lessons = 1)), now).ranked.single()
        assertEquals(14.0, studied.signals.single { it.kind == SignalKind.MASTERY_NEED }.points, 0.0001)
    }

    @Test
    fun masteredItemsAreLeftOut() {
        val result = selector.select(listOf(item("bonjour", lessons = 4, correct = 5, exposures = 40)), now)
        assertTrue(result.chosen.isEmpty())
        assertEquals(ExclusionReason.MASTERED, result.excluded.single().second)
    }

    @Test
    fun aMasteredItemThatWasNotSeenForALongTimeQualifiesAgain() {
        // Mastered, but unseen for 200 days: its evaluated state has fallen, so it is worth a review.
        val stale = item("bonjour", lessons = 4, correct = 5, exposures = 10, seenHoursAgo = 24 * 200)
        assertEquals(listOf("bonjour"), chosen(stale))
    }

    // ---- repetition value ----

    @Test
    fun anItemNeverTaughtGetsANewLessonBonus_aDueReviewGetsAnother() {
        val never = score(item("x", lessons = 3))
        val due = score(item("x", lessons = 3, taughtHoursAgo = 24 * 3)) // LEARNING, taught 3 days ago: review due
        val notYet = score(item("x", lessons = 3, taughtHoursAgo = 30)) // taught yesterday: not due yet
        assertEquals(6.0, never - notYet, 0.0001)
        assertEquals(4.0, due - never, 0.0001) // review (10) instead of new (6)
        assertTrue(due > notYet)
    }

    @Test
    fun nothingIsTaughtTwiceInADay() {
        val result = selector.select(listOf(item("x", taughtHoursAgo = 5), item("y", taughtHoursAgo = 19)), now)
        assertTrue(result.chosen.isEmpty())
        assertEquals(listOf(ExclusionReason.TAUGHT_RECENTLY, ExclusionReason.TAUGHT_RECENTLY), result.excluded.map { it.second })
        assertEquals(listOf("z"), chosen(item("z", taughtHoursAgo = 21)))
    }

    // ---- how many, and the shape of the lesson ----

    @Test
    fun aLessonHasAboutThreeItems() {
        val items = (1..10).map { item("mot$it", usefulness = 0.4 + it / 100.0) }.toTypedArray()
        assertEquals(3, chosen(*items).size)
        assertEquals(5, chosen(*items, selector = LessonSelector(SelectionConfig(dailyTarget = 5))).size)
    }

    @Test
    fun withFewerThanThreeCandidates_thereAreFewerItems_andWithNoneThereIsNoLesson() {
        assertEquals(2, chosen(item("un"), item("deux")).size)
        assertTrue(chosen().isEmpty())
    }

    @Test
    fun aLessonIsNotThreeWordsWhenAPhraseIsAvailable() {
        val lesson = chosen(
            item("un", exposures = 9, usefulness = 0.50), item("deux", exposures = 9, usefulness = 0.49), item("trois", exposures = 9, usefulness = 0.48),
            item("avant midi", UnitType.PHRASE, exposures = 1, usefulness = 0.30),
        )
        assertEquals(3, lesson.size)
        assertTrue("a phrase joined the lesson: $lesson", "avant midi" in lesson)
        assertEquals(2, lesson.count { it != "avant midi" })
    }

    @Test
    fun whenOnlyWordsExist_threeWordsAreStillChosen() {
        assertEquals(3, chosen(item("un"), item("deux"), item("trois"), item("quatre")).size)
    }

    @Test
    fun overlappingItemsAreNotTaughtTogether() {
        val lesson = chosen(
            item("envoyer le devis", UnitType.PHRASE, usefulness = 0.7),
            item("devis", usefulness = 0.6),
            item("avant midi", UnitType.PHRASE, usefulness = 0.5),
            item("réunion", usefulness = 0.4),
        )
        assertFalse("devis" in lesson && "envoyer le devis" in lesson)
        assertEquals(3, lesson.size)
        // The same rule for languages written without spaces.
        val japanese = chosen(item("お送りします", UnitType.PHRASE, usefulness = 0.7, language = "ja"), item("お送り", usefulness = 0.6, language = "ja"), item("正午", usefulness = 0.5, language = "ja"))
        assertFalse("お送り" in japanese && "お送りします" in japanese)
    }

    @Test
    fun anItemWithNothingGoingForItIsBelowTheMinimum() {
        val weak = item("mot", usefulness = 0.05, exposures = 1, seenHoursAgo = 24 * 60, taughtHoursAgo = 25)
        val result = selector.select(listOf(weak), now)
        assertEquals(ExclusionReason.BELOW_MINIMUM, result.excluded.single().second)
        assertTrue(result.chosen.isEmpty())
    }

    @Test
    fun olderRowsWithoutUsefulness_areRankedByTheirKindOfUnit() {
        val expression = score(item("e", UnitType.EXPRESSION, usefulness = 0.0))
        val phrase = score(item("p", UnitType.PHRASE, usefulness = 0.0))
        val word = score(item("w", UnitType.WORD, usefulness = 0.0))
        assertTrue(expression > phrase && phrase > word)
    }

    // ---- determinism ----

    @Test
    fun theSameMapAndTimeAlwaysGiveTheSameLesson_whateverTheInputOrder() {
        val items = (1..12).map { item("mot$it", exposures = it, usefulness = 0.3 + (it % 4) / 10.0, help = it % 3, seenHoursAgo = it * 5L) }
        val forwards = chosen(*items.toTypedArray())
        val backwards = chosen(*items.reversed().toTypedArray())
        val shuffled = chosen(*items.shuffled(java.util.Random(7)).toTypedArray())
        assertEquals(forwards, backwards)
        assertEquals(forwards, shuffled)
        assertEquals(forwards, chosen(*items.toTypedArray()))
    }

    @Test
    fun aTieIsBrokenByUsefulness_thenRecency_thenAlphabetically() {
        val a = item("beta", usefulness = 0.4)
        val b = item("alpha", usefulness = 0.4)
        assertEquals(listOf("alpha", "beta"), chosen(a, b))
    }

    @Test
    fun theRankedListShowsEverythingThatQualified() {
        val result = selector.select((1..6).map { item("mot$it", usefulness = 0.3 + it / 20.0) }, now)
        assertEquals(6, result.ranked.size)
        assertEquals(3, result.chosen.size)
        assertEquals(result.ranked.first(), result.chosen.first())
        assertNotNull(result.ranked.last().explain())
    }

    @Test
    fun theMasteryUsedIsTheEvaluatedOne_notTheStoredOne() {
        // A stored state of MASTERED must not matter: the evidence decides.
        val stored = item("x", lessons = 0, correct = 0).copy(masteryState = com.alterlingua.app.learning.MasteryStatus.MASTERED, masteryScore = 90.0)
        assertEquals(listOf("x"), chosen(stored))
        assertEquals(MasteryCalculator.DAY_MILLIS, day)
    }
}
