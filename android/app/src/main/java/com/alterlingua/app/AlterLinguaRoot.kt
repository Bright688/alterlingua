package com.alterlingua.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.navigation.TopLevelDestination
import com.alterlingua.app.ui.onboarding.AppLanguageScreen
import com.alterlingua.app.ui.onboarding.OnboardingRoute
import com.alterlingua.app.ui.onboarding.WelcomeScreen

/**
 * Chooses what to show at start: onboarding until the user finishes it, then the main app.
 * While the saved settings are being read, only the background colour shows.
 */
@Composable
fun AlterLinguaRoot(
    requestedDestination: TopLevelDestination? = null,
    onDestinationShown: () -> Unit = {},
    viewModel: AppViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    when (startState) {
        AppStartState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        AppStartState.Welcome -> WelcomeScreen(onGetStarted = viewModel::welcomeSeen)
        AppStartState.ChooseAppLanguage -> AppLanguageScreen(onConfirm = viewModel::chooseAppLanguage)
        AppStartState.Onboarding -> OnboardingRoute()
        AppStartState.Main -> AlterLinguaApp(requestedDestination, onDestinationShown)
    }
}
