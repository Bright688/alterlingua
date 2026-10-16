package com.alterlingua.app.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.alterlingua.app.localization.LocalizedActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/**
 * Receives a PDF or Word (.docx) file shared from another app (Premium). Android grants this screen temporary read
 * access to that one item; it is uploaded at once for translation and deleted the moment the upload attempt
 * finishes. The backend returns a real translated file of the same format, which the user can then save or share
 * on through Android's own Share sheet -- AlterLingua never sends anything itself.
 */
class ShareDocumentActivity : LocalizedActivity() {

    private val viewModel: SharedDocumentViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation recreates the activity but keeps the view model, which ignores a second start for the same screen.
        viewModel.start(sharedAddress(intent))
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SharedDocumentScreen(
                    state = state,
                    onSaveOrShare = ::saveOrShare,
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

    private fun saveOrShare() {
        val result = (viewModel.uiState.value as? SharedDocumentState.Result)?.value ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", result.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(result.contentType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri("", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewModel.keepProducedFile() // the receiving app (or the Save dialog) now reads this file; closing must not delete it
        startActivity(Intent.createChooser(send, getString(com.alterlingua.app.R.string.pd_share_title)))
    }

    /** The address of the one shared item, or null. Only ACTION_SEND is handled. */
    private fun sharedAddress(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        return uri?.toString()
    }
}
