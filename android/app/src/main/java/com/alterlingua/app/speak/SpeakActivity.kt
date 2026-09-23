package com.alterlingua.app.speak

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import com.alterlingua.app.localization.LocalizedActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/**
 * Translated outgoing voice. The finished audio is handed to Android's Share sheet as a temporary content address that
 * grants the chosen app read access to this one file (a FileProvider limited to one private folder). AlterLingua never
 * sends anything: the user picks the chat in the share sheet and presses Send in that app.
 */
class SpeakActivity : LocalizedActivity() {

    private val viewModel: SpeakViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::permissionAnswered)
                SpeakScreen(
                    state = state,
                    onChooseTarget = viewModel::chooseTarget,
                    onRecord = viewModel::start,
                    onStop = viewModel::stop,
                    onAllowMicrophone = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    onRetry = viewModel::retry,
                    onListen = viewModel::listen,
                    onShare = ::share,
                    onRecordAgain = viewModel::recordAgain,
                    onClose = {
                        viewModel.release()
                        finish()
                    },
                )
            }
        }
    }

    private fun share() {
        val result = viewModel.prepareShare() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", result.audioFile)
        val send = Intent(Intent.ACTION_SEND)
            .setType(result.audioType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri("", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(com.alterlingua.app.R.string.speak_share_title)))
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen (other than to the share sheet, which keeps this screen alive) stops playback.
        if (isFinishing) viewModel.release()
    }
}
