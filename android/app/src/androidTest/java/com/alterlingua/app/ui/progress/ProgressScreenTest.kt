package com.alterlingua.app.ui.progress

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.progress.DependenceStatus
import com.alterlingua.app.learning.progress.MasterySnapshot
import com.alterlingua.app.learning.progress.ProgressReport
import com.alterlingua.app.learning.progress.ProgressTotals
import com.alterlingua.app.learning.progress.WeekStats
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Runs on a device or emulator. */
class ProgressScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun week(n: Int, percent: Int?) = WeekStats(n, LocalDate.of(2026, 8, 3).plusWeeks(n.toLong()), 50, 30, 5, 2, 3, 4, 3, emptySet(), MasterySnapshot(20, 10, 6, 2), percent)

    private fun report(dependence: DependenceStatus, ready: Boolean, weeks: List<WeekStats>) = ProgressReport(
        "fr", ProgressTotals(20, 4, 10, 6, 2), dependence, weeks, weeks.last(), ready, ready, ready,
    )

    private fun show(report: ProgressReport) {
        composeTestRule.setContent { AlterLinguaTheme { ProgressScreen(ProgressUiState.Ready(Languages.French, report)) } }
    }

    @Test fun newUser_showsTheFourCountsAndEmptyStates() {
        show(report(DependenceStatus.NoData, false, listOf(week(1, null))))
        composeTestRule.onNodeWithTag("progress_totals").assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_dependence_empty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_mastery_empty").assertIsDisplayed()
    }

    @Test fun collecting_showsProgressTowardsABaseline_notAPercentage() {
        show(report(DependenceStatus.Collecting(12, 20, 0, 2), false, listOf(week(1, null))))
        composeTestRule.onNodeWithTag("progress_dependence_collecting").assertIsDisplayed()
    }

    @Test fun ready_showsTheWeeklyFigures_andTheDefinitionOnRequest() {
        val weeks = listOf(week(1, 94), week(4, 71), week(8, 49))
        show(report(DependenceStatus.Ready(weeks), true, weeks))
        composeTestRule.onNodeWithTag("progress_dependence_ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_dependence_change").assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_dependence_how").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_dependence_definition").performScrollTo().assertIsDisplayed()
    }
}
