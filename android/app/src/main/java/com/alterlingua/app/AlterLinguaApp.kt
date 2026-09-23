package com.alterlingua.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hierarchy
import com.alterlingua.app.navigation.AlterLinguaNavHost
import com.alterlingua.app.navigation.TopLevelDestination
import com.alterlingua.app.navigation.navigateToTopLevel
import com.alterlingua.app.ui.theme.extendedColors

/** Root of the app UI: bottom navigation bar plus the tab content. */
@Composable
fun AlterLinguaApp(
    requestedDestination: TopLevelDestination? = null,
    onDestinationShown: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Another part of the app (the keyboard's Settings button) asked to open a tab.
    LaunchedEffect(requestedDestination) {
        if (requestedDestination != null) {
            navController.navigateToTopLevel(requestedDestination)
            onDestinationShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.extendedColors.card) {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.extendedColors.navIndicator,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        AlterLinguaNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
