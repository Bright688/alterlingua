package com.alterlingua.app.ui.settings

import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EraseLearningDataTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun nothingIsErasedUntilTheUserConfirms() {
        var calls = 0
        val vm = SettingsViewModel(FakeUserSettingsRepository()) { calls++; true }
        assertNull(vm.uiState.value.dataErased)
        assertEquals(0, calls)
    }

    @Test fun confirming_erasesAndReportsSuccess() {
        var calls = 0
        val vm = SettingsViewModel(FakeUserSettingsRepository()) { calls++; true }
        vm.onEraseLearningData()
        assertEquals(1, calls)
        assertEquals(true, vm.uiState.value.dataErased)
    }

    @Test fun aPartialFailureIsReportedHonestly() {
        val vm = SettingsViewModel(FakeUserSettingsRepository()) { false }
        vm.onEraseLearningData()
        assertEquals(false, vm.uiState.value.dataErased)
    }

    @Test fun theSettingsAreKeptWhenLearningDataIsErased() {
        val repo = FakeUserSettingsRepository()
        val before = repo.current
        val vm = SettingsViewModel(repo) { true }
        vm.onEraseLearningData()
        assertEquals(before, repo.current)
    }
}

class LanguageSettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun choosingTheAppLanguage_changesOnlyTheAppLanguage() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        val before = repo.current
        vm.onAppLanguageSelected(com.alterlingua.app.learning.Languages.Japanese)
        assertEquals(com.alterlingua.app.learning.Languages.Japanese, repo.current.appLanguage)
        assertEquals(true, repo.current.appLanguageChosen)
        assertEquals(before.nativeLanguage, repo.current.nativeLanguage)
        assertEquals(before.targetLanguage, repo.current.targetLanguage)
        assertEquals(before.level, repo.current.level)
        assertEquals(com.alterlingua.app.learning.Languages.Japanese, vm.uiState.value.appLanguage)
    }

    @Test fun theSourceAndTargetLanguagesAreSetSeparately_fromTheAppLanguage() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onAppLanguageSelected(com.alterlingua.app.learning.Languages.Spanish)
        vm.onNativeLanguageSelected(com.alterlingua.app.learning.Languages.Italian)
        vm.onTargetLanguageSelected(com.alterlingua.app.learning.Languages.Dutch)
        assertEquals(com.alterlingua.app.learning.Languages.Spanish, repo.current.appLanguage)
        assertEquals(com.alterlingua.app.learning.Languages.Italian, repo.current.nativeLanguage)
        assertEquals(com.alterlingua.app.learning.Languages.Dutch, repo.current.targetLanguage)
    }

    @Test fun autoDetectionCanBeTurnedOffAndOn() {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertEquals(true, vm.uiState.value.detectSourceAutomatically)
        vm.onDetectSourceChanged(false)
        assertEquals(false, repo.current.detectSourceAutomatically)
        assertEquals(false, vm.uiState.value.detectSourceAutomatically)
        vm.onDetectSourceChanged(true)
        assertEquals(true, repo.current.detectSourceAutomatically)
    }
}
