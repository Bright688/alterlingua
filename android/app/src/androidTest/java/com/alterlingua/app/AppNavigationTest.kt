package com.alterlingua.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator: opens each of the five tabs from the bottom bar. */
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensEachTab_fromTheBottomBar() {
        composeTestRule.setContent { AlterLinguaTheme { AlterLinguaApp() } }

        composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()

        listOf(
            "Learn" to "screen_learn",
            "Words" to "screen_words",
            "Progress" to "screen_progress",
            "Settings" to "screen_settings",
            "Home" to "screen_home",
        ).forEach { (label, tag) ->
            // The label also appears as the screen title, and the bottom bar comes last.
            composeTestRule.onAllNodesWithText(label).onLast().performClick()
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun homeWithoutALesson_offersNoContinueButton_andLearnIsOneTapAway() {
        composeTestRule.setContent { AlterLinguaTheme { AlterLinguaApp() } }
        composeTestRule.onNodeWithTag("home_lesson_summary").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Continue today's lesson").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Learn").onLast().performClick()
        composeTestRule.onNodeWithTag("screen_learn").assertIsDisplayed()
    }
}
