package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.MasteryStatus.FAMILIAR
import com.alterlingua.app.learning.MasteryStatus.LEARNING
import com.alterlingua.app.learning.MasteryStatus.MASTERED
import com.alterlingua.app.learning.MasteryStatus.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mastery calculation, as a table of evidence and results that can be read and checked by hand. */
class MasteryCalculatorTest {

    private val calculator = MasteryCalculator()
    private val now = 5_000L * MasteryCalculator.DAY_MILLIS

    /** Evidence "seen just now", so the inactivity rule stays out of the way unless a test asks for it. */
    private fun eval(
        exposures: Int = 0,
        lessons: Int = 0,
        correct: Int = 0,
        incorrect: Int = 0,
        help: Int = 0,
        daysSinceSeen: Int = 0,
    ) = calculator.evaluate(
        MasteryEvidence(exposures, lessons, correct, incorrect, help, lastSeenMillis = now - daysSinceSeen * MasteryCalculator.DAY_MILLIS),
        now,
    )

    private fun assertState(expected: MasteryStatus, evaluation: MasteryEvaluation, score: Double? = null) {
        assertEquals(evaluation.explain(), expected, evaluation.state)
        if (score != null) assertEquals(score, evaluation.score, 0.0001)
    }

    // ---- the progression UNKNOWN -> LEARNING -> FAMILIAR -> MASTERED ----

    @Test
    fun anItemNeverMet_isUnknown() = assertState(UNKNOWN, eval(), score = 0.0)

    @Test
    fun theJourneyFromUnknownToMastered_stepByStep() {
        // Just met it a few times: still UNKNOWN (0.5 points per exposure).
        assertState(UNKNOWN, eval(exposures = 3), score = 1.5)
        // A lesson introduces it: 3 exposures + 1 lesson = 5.5, still below 10.
        assertState(UNKNOWN, eval(exposures = 3, lessons = 1), score = 5.5)
        // A second lesson: 3 + 2 lessons = 9.5, one lesson short ...
        assertState(UNKNOWN, eval(exposures = 3, lessons = 2), score = 9.5)
        // ... and a third makes it LEARNING (13.5).
        assertState(LEARNING, eval(exposures = 3, lessons = 3), score = 13.5)
        // The first correct recognition (+15) keeps it LEARNING: 28.5.
        assertState(LEARNING, eval(exposures = 3, lessons = 3, correct = 1), score = 28.5)
        // A second correct one reaches 43.5 with 2 correct recognitions: FAMILIAR.
        assertState(FAMILIAR, eval(exposures = 3, lessons = 3, correct = 2), score = 43.5)
        // Two more correct recognitions (4 in total) reach 73.5: still FAMILIAR, just below 75.
        assertState(FAMILIAR, eval(exposures = 3, lessons = 3, correct = 4), score = 73.5)
        // One more meeting (+0.5) and one more lesson (+4) reach 78 with 4 correct recognitions at 100% accuracy: MASTERED.
        assertState(MASTERED, eval(exposures = 4, lessons = 4, correct = 4), score = 78.0)
    }

    @Test
    fun exactBoundaries() {
        assertState(UNKNOWN, eval(exposures = 19), score = 9.5)
        assertState(LEARNING, eval(exposures = 20), score = 10.0) // 10 is LEARNING
        assertState(FAMILIAR, eval(lessons = 2, correct = 2, exposures = 4), score = 40.0) // exactly 40, with 2 correct
        assertState(MASTERED, eval(lessons = 4, correct = 4), score = 76.0) // 16 + 60, with 4 correct at 100% accuracy
    }

    // ---- exposure alone is never proof ----

    @Test
    fun meetingAnItemManyTimes_neverGetsPastLearning() {
        assertState(LEARNING, eval(exposures = 20), score = 10.0)
        for (exposures in listOf(24, 100, 10_000)) {
            val result = eval(exposures = exposures)
            assertState(LEARNING, result, score = 12.0) // exposure counts for at most 12 points, however often it is met
        }
    }

    @Test
    fun manyLessonsAndExposuresWithoutRecognition_cannotBeFamiliar() {
        val result = eval(exposures = 100, lessons = 20, correct = 1) // 12 + 16 + 15 = 43 points but only one correct recognition
        assertEquals(43.0, result.score, 0.0001)
        assertState(LEARNING, result) // the gate: FAMILIAR needs 2 correct recognitions
    }

    @Test
    fun aHighScoreWithPoorAccuracy_isNotMastered() {
        val result = eval(exposures = 100, lessons = 20, correct = 5, incorrect = 2) // 12 + 16 + 75 - 24 = 79
        assertEquals(79.0, result.score, 0.0001)
        assertState(FAMILIAR, result) // 5 of 7 is 71%: the gate wants 80%
    }

    @Test
    fun masteryNeedsAtLeastFourCorrectRecognitions() {
        val result = eval(exposures = 100, lessons = 20, correct = 3) // 12 + 16 + 45 = 73, and only 3 correct
        assertState(FAMILIAR, result)
        assertState(MASTERED, eval(exposures = 100, lessons = 20, correct = 4)) // 12 + 16 + 60 = 88
    }

    // ---- regression, where justified ----

