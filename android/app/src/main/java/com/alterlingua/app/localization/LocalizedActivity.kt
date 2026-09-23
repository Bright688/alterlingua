package com.alterlingua.app.localization

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alterlingua.app.AlterLinguaApplication
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The base of every AlterLingua screen: it shows the screen in the user's app language and, if that language is changed while the
 * screen is open, recreates the screen in the new one. Nothing else is touched by a language change.
 *
 * The language is read once, before the screen is built (a small local read), because a screen's resources must be chosen before
 * it draws its first frame.
 */
abstract class LocalizedActivity : ComponentActivity() {
    private var appliedKey: String = ""

    override fun attachBaseContext(newBase: Context) {
        val settings = runCatching {
            runBlocking { (newBase.applicationContext as AlterLinguaApplication).userSettings.settings.first() }
        }.getOrNull()
        appliedKey = settings?.let(AppLanguage::keyOf).orEmpty()
        super.attachBaseContext(if (settings != null) AppLanguage.forSettings(newBase, settings) else newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as? AlterLinguaApplication ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.userSettings.settings.map(AppLanguage::keyOf).distinctUntilChanged().collect { key ->
                    if (key != appliedKey) recreate()
                }
            }
        }
    }
}
