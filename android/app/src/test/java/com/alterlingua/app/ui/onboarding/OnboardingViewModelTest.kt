package com.alterlingua.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repo = FakeUserSettingsRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(repo, saved)

    @Test
    fun startsOnTheSourceLanguage_afterLoadingSavedAnswers() {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(OnboardingStep.SOURCE, state.step)
        assertTrue(state.loaded)
        // Choosing the app language is step 1 (its own screen before these), so the first step here is 2 of 11.
        assertEquals(2, state.stepNumber)
        assertEquals(11, state.stepCount)
    }

    @Test
    fun earlierSavedAnswers_arePreloaded() {
        val earlier = UserSettings(purpose = LearningPurpose.FAMILY, level = LanguageLevel.SOME_BASICS)
        val vm = OnboardingViewModel(FakeUserSettingsRepository(earlier), SavedStateHandle())
        assertEquals(LearningPurpose.FAMILY, vm.uiState.value.settings.purpose)
        assertEquals(LanguageLevel.SOME_BASICS, vm.uiState.value.settings.level)
    }

    @Test
    fun next_walksEveryStep_andStopsOnTheLast() {
        val vm = viewModel()
        val seen = mutableListOf(vm.uiState.value.step)
        repeat(10) {
            vm.next()
            if (vm.uiState.value.step != seen.last()) seen += vm.uiState.value.step
        }
        assertEquals("English has one way of typing, so the typing-style screen is skipped", OnboardingStep.entries - OnboardingStep.KEYBOARD_STYLE, seen)
        assertTrue(vm.uiState.value.isLastStep)
    }

    @Test
    fun theSetupStepsComeAfterTheReminder_andBeforeTheSummary() {
        val order = OnboardingStep.entries
        assertEquals(
            listOf(
                OnboardingStep.REMINDER,
                OnboardingStep.KEYBOARD,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.MICROPHONE,
                OnboardingStep.COMPLETE,
            ),
            order.subList(order.indexOf(OnboardingStep.REMINDER), order.size),
        )
    }

    @Test
    fun savingAnswers_neverEraseTheMicrophoneFlag() {
        val vm = OnboardingViewModel(repo, SavedStateHandle())
        // The draft was loaded before the microphone question was shown...
        repo.current.let { assertFalse(it.microphonePermissionAsked) }
        // ...then setup records that the question was asked.
        kotlinx.coroutines.runBlocking { repo.update { it.copy(microphonePermissionAsked = true) } }

        vm.onPurposeSelected(LearningPurpose.TRAVEL)
        vm.finish()

        assertTrue(repo.current.microphonePermissionAsked)
        assertTrue(repo.current.onboardingCompleted)
        assertEquals(LearningPurpose.TRAVEL, repo.current.purpose)
    }

    @Test
    fun englishToFrench_thenEnglishToSpanish_areSavedAsChosen() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.English)
        vm.onTargetLanguageSelected(Languages.French)
        vm.onLevelSelected(LanguageLevel.INTERMEDIATE)
        vm.next()
        assertEquals("fr", repo.current.targetLanguage.code)
        assertEquals(LanguageLevel.INTERMEDIATE, repo.current.level)

        vm.onTargetLanguageSelected(Languages.Spanish)
        assertEquals(LanguageLevel.BEGINNER, vm.uiState.value.settings.level) // Español has its own answer
        vm.onLevelSelected(LanguageLevel.SOME_BASICS)
        vm.next()
        assertEquals("en", repo.current.nativeLanguage.code)
        assertEquals("es", repo.current.targetLanguage.code)
        assertEquals(LanguageLevel.SOME_BASICS, repo.current.level)
        assertEquals(LanguageLevel.INTERMEDIATE, repo.current.otherLevels["fr"])
    }

    @Test
    fun savingAnswers_neverEraseTheIncomingTranslationChoice() {
        val vm = OnboardingViewModel(repo, SavedStateHandle())
        kotlinx.coroutines.runBlocking { repo.update { it.copy(incomingTranslationEnabled = false) } } // switched off in Settings meanwhile
        vm.onPurposeSelected(LearningPurpose.TRAVEL)
        vm.next()
        vm.finish()
        assertFalse(repo.current.incomingTranslationEnabled)
    }

    @Test
    fun back_isRefusedOnFirstStep_andWorksAfterwards() {
        val vm = viewModel()
        assertFalse(vm.back())
        vm.next()
        assertTrue(vm.back())
        assertEquals(OnboardingStep.SOURCE, vm.uiState.value.step)
    }

    @Test
    fun everyAnswerCanBeChosen() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.French)
        vm.onPurposeSelected(LearningPurpose.STUDY)
        vm.onLevelSelected(LanguageLevel.INTERMEDIATE)
        vm.onAssistanceModeSelected(AssistanceMode.ON_DEMAND)
        vm.onReminderEnabledChanged(false)
        vm.onReminderTimeChanged(LocalTime.of(6, 15))

        val s = vm.uiState.value.settings
        assertEquals(Languages.French, s.nativeLanguage)
        assertEquals(Languages.English, s.targetLanguage) // swapped so the two never match
        assertEquals(LearningPurpose.STUDY, s.purpose)
        assertEquals(LanguageLevel.INTERMEDIATE, s.level)
        assertEquals(AssistanceMode.ON_DEMAND, s.assistanceMode)
        assertFalse(s.dailyReminderEnabled)
        assertEquals(LocalTime.of(6, 15), s.reminderTime)
    }

    @Test
    fun anyOfTheEightLanguages_canBeChosenForEitherField() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.Spanish)
        vm.onTargetLanguageSelected(Languages.German)
        assertEquals(Languages.Spanish, vm.uiState.value.settings.nativeLanguage)
        assertEquals(Languages.German, vm.uiState.value.settings.targetLanguage)

        vm.onTargetLanguageSelected(Languages.Japanese)
        assertEquals(Languages.Japanese, vm.uiState.value.settings.targetLanguage)
        assertEquals(Languages.Spanish, vm.uiState.value.settings.nativeLanguage)
    }

    @Test
    fun pickingTheOtherFieldsLanguage_swapsThemBack() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.Spanish)
        vm.onTargetLanguageSelected(Languages.German)

        vm.onTargetLanguageSelected(Languages.Spanish)

        assertEquals(Languages.Spanish, vm.uiState.value.settings.targetLanguage)
        assertEquals(Languages.German, vm.uiState.value.settings.nativeLanguage)
    }

    @Test
    fun continuing_savesAnswersButDoesNotCompleteOnboarding() {
        val vm = viewModel()
        vm.next() // Welcome -> Languages
        vm.onPurposeSelected(LearningPurpose.TRAVEL)
        vm.next() // Languages -> Assistance: saves the draft

        assertEquals(LearningPurpose.TRAVEL, repo.current.purpose)
        assertFalse(repo.current.onboardingCompleted)
    }

    @Test
    fun finish_savesEverything_andMarksOnboardingComplete() {
        val vm = viewModel()
        vm.onLevelSelected(LanguageLevel.SOME_BASICS)
        vm.onAssistanceModeSelected(AssistanceMode.ADAPTIVE)
        vm.onReminderTimeChanged(LocalTime.of(22, 0))

        vm.finish()

        val saved = repo.current
        assertTrue(saved.onboardingCompleted)
        assertEquals(LanguageLevel.SOME_BASICS, saved.level)
        assertEquals(AssistanceMode.ADAPTIVE, saved.assistanceMode)
        assertEquals(LocalTime.of(22, 0), saved.reminderTime)
    }

    @Test
    fun currentStep_isRestoredAfterTheAppIsRecreated() {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        repeat(4) { first.next() }

        val recreated = viewModel(saved)
        assertEquals(OnboardingStep.ASSISTANCE, recreated.uiState.value.step)
    }

    @Test
    fun theStepsFollowTheBriefsOrder() {
        assertEquals(
            listOf("SOURCE", "KEYBOARD_STYLE", "TARGET", "PURPOSE", "LEVEL", "ASSISTANCE", "REMINDER", "KEYBOARD", "NOTIFICATIONS", "MICROPHONE", "COMPLETE"),
            OnboardingStep.entries.map { it.name },
        )
    }

    @Test
    fun theSourceStepCanTurnAutomaticDetectionOffAndOn() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.settings.detectSourceAutomatically)
        vm.onDetectSourceChanged(false)
        assertFalse(vm.uiState.value.settings.detectSourceAutomatically)
        vm.next()
        assertFalse(repo.current.detectSourceAutomatically)
    }

    @Test
    fun sourceAndTargetAreChosenOnSeparateSteps_andStayIndependentOfTheAppLanguage() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.Spanish)
        vm.next()
        assertEquals(OnboardingStep.TARGET, vm.uiState.value.step)
        vm.onTargetLanguageSelected(Languages.German)
        vm.next()
        val s = repo.current
        assertEquals(Languages.Spanish, s.nativeLanguage)
        assertEquals(Languages.German, s.targetLanguage)
        assertEquals("the app language was chosen before onboarding and is not touched here", UserSettings().appLanguage, s.appLanguage)
    }

    @Test
    fun aStepSavedByAnEarlierVersion_isIgnoredNotMisread() {
        val vm = viewModel(SavedStateHandle(mapOf("onboarding_step" to 5)))
        assertEquals(OnboardingStep.SOURCE, vm.uiState.value.step)
    }

    @Test
    fun chineseOrJapanese_addsATypingStyleStep_rightAfterTheLanguage() {
        for (language in listOf(Languages.Chinese, Languages.Japanese)) {
            val vm = viewModel()
            vm.onNativeLanguageSelected(language)
            assertEquals(12, vm.uiState.value.stepCount)
            vm.next()
            assertEquals(OnboardingStep.KEYBOARD_STYLE, vm.uiState.value.step)
            assertEquals(3, vm.uiState.value.stepNumber)
            vm.next()
            assertEquals(OnboardingStep.TARGET, vm.uiState.value.step)
            assertTrue(vm.back())
            assertEquals(OnboardingStep.KEYBOARD_STYLE, vm.uiState.value.step)
        }
    }

    @Test
    fun aLanguageWithOneWayOfTyping_skipsTheStyleStepBothWays() {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.German)
        assertEquals(11, vm.uiState.value.stepCount)
        vm.next()
        assertEquals(OnboardingStep.TARGET, vm.uiState.value.step)
        assertTrue(vm.back())
        assertEquals(OnboardingStep.SOURCE, vm.uiState.value.step)
    }

    @Test
    fun theChosenStyleIsKeptForThatLanguage_andSavedWithTheAnswers() = kotlinx.coroutines.runBlocking {
        val vm = viewModel()
        vm.onNativeLanguageSelected(Languages.Japanese)
        vm.onKeyboardStyleSelected(KeyboardStyle.ROMAJI)
        assertEquals(KeyboardStyle.ROMAJI, vm.uiState.value.settings.keyboardStyleFor("ja"))
        vm.next()
        assertEquals(KeyboardStyle.ROMAJI, repo.settings.first().keyboardStyleFor("ja"))
    }
}
