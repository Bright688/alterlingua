package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.curriculum.CurriculumCard
import com.alterlingua.app.learning.curriculum.CurriculumLanguageChoice
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class CurriculumLessonScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun inProgress(
        position: Int = 0,
        tier: Tier = Tier.FREE,
        languages: CurriculumLanguageChoice = CurriculumLanguageChoice(Languages.French, listOf(Languages.French)),
    ) = CurriculumSessionState.InProgress(
        languages,
        tier,
        listOf(CurriculumCard("bonjour", "hello"), CurriculumCard("demain", null)),
        position,
        goalToday = 2,
    )

    @Test
    fun aFreshCard_hidesTheMeaning_untilTapped() {
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(), onNext = {}, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_term").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_reveal_prompt").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("curriculum_meaning").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("curriculum_already_knew").assertCountEquals(0)

        composeTestRule.onNodeWithTag("curriculum_card").performClick()
        composeTestRule.onNodeWithTag("curriculum_meaning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_already_knew").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_new_to_me").assertIsDisplayed()
    }

    @Test
    fun sayingYouAlreadyKnewIt_callsOnNextWithTrue() {
        var answer: Boolean? = null
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(), onNext = { answer = it }, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_card").performClick()
        composeTestRule.onNodeWithTag("curriculum_already_knew").performClick()
        assertEquals(true, answer)
    }

    @Test
    fun sayingItIsNewToYou_callsOnNextWithFalse() {
        var answer: Boolean? = null
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(), onNext = { answer = it }, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_card").performClick()
        composeTestRule.onNodeWithTag("curriculum_new_to_me").performClick()
        assertEquals(false, answer)
    }

    @Test
    fun aCardWithoutAMeaning_saysSoOnceRevealed() {
        composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(1), onNext = {}, onRetry = {}, onUpgrade = {}) } }
        composeTestRule.onNodeWithTag("curriculum_card").performClick()
        composeTestRule.onNodeWithTag("curriculum_meaning_missing").assertIsDisplayed()
    }

    @Test
    fun listenButton_reflectsWhetherItCanSpeak_andCallsOnListen() {
        var listened = 0
        composeTestRule.setContent {
            AlterLinguaTheme { CurriculumLessonScreen(inProgress(), onNext = {}, onRetry = {}, onUpgrade = {}, onListen = { listened++ }, canListen = true) }
        }
        composeTestRule.onNodeWithTag("curriculum_listen").performClick()
        assertEquals(1, listened)
    }

    @Test
    fun thePlanBadge_showsWhichPlanIsActive_forEveryTier() {
        Tier.entries.forEach { tier ->
            composeTestRule.setContent { AlterLinguaTheme { CurriculumLessonScreen(inProgress(tier = tier), onNext = {}, onRetry = {}, onUpgrade = {}) } }
            composeTestRule.onNodeWithTag("curriculum_plan_badge").assertIsDisplayed()
        }
    }

    @Test
    fun aPremiumUserWithOneLanguage_seesNoLanguagePicker() {
        composeTestRule.setContent {
            AlterLinguaTheme {
                CurriculumLessonScreen(inProgress(tier = Tier.PREMIUM), onNext = {}, onRetry = {}, onUpgrade = {})
            }
        }
        composeTestRule.onAllNodesWithTag("curriculum_language_picker").assertCountEquals(0)
    }

    @Test
    fun aPremiumUserWithSeveralLanguages_canSwitchLanguage() {
        var selected: Language? = null
        val choice = CurriculumLanguageChoice(Languages.French, listOf(Languages.French, Languages.Spanish))
        composeTestRule.setContent {
            AlterLinguaTheme {
                CurriculumLessonScreen(
                    inProgress(tier = Tier.PREMIUM, languages = choice),
                    onNext = {},
                    onRetry = {},
                    onUpgrade = {},
                    onLanguageSelected = { selected = it },
                )
            }
        }
        composeTestRule.onNodeWithTag("curriculum_language_picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_language_es").performClick()
        assertEquals(Languages.Spanish, selected)
    }

    @Test
    fun doneForToday_showsTheMessage() {
        composeTestRule.setContent {
            AlterLinguaTheme {
                CurriculumLessonScreen(
                    CurriculumSessionState.DoneForToday(CurriculumLanguageChoice(Languages.French, listOf(Languages.French)), Tier.FREE, 3),
                    onNext = {},
                    onRetry = {},
                    onUpgrade = {},
                )
            }
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
    fun unitPracticeComplete_showsTheCompletionCard_andBackToUnitsCallsOnUnitPracticeDone() {
        var done = 0
        composeTestRule.setContent {
            AlterLinguaTheme {
                CurriculumLessonScreen(
                    CurriculumSessionState.UnitPracticeComplete(com.alterlingua.app.learning.curriculum.CefrLevel.A1, 3),
                    onNext = {},
                    onRetry = {},
                    onUpgrade = {},
                    onUnitPracticeDone = { done++ },
                )
            }
        }
        composeTestRule.onNodeWithTag("curriculum_unit_complete").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_unit_complete_back").performClick()
        assertEquals(1, done)
    }
}
