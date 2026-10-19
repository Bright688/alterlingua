package com.alterlingua.app.speak

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.localization.LocalizedActivity
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/** Past translated voice messages. Opened from [SpeakActivity]'s History button. */
class SpokenHistoryActivity : LocalizedActivity() {

    private val viewModel: SpokenHistoryViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlterLinguaTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SpokenHistoryScreen(
                    state = state,
                    onDelete = viewModel::delete,
                    onClearAll = viewModel::clearAll,
                    onBack = { finish() },
                )
            }
        }
    }
}
