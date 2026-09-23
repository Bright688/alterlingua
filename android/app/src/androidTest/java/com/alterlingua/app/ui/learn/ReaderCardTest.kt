package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.assistance.HelpAnswer
import com.alterlingua.app.learning.assistance.ReaderSegment
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class ReaderCardTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val segments = listOf(ReaderSegment("Le "), ReaderSegment("fournisseur", "fournisseur"), ReaderSegment(" exige un "), ReaderSegment("acompte", "acompte"), ReaderSegment("."))

    @Test
    fun beforeReading_showsTheInput_andShowText() {
        var shown = 0
        composeTestRule.setContent {
            AlterLinguaTheme { ReaderCard(ReaderUiState(Languages.French, "texte"), {}, { shown++ }, {}, {}, {}) }
        }
        composeTestRule.onNodeWithTag("reader_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_show").performClick()
        assertEquals(1, shown)
    }

    @Test
    fun aHelpAnswer_showsMeaning_addedToLearning_andADisabledListen() {
        composeTestRule.setContent {
            AlterLinguaTheme {
                ReaderCard(
                    ReaderUiState(Languages.French, segments = segments, help = WordHelpState.Shown(HelpAnswer("acompte", "deposit / advance payment", MasteryStatus.UNKNOWN))),
                    {}, {}, {}, {}, {},
                )
            }
        }
        composeTestRule.onNodeWithTag("reader_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_meaning").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_added").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_listen").assertIsNotEnabled()
    }
}
