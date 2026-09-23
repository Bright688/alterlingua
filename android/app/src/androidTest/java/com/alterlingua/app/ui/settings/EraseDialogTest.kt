package com.alterlingua.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class EraseDialogTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun deleting_asksFirst_andOnlyErasesOnConfirm() {
        var erased = 0
        composeTestRule.setContent {
            AlterLinguaTheme { SettingsScreen(SettingsUiState(), {}, {}, {}, {}, onEraseLearningData = { erased++ }) }
        }
        composeTestRule.onNodeWithTag("settings_erase").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_erase_cancel").assertIsDisplayed().performClick()
        assertEquals(0, erased)
        composeTestRule.onNodeWithTag("settings_erase").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_erase_confirm").performClick()
        assertEquals(1, erased)
    }
}
