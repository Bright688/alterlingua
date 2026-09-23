package com.alterlingua.app

import android.os.Bundle
import com.alterlingua.app.localization.LocalizedActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.navigation.TopLevelDestination
import com.alterlingua.app.ui.theme.AlterLinguaTheme

class MainActivity : LocalizedActivity() {

    /** A tab another part of AlterLingua (the keyboard's Settings button) asked to open. Cleared once shown. */
    private var requestedDestination by mutableStateOf<TopLevelDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedDestination = AppLinks.destinationFrom(intent)
        setContent {
            AlterLinguaTheme {
                AlterLinguaRoot(
                    requestedDestination = requestedDestination,
                    onDestinationShown = { requestedDestination = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination = AppLinks.destinationFrom(intent)
    }
}
