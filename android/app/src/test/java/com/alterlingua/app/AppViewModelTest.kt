package com.alterlingua.app

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun newUser_startsAtWelcome() {
        val vm = AppViewModel(FakeUserSettingsRepository())
        assertEquals(AppStartState.Welcome, vm.startState.value)
    }

    @Test
    fun gettingStarted_movesOnToChoosingTheAppLanguage_andChangesNothingElse() {
        val repo = FakeUserSettingsRepository()
        val vm = AppViewModel(repo)
        val before = repo.current
        vm.welcomeSeen()
        assertEquals(AppStartState.ChooseAppLanguage, vm.startState.value)
        assertEquals(before.copy(welcomeSeen = true), repo.current)
    }

    @Test
    fun returningUser_startsInTheMainApp() {
        val vm = AppViewModel(FakeUserSettingsRepository(UserSettings(onboardingCompleted = true)))
        assertEquals(AppStartState.Main, vm.startState.value)
    }

    @Test
    fun finishingOnboarding_opensTheMainApp() = runBlocking {
        val repo = FakeUserSettingsRepository(UserSettings(welcomeSeen = true, appLanguageChosen = true))
        val vm = AppViewModel(repo)
        assertEquals(AppStartState.Onboarding, vm.startState.value)

        repo.update { it.copy(onboardingCompleted = true) }

        assertEquals(AppStartState.Main, vm.startState.value)
    }

    @Test
    fun choosingTheAppLanguage_movesOnToOnboarding_andChangesNothingElse() {
        val repo = FakeUserSettingsRepository(UserSettings(welcomeSeen = true))
        val vm = AppViewModel(repo)
        val before = repo.current
        vm.chooseAppLanguage(com.alterlingua.app.learning.Languages.German)
        assertEquals(AppStartState.Onboarding, vm.startState.value)
        assertEquals(com.alterlingua.app.learning.Languages.German, repo.current.appLanguage)
        assertEquals(before.copy(appLanguage = com.alterlingua.app.learning.Languages.German, appLanguageChosen = true), repo.current)
    }

    @Test
    fun aReturningUserIsNeverAskedForTheAppLanguageAgain() {
        val vm = AppViewModel(FakeUserSettingsRepository(UserSettings(onboardingCompleted = true, appLanguageChosen = false)))
        assertEquals(AppStartState.Main, vm.startState.value)
    }
}
