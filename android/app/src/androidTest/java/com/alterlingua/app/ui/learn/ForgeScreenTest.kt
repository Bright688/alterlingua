package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.curriculum.CefrLevel
import com.alterlingua.app.learning.curriculum.CurriculumLanguageChoice
import com.alterlingua.app.learning.curriculum.CurriculumLevelRange
import com.alterlingua.app.learning.curriculum.CurriculumUnitRange
import com.alterlingua.app.learning.curriculum.ForgeLevelState
import com.alterlingua.app.learning.curriculum.ForgeLevelSummary
import com.alterlingua.app.learning.curriculum.ForgeState
import com.alterlingua.app.learning.curriculum.ForgeUnitSummary
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class ForgeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val choice = CurriculumLanguageChoice(Languages.French, listOf(Languages.French))

    @Test
    fun levelsScreen_showsEveryLevel_withTheCurrentOneMarked() {
        val levels = ForgeState.Levels(
            choice,
            Tier.STANDARD,
            listOf(
                ForgeLevelSummary(CurriculumLevelRange(CefrLevel.A1, 0, 500), isCurrent = false),
                ForgeLevelSummary(CurriculumLevelRange(CefrLevel.A2, 500, 1000), isCurrent = true),
            ),
        )
        composeTestRule.setContent { AlterLinguaTheme { ForgeLevelsScreen(levels, onOpenLevel = {}, onContinue = {}, onRetry = {}) } }
        composeTestRule.onNodeWithTag("forge_level_A1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forge_level_A2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forge_continue").assertIsDisplayed()
    }

    @Test
    fun levelsScreen_tappingALevel_callsOnOpenLevel() {
        var opened: CurriculumLevelRange? = null
        val range = CurriculumLevelRange(CefrLevel.A1, 0, 500)
        val levels = ForgeState.Levels(choice, Tier.STANDARD, listOf(ForgeLevelSummary(range, isCurrent = false)))
        composeTestRule.setContent { AlterLinguaTheme { ForgeLevelsScreen(levels, onOpenLevel = { opened = it }, onContinue = {}, onRetry = {}) } }
        composeTestRule.onNodeWithTag("forge_level_A1").performClick()
        assertEquals(range, opened)
    }

    @Test
    fun levelsScreen_backButton_callsOnBack() {
        var back = 0
        composeTestRule.setContent {
            AlterLinguaTheme {
                ForgeLevelsScreen(ForgeState.Levels(choice, Tier.FREE, emptyList()), onOpenLevel = {}, onContinue = {}, onRetry = {}, onBack = { back++ })
            }
        }
        composeTestRule.onNodeWithTag("forge_back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun levelsScreen_continueButton_callsOnContinue() {
        var continued = 0
        composeTestRule.setContent {
            AlterLinguaTheme {
                ForgeLevelsScreen(ForgeState.Levels(choice, Tier.FREE, emptyList()), onOpenLevel = {}, onContinue = { continued++ }, onRetry = {})
            }
        }
        composeTestRule.onNodeWithTag("forge_continue").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun levelsScreen_whenUnavailable_retryIsOffered() {
        var retries = 0
        composeTestRule.setContent {
            AlterLinguaTheme { ForgeLevelsScreen(ForgeState.Unavailable, onOpenLevel = {}, onContinue = {}, onRetry = { retries++ }) }
        }
        composeTestRule.onNodeWithTag("curriculum_unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("curriculum_retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun levelScreen_showsEveryUnit_withItsMasteredCount() {
        val level = ForgeLevelState.Loaded(
            CurriculumLevelRange(CefrLevel.A1, 0, 40),
            listOf(
                ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 20), (1..20).map { "w$it" }, masteredCount = 5),
                ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 2, 20, 40), (21..40).map { "w$it" }, masteredCount = 20),
            ),
        )
        composeTestRule.setContent { AlterLinguaTheme { ForgeLevelScreen(level, onOpenUnit = {}, onBack = {}, onRetry = {}) } }
        composeTestRule.onNodeWithTag("forge_unit_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forge_unit_2").assertIsDisplayed()
    }

    @Test
    fun levelScreen_tappingAUnit_callsOnOpenUnit() {
        var opened: ForgeUnitSummary? = null
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 2), listOf("un", "deux"), masteredCount = 0)
        val level = ForgeLevelState.Loaded(CurriculumLevelRange(CefrLevel.A1, 0, 2), listOf(unit))
        composeTestRule.setContent { AlterLinguaTheme { ForgeLevelScreen(level, onOpenUnit = { opened = it }, onBack = {}, onRetry = {}) } }
        composeTestRule.onNodeWithTag("forge_unit_1").performClick()
        assertEquals(unit, opened)
    }

    @Test
    fun levelScreen_backButton_callsOnBack() {
        var back = 0
        val level = ForgeLevelState.Loaded(CurriculumLevelRange(CefrLevel.A1, 0, 0), emptyList())
        composeTestRule.setContent { AlterLinguaTheme { ForgeLevelScreen(level, onOpenUnit = {}, onBack = { back++ }, onRetry = {}) } }
        composeTestRule.onNodeWithTag("forge_level_back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun lessonMenu_vocabularyOpensThePracticeFlow_othersAreShownAsComingSoon() {
        var opened = 0
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 3, 0, 2), listOf("un", "deux"), masteredCount = 0)
        composeTestRule.setContent {
            AlterLinguaTheme { LessonMenuScreen(unit, onOpenVocabulary = { opened++ }, onOpenReading = {}, onBack = {}) }
        }
        composeTestRule.onNodeWithTag("lesson_vocabulary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_vocabulary").performClick()
        assertEquals(1, opened)
        composeTestRule.onNodeWithTag("lesson_grammar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_listening").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_reading").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_writing").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_speaking").assertIsDisplayed()
    }

    @Test
    fun lessonMenu_readingOpensTheReadingFlow() {
        var opened = 0
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 3, 0, 2), listOf("un", "deux"), masteredCount = 0)
        composeTestRule.setContent {
            AlterLinguaTheme { LessonMenuScreen(unit, onOpenVocabulary = {}, onOpenReading = { opened++ }, onBack = {}) }
        }
        composeTestRule.onNodeWithTag("lesson_reading").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun lessonMenu_backButton_callsOnBack() {
        var back = 0
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 1), listOf("un"), masteredCount = 0)
        composeTestRule.setContent { AlterLinguaTheme { LessonMenuScreen(unit, onOpenVocabulary = {}, onOpenReading = {}, onBack = { back++ }) } }
        composeTestRule.onNodeWithTag("lesson_menu_back").performClick()
        assertEquals(1, back)
    }
}
