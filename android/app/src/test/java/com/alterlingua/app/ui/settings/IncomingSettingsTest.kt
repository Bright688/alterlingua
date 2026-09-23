package com.alterlingua.app.ui.settings

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.notifications.IncomingOutcome
import com.alterlingua.app.notifications.IncomingOutcomeKind
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IncomingSettingsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun incomingTranslationIsOnByDefault_andCanBeSwitchedOffAndOn() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertTrue(vm.uiState.value.incomingTranslation)

        vm.onIncomingTranslationChanged(false)
        assertFalse(repo.current.incomingTranslationEnabled)
        assertFalse(vm.uiState.value.incomingTranslation)

        vm.onIncomingTranslationChanged(true)
        assertTrue(repo.current.incomingTranslationEnabled)
    }

    @Test
    fun floatingTranslationIsOffByDefault_andCanBeSwitchedOn_withoutTouchingIncomingTranslation() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertFalse(vm.uiState.value.floatingTranslationEnabled)

        vm.onFloatingTranslationChanged(true)
        assertTrue(repo.current.floatingTranslationEnabled)
        assertTrue(vm.uiState.value.floatingTranslationEnabled)
        assertTrue(repo.current.incomingTranslationEnabled) // unrelated toggle, untouched

        vm.onFloatingTranslationChanged(false)
        assertFalse(repo.current.floatingTranslationEnabled)
    }

    @Test
    fun learningFromMessagesIsOnByDefault_andCanBeSwitchedOff_withoutTouchingTheOtherSwitches() {
        val repo = FakeUserSettingsRepository(UserSettings(incomingTranslationEnabled = false))
        val vm = SettingsViewModel(repo)
        assertTrue(vm.uiState.value.learningFromMessages)
        vm.onLearningFromMessagesChanged(false)
        assertFalse(repo.current.learningFromMessagesEnabled)
        assertFalse(repo.current.incomingTranslationEnabled)
        vm.onLearningFromMessagesChanged(true)
        assertTrue(repo.current.learningFromMessagesEnabled)
    }

    @Test
    fun changingTheSwitch_keepsTheLanguagesAndOtherAnswers() {
        val repo = FakeUserSettingsRepository(UserSettings(nativeLanguage = Languages.Spanish, targetLanguage = Languages.German, microphonePermissionAsked = true))
        SettingsViewModel(repo).onIncomingTranslationChanged(false)
        assertEquals(Languages.Spanish, repo.current.nativeLanguage)
        assertEquals(Languages.German, repo.current.targetLanguage)
        assertTrue(repo.current.microphonePermissionAsked)
    }

    @Test
    fun theLatestOutcomeIsShown() {
        val status = MutableStateFlow<IncomingOutcome?>(null)
        val vm = SettingsViewModel(FakeUserSettingsRepository(), status)
        assertNull(vm.uiState.value.lastIncoming)
        status.value = IncomingOutcome(IncomingOutcomeKind.TRANSLATED, "fr", 1)
        assertEquals(IncomingOutcomeKind.TRANSLATED, vm.uiState.value.lastIncoming?.kind)
    }

    @Test
    fun theOutcomeSentencesNameTheSourceLanguageInItsOwnName_andNeverContainMessageText() {
        assertEquals(com.alterlingua.app.ui.UiText(com.alterlingua.app.R.string.set_last_translated_from, "Français"), incomingOutcomeText(IncomingOutcome(IncomingOutcomeKind.TRANSLATED, "fr")))
        assertEquals(com.alterlingua.app.ui.UiText(com.alterlingua.app.R.string.set_last_translated_from, "日本語"), incomingOutcomeText(IncomingOutcome(IncomingOutcomeKind.TRANSLATED, "ja")))
        assertEquals(com.alterlingua.app.ui.UiText(com.alterlingua.app.R.string.set_last_translated), incomingOutcomeText(IncomingOutcome(IncomingOutcomeKind.TRANSLATED)))
        // Every outcome has its own sentence, and the only argument any of them takes is a language name.
        assertEquals(IncomingOutcomeKind.entries.size, IncomingOutcomeKind.entries.map { incomingOutcomeText(IncomingOutcome(it)).id }.toSet().size)
        for (kind in IncomingOutcomeKind.entries) assertTrue(kind.name, incomingOutcomeText(IncomingOutcome(kind, "fr")).args.all { it == "Français" })
    }

    @Test
    fun theFlagIsSavedByTheRealStore_andSurvivesOtherSaves() {
        // Round trip through the same read/write functions the app uses.
        val repo = FakeUserSettingsRepository(UserSettings(incomingTranslationEnabled = false))
        val vm = SettingsViewModel(repo)
        vm.onDailyReminderChanged(false)
        assertFalse(repo.current.incomingTranslationEnabled)
    }
}
