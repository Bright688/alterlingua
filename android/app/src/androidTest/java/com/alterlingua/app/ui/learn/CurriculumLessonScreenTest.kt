package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.curriculum.CurriculumCard
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class CurriculumLessonScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun inProgress(position: Int = 0) = CurriculumSessionState.InProgress(
        Languages.French,
        listOf(CurriculumCard("bonjour", "hello"), CurriculumCard("demain", null)),
        position,
        goalToday = 2,
    )

    @Test
    fun aCard_showsTermAndMeaning_andLearnedMovesOn() {
        var next = 0
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(), onNext = { next++ }, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_term").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_meaning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_learned").performClick()
        assertEquals(1, next)
    }

    @Test
    fun aCardWithoutAMeaning_saysSo() {
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(1), onNext = {}, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_meaning_missing").assertIsDisplayed()
    }

    @Test
    fun doneForToday_showsTheMessage() {
        composeTestRule.setContent {
            AlterLinguaTheme { CurriculumLessonScreen(CurriculumSessionState.DoneForToday(Languages.French, 3), onNext = {}, onRetry = {}, onUpgrade = {}) }
        }
        composeTestRule.onNodeWithTag("curriculum_done").assertIsDisplayed()
    }

    @Test
    fun theTrialLimit_showsThePaywall_andUpgradeIsOffered() {
        var upgrades = 0
        composeTestRule.setContent {
            AlterLinguaTheme { CurriculumLessonScreen(CurriculumSessionState.TrialLimitReached(50), onNext = {}, onRetry = {}, onUpgrade = { upgrades++ }) }
        }
        composeTestRule.onNodeWithTag("curriculum_paywall").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_upgrade").performClick()
        assertEquals(1, upgrades)
    }

    @Test
    fun whenUnavailable_retryIsOffered() {
        var retries = 0
        composeTestRule.setContent {
            AlterLinguaTheme { CurriculumLessonScreen(CurriculumSessionState.Unavailable, onNext = {}, onRetry = { retries++ }, onUpgrade = {}) }
        }
        composeTestRule.onNodeWithTag("curriculum_unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun comingSoonDialog_showsAndDismisses() {
        var dismissed = 0
        composeTestRule.setContent { AlterLinguaTheme { ComingSoonDialog(onDismiss = { dismissed++ }) } }
        composeTestRule.onNodeWithTag("coming_soon_dialog").assertIsDisplayed()
    }
}
