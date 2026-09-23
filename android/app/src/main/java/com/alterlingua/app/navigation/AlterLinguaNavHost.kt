package com.alterlingua.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.alterlingua.app.ui.home.HomeRoute
import com.alterlingua.app.ui.learn.LearnRoute
import com.alterlingua.app.ui.progress.ProgressRoute
import com.alterlingua.app.ui.settings.SettingsRoute
import com.alterlingua.app.ui.words.WordsRoute

/** Switches to a tab, saving the state of the one being left and restoring the target's. */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AlterLinguaNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeRoute(onOpenLearn = { navController.navigateToTopLevel(TopLevelDestination.LEARN) })
        }
        composable(TopLevelDestination.LEARN.route) { LearnRoute() }
        composable(TopLevelDestination.WORDS.route) { WordsRoute() }
        composable(TopLevelDestination.PROGRESS.route) { ProgressRoute() }
        composable(TopLevelDestination.SETTINGS.route) { SettingsRoute() }
    }
}
