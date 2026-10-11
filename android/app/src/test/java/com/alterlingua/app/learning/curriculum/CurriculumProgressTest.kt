package com.alterlingua.app.learning.curriculum

import org.junit.Assert.assertEquals
import org.junit.Test

class CurriculumProgressTest {

    @Test
    fun goalToday_isTheDailyGoalPlusAnyBacklog() {
        assertEquals(3, CurriculumProgress().goalToday(3))
        assertEquals(7, CurriculumProgress(backlog = 4).goalToday(3))
    }

    @Test
    fun remainingToday_isZero_onceTheGoalIsMet_neverNegative() {
        assertEquals(1, CurriculumProgress(completedToday = 2).remainingToday(3))
        assertEquals(0, CurriculumProgress(completedToday = 3).remainingToday(3))
        assertEquals(0, CurriculumProgress(completedToday = 5).remainingToday(3))
    }
}
