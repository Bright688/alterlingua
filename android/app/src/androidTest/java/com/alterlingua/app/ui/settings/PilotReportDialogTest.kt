package com.alterlingua.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device or emulator: the consent step always appears first and can be cancelled without building
 * anything (`docs/pilot.md` B4). The report screen's own "Generate" button, which calls the real app's
 * `PilotReportViewModel`, is exercised only by a person on a phone, not here.
 */
class PilotReportDialogTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingTheRow_showsConsentFirst_cancellingBuildsNothing() {
        composeTestRule.setContent { AlterLinguaTheme { SettingsScreen(SettingsUiState(), {}, {}, {}, {}) } }
        composeTestRule.onNodeWithTag("settings_pilot_report").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pilot_consent_agree").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pilot_consent_cancel").performClick()
        composeTestRule.onAllNodesWithTag("pilot_participant_code").assertCountEquals(0)
    }

    @Test fun agreeing_opensTheReportScreen_withTheParticipantCodeField() {
        composeTestRule.setContent { AlterLinguaTheme { SettingsScreen(SettingsUiState(), {}, {}, {}, {}) } }
        composeTestRule.onNodeWithTag("settings_pilot_report").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pilot_consent_agree").performClick()
        composeTestRule.onNodeWithTag("pilot_participant_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pilot_report_generate").assertIsDisplayed()
    }
}
