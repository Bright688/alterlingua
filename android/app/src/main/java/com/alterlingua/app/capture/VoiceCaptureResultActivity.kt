package com.alterlingua.app.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.localization.LocalizedActivity
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.share.SharedVoiceScreen
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/**
 * Opened from the "Voice note captured" notification, or from the keyboard's panel. It shows the same Voice Translation screen
 * as a voice note shared into AlterLingua. The note behind it is the one [CapturedNotes] already holds: if the note was
 * translated automatically the result is there at once, otherwise it starts now. The audio is deleted as soon as the backend
 * has answered, and closing the screen forgets the note.
 */
class VoiceCaptureResultActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showNote(intent.getStringExtra(EXTRA_ADDRESS))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNote(intent.getStringExtra(EXTRA_ADDRESS))
    }

    private fun showNote(address: String?) {
        val notes = (application as AlterLinguaApplication).capturedNotes
        if (address == null) {
            finish()
            return
        }
        // Idempotent: a rotation, or a tap on the notification of a note already translated, finds the same note.
        val viewModel = notes.open(address, announce = false).viewModel
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SharedVoiceScreen(
                    state = state,
                    onListen = viewModel::listen,
                    onReview = {
                        startActivity(AppLinks.learnIntent(this))
                        finish()
                    },
                    onRetry = viewModel::retry,
                    onClose = {
                        notes.close(address)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
