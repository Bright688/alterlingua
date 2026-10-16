package com.alterlingua.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.alterlingua.app.localization.LocalizedActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/**
 * Receives an image shared from another app (for example a photo of a menu or a sign, via Share). Android grants
 * this screen temporary read access to that one item; the image is copied at once into a private temporary file,
 * its text found entirely on the phone (ML Kit), and that text translated. The image itself is never uploaded
 * anywhere and is deleted as soon as its text has been looked for.
 */
class ShareImageActivity : LocalizedActivity() {

    private val viewModel: SharedImageViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation recreates the activity but keeps the view model, which ignores a second start for the same screen.
        viewModel.start(sharedAddress(intent))
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SharedImageScreen(
                    state = state,
                    onListen = viewModel::listen,
                    onRetry = viewModel::retry,
                    onUpgrade = {
                        startActivity(AppLinks.settingsIntent(this))
                        finish()
                    },
                    onClose = {
                        viewModel.cancel()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.start(sharedAddress(intent), force = true)
    }

    /** The address of the one shared item, or null. Only ACTION_SEND is handled. */
    private fun sharedAddress(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        return uri?.toString()
    }
}
