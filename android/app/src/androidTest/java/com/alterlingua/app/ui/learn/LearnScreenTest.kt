package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class LearnScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun card(term: String, type: UnitType, meaning: String?) = LessonCard(
        UnitKey("fr", term, type), term, if (type == UnitType.WORD) CardKind.WORD_CARD else CardKind.PHRASE_CARD,
        meaning, meaning?.let { "en" }, LessonContext(3, InteractionKind.INCOMING_MESSAGE, System.currentTimeMillis(), 0, listOf(com.alterlingua.app.learning.lessons.LessonReason.NEW)),
        MasteryStatus.LEARNING,
    )

    private fun lesson(position: Int = 0) = DailyLesson("2026-09-20", "fr", listOf(card("devis", UnitType.WORD, "quotation"), card("avant midi", UnitType.PHRASE, null)), position)

    @Test
    fun wordCard_showsTermMeaningContext_andDisabledListenAndRepeat() {
        var next = 0
        composeTestRule.setContent { AlterLinguaTheme { LearnScreen(LearnUiState.Card(lesson()), onPrevious = {}, onNext = { next++ }) } }
        composeTestRule.onNodeWithTag("lesson_word_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_term").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_meaning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_context").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_listen").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("lesson_repeat").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("lesson_back").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("lesson_next").performClick()
        assertEquals(1, next)
    }

    @Test
    fun phraseCard_withoutMeaning_saysSo() {
        composeTestRule.setContent { AlterLinguaTheme { LearnScreen(LearnUiState.Card(lesson(1)), onPrevious = {}, onNext = {}) } }
        composeTestRule.onNodeWithTag("lesson_phrase_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_meaning_missing").assertIsDisplayed()
    }

    @Test
    fun emptyAndCompleteStates_areShown() {
        composeTestRule.setContent { AlterLinguaTheme { LearnScreen(LearnUiState.Empty(Languages.French), onPrevious = {}, onNext = {}) } }
        composeTestRule.onNodeWithTag("lesson_empty").assertIsDisplayed()
    }

    @Test
    fun completeState_isShown() {
        composeTestRule.setContent { AlterLinguaTheme { LearnScreen(LearnUiState.Complete(lesson().copy(completed = true)), onPrevious = {}, onNext = {}) } }
        composeTestRule.onNodeWithTag("lesson_complete").assertIsDisplayed()
    }
}