    @Test
    fun incorrectRecognitionsPullAMasteredItemBackDown_oneStateAtATime() {
        val base = eval(exposures = 10, lessons = 2, correct = 5) // 5 + 8 + 75 = 88
        assertState(MASTERED, base, score = 88.0)
        assertState(MASTERED, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 1), score = 76.0) // one slip: still mastered
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 2), score = 64.0) // two slips
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 3), score = 52.0)
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 4), score = 40.0)
        assertState(LEARNING, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 5), score = 28.0)
        assertState(LEARNING, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 6), score = 16.0)
        assertState(UNKNOWN, eval(exposures = 10, lessons = 2, correct = 5, incorrect = 8), score = 0.0) // never below 0
    }

    @Test
    fun askingForTranslationHelp_countsAgainstKnowingIt_butOnlyUpToAPoint() {
        val familiar = eval(exposures = 4, lessons = 2, correct = 2) // 2 + 8 + 30 = 40
        assertState(FAMILIAR, familiar, score = 40.0)
        assertState(LEARNING, eval(exposures = 4, lessons = 2, correct = 2, help = 1), score = 37.0) // 1 help request: below 40
        assertEquals(25.0, eval(exposures = 4, lessons = 2, correct = 2, help = 5).score, 0.0001) // 5 requests: -15
        assertEquals(25.0, eval(exposures = 4, lessons = 2, correct = 2, help = 50).score, 0.0001) // capped at -15
    }

    @Test
    fun aLongTimeWithoutMeetingAnItem_lowersIt() {
        val fresh = eval(exposures = 10, lessons = 2, correct = 5) // 88
        assertState(MASTERED, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 60), score = 88.0) // no penalty up to 60 days
        assertState(MASTERED, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 61), score = 78.0) // -10 for the first 30 days beyond
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 91), score = 68.0)
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 151), score = 48.0) // capped at -40
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 3_000), score = 48.0)
        assertTrue(fresh.score > 48.0)
        assertState(UNKNOWN, eval(exposures = 6, lessons = 2, correct = 1, daysSinceSeen = 400), score = 0.0) // 3 + 8 + 15 = 26 -> 0 after -40
    }

    @Test
    fun anUnknownLastSeenTime_meansNoInactivityPenalty() {
        val result = calculator.evaluate(MasteryEvidence(exposures = 10, lessonEncounters = 2, correctRecognitions = 5, lastSeenMillis = 0), now)
        assertEquals(88.0, result.score, 0.0001)
        assertState(MASTERED, result)
    }

    @Test
    fun anItemMeetingItAgain_recovers() {
        // Unseen for a long time it drops; met again (last seen now), the penalty is gone.
        assertState(FAMILIAR, eval(exposures = 10, lessons = 2, correct = 5, daysSinceSeen = 91))
        assertState(MASTERED, eval(exposures = 11, lessons = 2, correct = 5, daysSinceSeen = 0))
    }

    // ---- properties of the calculation ----

    @Test
    fun theScoreStaysBetween0And100() {
        assertEquals(100.0, eval(exposures = 10_000, lessons = 10_000, correct = 10_000).score, 0.0)
        assertEquals(0.0, eval(incorrect = 10_000, help = 10_000).score, 0.0)
    }

    @Test
    fun theSameEvidenceAlwaysGivesTheSameResult() {
        val evidence = MasteryEvidence(12, 3, 4, 1, 2, lastSeenMillis = now - 70 * MasteryCalculator.DAY_MILLIS)
        val a = calculator.evaluate(evidence, now)
        val b = MasteryCalculator().evaluate(evidence.copy(), now)
        assertEquals(a, b)
    }

    @Test
    fun theRulesCanBeReadAndChanged_andTheCalculationFollowsThem() {
        val strict = MasteryCalculator(MasteryRules(correctPoints = 10.0, masteredMinCorrect = 6))
        val evidence = MasteryEvidence(0, 0, 5, 0, 0, now)
        assertEquals(50.0, strict.evaluate(evidence, now).score, 0.0001) // 5 x 10
        assertEquals(75.0, calculator.evaluate(evidence, now).score, 0.0001) // 5 x 15 with the default rules
    }

    @Test
    fun onlyTheFourStatesExist_andTheyAreNotLanguageLevels() {
        assertEquals(listOf("UNKNOWN", "LEARNING", "FAMILIAR", "MASTERED"), MasteryStatus.entries.map { it.name })
    }

    // ---- explanation ----

    @Test
    fun everyResultCanBeExplained() {
        val text = eval(exposures = 10, lessons = 2, correct = 3, incorrect = 1, help = 2).explain()
        assertTrue(text, "Score" in text && "FAMILIAR" in text)
        assertTrue("+5.0 for meeting it 10 times" in text)
        assertTrue("+8.0 for 2 lesson encounters" in text)
        assertTrue("+45.0 for 3 correct recognitions" in text)
        assertTrue("-12.0 for 1 incorrect recognitions" in text)
        assertTrue("-6.0 for 2 translation-help requests" in text)
        assertTrue("Next: MASTERED needs" in text)
    }

    @Test
    fun theNextStepSaysWhatIsMissing() {
        assertTrue(eval().nextStep!!.contains("LEARNING"))
        val learning = eval(exposures = 20, lessons = 4, correct = 1) // 10 + 16 + 15 = 41 points, but one correct recognition
        assertTrue(learning.nextStep!!.contains("at least 2 correct"))
        val familiar = eval(exposures = 100, lessons = 20, correct = 3, incorrect = 1)
        assertTrue(familiar.nextStep!!.contains("at least 4 correct") && familiar.nextStep!!.contains("accuracy of at least 80%"))
        assertNull(eval(exposures = 10, lessons = 2, correct = 5).nextStep) // mastered: nothing next
        assertNotNull(eval(exposures = 3).explain())
    }
}
