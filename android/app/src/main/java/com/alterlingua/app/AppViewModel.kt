package com.alterlingua.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where the app should start: waiting for saved settings, choosing the app language, in onboarding, or in the main app. */
sealed interface AppStartState {
    data object Loading : AppStartState

    /** First launch, before anything else: the app's promise, with a single "Get started" action. No question is asked here. */
    data object Welcome : AppStartState

    /** The user chooses the language AlterLingua itself is shown in. */
    data object ChooseAppLanguage : AppStartState
    data object Onboarding : AppStartState
    data object Main : AppStartState
}

class AppViewModel(private val repository: UserSettingsRepository) : ViewModel() {
    val startState: StateFlow<AppStartState> = repository.settings
        .map {
            when {
                it.onboardingCompleted -> AppStartState.Main
                !it.welcomeSeen -> AppStartState.Welcome
                !it.appLanguageChosen -> AppStartState.ChooseAppLanguage
                else -> AppStartState.Onboarding
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.Loading)

    /** "Get started" on the welcome screen. Nothing else changes; the language questions come next. */
    fun welcomeSeen() {
        viewModelScope.launch { repository.update { it.copy(welcomeSeen = true) } }
    }

    /** The user confirmed the app language on first launch. Only the app language changes; the source and target are chosen next. */
    fun chooseAppLanguage(language: Language) {
        viewModelScope.launch { repository.update { it.copy(appLanguage = language, appLanguageChosen = true) } }
    }
}
