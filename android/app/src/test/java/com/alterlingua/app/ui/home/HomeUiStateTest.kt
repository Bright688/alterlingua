package com.alterlingua.app.ui.home

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.progress.DependenceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home figures start at zero: no sample numbers are shown as if they were the user's. */
class HomeUiStateTest {
    @Test
    fun theStartingStateHasNoInventedFigures() {
        val state = HomeUiState.empty(Languages.Spanish)
        assertEquals(0, state.translationsThisWeek)
        assertEquals(0, state.newWordsThisWeek)
        assertEquals(0, state.languageMap.total)
        assertEquals(DependenceStatus.NoData, state.dependence)
    }
}
