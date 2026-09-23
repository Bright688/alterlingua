package com.alterlingua.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.alterlingua.app.R
import com.alterlingua.app.ui.components.NavIcons

/**
 * The five main areas of the app (CLAUDE.md section 11), in bottom-bar order.
 * Each one is a top-level destination: switching tabs keeps each tab's state.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    LEARN("learn", R.string.nav_learn, NavIcons.Learn),
    WORDS("words", R.string.nav_words, NavIcons.Words),
    PROGRESS("progress", R.string.nav_progress, NavIcons.Progress),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
