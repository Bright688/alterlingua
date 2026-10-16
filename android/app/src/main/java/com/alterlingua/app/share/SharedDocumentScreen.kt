package com.alterlingua.app.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string

/**
 * The document translation screen for a PDF or Word (.docx) file shared into AlterLingua (Premium): process, result
 * and error states, mirroring [SharedImageScreen]'s own shape.
 */
@Composable
fun SharedDocumentScreen(
    state: SharedDocumentState,
    onSaveOrShare: () -> Unit,
    onRetry: () -> Unit,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("screen_shared_document"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.pd_document_translation), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            when (state) {
                SharedDocumentState.Idle, is SharedDocumentState.Working -> Working(state, onClose)
                is SharedDocumentState.Result -> ResultContent(state.value, onSaveOrShare, onClose)
                is SharedDocumentState.Failed -> Problem(state, onRetry, onClose)
                SharedDocumentState.NotEntitled -> NotEntitled(onUpgrade, onClose)
                SharedDocumentState.Closed -> Unit
            }
            if (state !is SharedDocumentState.NotEntitled) {
                Text(
                    text = stringResource(R.string.pd_about_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("shared_document_privacy"),
                )
            }
        }
    }
}

@Composable
private fun Working(state: SharedDocumentState, onClose: () -> Unit) {
    val step = (state as? SharedDocumentState.Working)?.step ?: SharedDocumentState.Step.READING
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_document_working"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(
                    stringResource(
                        when (step) {
                            SharedDocumentState.Step.READING -> R.string.pd_step_reading
                            SharedDocumentState.Step.UPLOADING -> R.string.pd_step_uploading
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StepLine(stringResource(R.string.pd_line_get), done = step != SharedDocumentState.Step.READING)
            StepLine(stringResource(R.string.pd_line_translate), done = false)
        }
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_document_cancel")) { Text(stringResource(R.string.voice_cancel)) }
}

@Composable
private fun StepLine(text: String, done: Boolean) {
    Text(
        text = (if (done) "✓  " else "•  ") + text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultContent(result: TranslatedDocumentResult, onSaveOrShare: () -> Unit, onClose: () -> Unit) {
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_document_result"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pd_translated_ready), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(
                    if (result.contentType == DocumentSniffer.PDF) R.string.pd_translated_pdf_label else R.string.pd_translated_docx_label,
                    result.targetLanguage.nativeName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(onClick = onSaveOrShare, modifier = Modifier.fillMaxWidth().testTag("shared_document_save")) { Text(stringResource(R.string.pd_save_or_share)) }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_document_done")) { Text(stringResource(R.string.vn_done)) }
}

@Composable
private fun Problem(state: SharedDocumentState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    val text = SharedDocumentMessages.forState(state)
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_document_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.title.string(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text.message.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.canRetry) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("shared_document_retry")) { Text(stringResource(R.string.vn_try_again)) }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_document_close")) { Text(stringResource(R.string.voice_close)) }
}

@Composable
private fun NotEntitled(onUpgrade: () -> Unit, onClose: () -> Unit) {
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_document_not_entitled"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pd_not_entitled_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.pd_not_entitled_msg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth().testTag("shared_document_upgrade")) { Text(stringResource(R.string.lrn_curriculum_upgrade)) }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_document_not_entitled_close")) { Text(stringResource(R.string.voice_close)) }
}
