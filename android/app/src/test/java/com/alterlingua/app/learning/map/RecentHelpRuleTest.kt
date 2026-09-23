package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentHelpRuleTest {
    private val calculator = MasteryCalculator()
    private val day = MasteryCalculator.DAY_MILLIS
    private val now = 10_000L * day

    private fun mastered(lastHelp: Long) = MasteryEvidence(
        exposures = 12, lessonEncounters = 4, correctRecognitions = 5, helpRequests = if (lastHelp > 0) 1 else 0,
        lastSeenMillis = now, lastHelpMillis = lastHelp,
    )

    @Test fun masteredWithNoHelpRequest_staysMastered() {
        assertEquals(MasteryStatus.MASTERED, calculator.evaluate(mastered(0), now).state)
    }

    @Test fun aRecentHelpRequest_holdsAMasteredWordAtFamiliar() {
        assertEquals(MasteryStatus.FAMILIAR, calculator.evaluate(mastered(now - day), now).state)
        assertEquals(MasteryStatus.FAMILIAR, calculator.evaluate(mastered(now), now).state)
    }

    @Test fun afterTheWindow_theWordCanBeMasteredAgain() {
        assertEquals(MasteryStatus.FAMILIAR, calculator.evaluate(mastered(now - 13 * day), now).state)
        assertEquals(MasteryStatus.MASTERED, calculator.evaluate(mastered(now - 14 * day), now).state)
        assertEquals(MasteryStatus.MASTERED, calculator.evaluate(mastered(now - 90 * day), now).state)
    }

    @Test fun theExplanationSaysWhy() {
        assertTrue(calculator.evaluate(mastered(now - day), now).explain().contains("asked for its translation recently"))
    }

    @Test fun helpDoesNotChangeTheStateOfWordsThatAreNotMasteredAnyway() {
        val learning = MasteryEvidence(exposures = 20, lessonEncounters = 2, lastSeenMillis = now, lastHelpMillis = now, helpRequests = 1)
        assertEquals(MasteryStatus.LEARNING, calculator.evaluate(learning, now).state)
    }
}
