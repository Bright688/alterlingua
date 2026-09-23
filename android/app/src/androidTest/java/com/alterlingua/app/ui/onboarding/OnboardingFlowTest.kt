package com.alterlingua.app.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.storage.UserSettingsRepository
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** In-memory settings, so this test never touches the phone's real saved settings. */
private class InMemorySettings : UserSettingsRepository {
    private val state = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = state
    val current: UserSettings get() = state.value
    override suspend fun update(transform: (UserSettings) -> UserSettings) = state.update(transform)
}

/** Runs on a device or emulator: taps through all eight onboarding steps and checks what gets saved. */
class OnboardingFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun completingOnboarding_savesTheChosenAnswers() {
        val repo = InMemorySettings()
        val viewModel = OnboardingViewModel(repo, SavedStateHandle())
        composeTestRule.setContent { AlterLinguaTheme { OnboardingRoute(viewModel) } }

        // Step 2 of 11: source language (step 1, the app language, is its own screen before onboarding)
        composeTestRule.onNodeWithText("Step 2 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("native_language").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detect_source").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 3: target language
        composeTestRule.onNodeWithText("Step 3 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_language").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 4: reason
        composeTestRule.onNodeWithText("Step 4 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("purpose_STUDY").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 5: level
        composeTestRule.onNodeWithText("Step 5 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("level_INTERMEDIATE").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 6: assistance mode
        composeTestRule.onNodeWithText("Step 6 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_ADAPTIVE").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 7: reminder (pick the Morning preset)
        composeTestRule.onNodeWithText("Step 7 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithText("Morning").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Steps 8 to 10: keyboard, incoming messages, microphone. Skipped here: they only open Android screens.
        composeTestRule.onNodeWithText("Step 8 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_keyboard_enable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithText("Step 9 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_notifications_open").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithText("Step 10 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // Step 11: all set
        composeTestRule.onNodeWithText("Step 11 of 11").assertIsDisplayed()
        composeTestRule.onNodeWithText("You're all set!").assertIsDisplayed()
        assertTrue(!repo.current.onboardingCompleted)
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        composeTestRule.waitForIdle()
        val saved = repo.current
        assertTrue(saved.onboardingCompleted)
        assertEquals(LearningPurpose.STUDY, saved.purpose)
        assertEquals(LanguageLevel.INTERMEDIATE, saved.level)
        assertEquals(AssistanceMode.ADAPTIVE, saved.assistanceMode)
        assertEquals(java.time.LocalTime.of(8, 30), saved.reminderTime)
    }

    @Test
    fun anyLanguagePair_canBeChosenFromTheMenus_andShowsOnTheSummary() {
        val repo = InMemorySettings()
        val viewModel = OnboardingViewModel(repo, SavedStateHandle())
        composeTestRule.setContent { AlterLinguaTheme { OnboardingRoute(viewModel) } }

        composeTestRule.onNodeWithTag("native_language").performClick()
        composeTestRule.onNodeWithTag("native_language_option_es").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithTag("target_language").performClick()
        composeTestRule.onNodeWithTag("target_language_option_ja").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        // The questions name the chosen target language.
        composeTestRule.onNodeWithText("Why do you need 日本語?").assertExists()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithText("How much 日本語 do you already know?").assertExists()

        // Level, Assistance, Reminder, Keyboard, Incoming messages, Microphone -> All set.
        repeat(6) { composeTestRule.onNodeWithTag("onboarding_continue").performClick() }
        composeTestRule.onNodeWithText("Step 11 of 11").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("AlterLingua is ready to help you write in Español and be understood in 日本語.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()

        composeTestRule.waitForIdle()
        assertEquals(com.alterlingua.app.learning.Languages.Spanish, repo.current.nativeLanguage)
        assertEquals(com.alterlingua.app.learning.Languages.Japanese, repo.current.targetLanguage)
    }

    @Test
    fun englishToSpanish_showsSpanishInTheQuestions_andInAdaptiveMode() {
        val viewModel = OnboardingViewModel(InMemorySettings(), SavedStateHandle())
        composeTestRule.setContent { AlterLinguaTheme { OnboardingRoute(viewModel) } }

        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithTag("target_language").performClick()
        composeTestRule.onNodeWithTag("target_language_option_es").performClick()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithText("Why do you need Español?").assertExists()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule.onNodeWithText("How much Español do you already know?").assertExists()
        composeTestRule.onNodeWithTag("onboarding_continue").performClick()
        composeTestRule
            .onNodeWithText("Help me less as I learn. Assistance reduces step by step, based on your Español Personal Language Map.")
            .assertExists()
    }
}
