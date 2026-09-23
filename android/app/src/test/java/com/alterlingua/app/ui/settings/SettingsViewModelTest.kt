package com.alterlingua.app.ui.settings

import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun showsTheSavedAnswers() {
        val repo = FakeUserSettingsRepository(
            UserSettings(
                nativeLanguage = Languages.French,
                targetLanguage = Languages.English,
                assistanceMode = AssistanceMode.ON_DEMAND,
                dailyReminderEnabled = false,
                reminderTime = LocalTime.of(9, 0),
            ),
        )
        val state = SettingsViewModel(repo).uiState.value
        assertEquals(Languages.French, state.nativeLanguage)
        assertEquals(Languages.English, state.targetLanguage)
        assertEquals(AssistanceMode.ON_DEMAND, state.assistanceMode)
        assertFalse(state.dailyReminderOn)
        assertEquals(LocalTime.of(9, 0), state.reminderTime)
    }

    @Test
    fun selectingAMode_isSaved_andShown() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onAssistanceModeSelected(AssistanceMode.ADAPTIVE)
        assertEquals(AssistanceMode.ADAPTIVE, repo.current.assistanceMode)
        assertEquals(AssistanceMode.ADAPTIVE, vm.uiState.value.assistanceMode)
    }

    @Test
    fun reminderCanBeTurnedOff_andIsSaved() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onDailyReminderChanged(false)
        assertFalse(repo.current.dailyReminderEnabled)
        assertFalse(vm.uiState.value.dailyReminderOn)
    }

    @Test
    fun autoTranslateIsOffByDefault_andCanBeTurnedOn_andIsSaved() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertFalse(vm.uiState.value.autoTranslateEnabled)
        vm.onAutoTranslateChanged(true)
        assertTrue(repo.current.autoTranslateEnabled)
        assertTrue(vm.uiState.value.autoTranslateEnabled)
    }

    @Test
    fun theLearningLanguageCanBeChanged_andIsSaved() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onTargetLanguageSelected(Languages.Spanish)
        assertEquals(Languages.Spanish, repo.current.targetLanguage)
        assertEquals(Languages.Spanish, vm.uiState.value.targetLanguage)
        assertEquals(Languages.English, vm.uiState.value.nativeLanguage)
    }

    @Test
    fun switchingBetweenLanguages_neverLeavesTheSameLanguageInBothFields() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onNativeLanguageSelected(Languages.French) // French is the current target, so the two swap
        assertEquals(Languages.French, repo.current.nativeLanguage)
        assertEquals(Languages.English, repo.current.targetLanguage)
    }

    @Test
    fun changingLanguage_keepsTheOtherAnswers() {
        val repo = FakeUserSettingsRepository(
            UserSettings(assistanceMode = AssistanceMode.ADAPTIVE, reminderTime = LocalTime.of(7, 0)),
        )
        SettingsViewModel(repo).onTargetLanguageSelected(Languages.Japanese)
        assertEquals(AssistanceMode.ADAPTIVE, repo.current.assistanceMode)
        assertEquals(LocalTime.of(7, 0), repo.current.reminderTime)
    }

    @Test
    fun keyboardStylesAreOfferedOnlyForTheLanguageTypedIn_whenItHasMoreThanOne() {
        fun choicesFor(language: com.alterlingua.app.learning.Language) =
            SettingsViewModel(FakeUserSettingsRepository(UserSettings(nativeLanguage = language, targetLanguage = if (language == Languages.English) Languages.French else Languages.English))).uiState.value.keyboardStyleChoices
        assertEquals(listOf(Languages.Japanese), choicesFor(Languages.Japanese).map { it.language })
        assertEquals(listOf(Languages.Chinese), choicesFor(Languages.Chinese).map { it.language })
        for (language in listOf(Languages.English, Languages.French, Languages.Spanish, Languages.German, Languages.Italian, Languages.Dutch)) {
            assertTrue(language.code, choicesFor(language).isEmpty())
        }
    }
}
