package com.alterlingua.app.navigation

import android.content.Context
import android.content.Intent
import com.alterlingua.app.MainActivity

/** Ways other parts of AlterLingua (for example the keyboard) open a specific tab of the app. */
object AppLinks {
    const val EXTRA_DESTINATION = "com.alterlingua.app.extra.DESTINATION"

    /** Opens AlterLingua on its Settings tab, reusing the running app if there is one. */
    fun settingsIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DESTINATION, TopLevelDestination.SETTINGS.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Opens AlterLingua on its Learn tab (from the voice-note screen's "Review useful language"). */
    fun learnIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DESTINATION, TopLevelDestination.LEARN.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The tab named in an intent, or null if there is none or it is not one of ours. */
    fun destinationFrom(intent: Intent?): TopLevelDestination? = destinationForRoute(intent?.getStringExtra(EXTRA_DESTINATION))

    fun destinationForRoute(route: String?): TopLevelDestination? = TopLevelDestination.entries.firstOrNull { it.route == route }
}
