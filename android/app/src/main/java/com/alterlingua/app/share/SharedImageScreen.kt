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
 * The image translation screen for an image shared into AlterLingua (Standard/Premium): process, result and error
 * states, mirroring [SharedVoiceScreen]'s own shape.
 */
@Composable
fun SharedImageScreen(
    state: SharedImageState,
    onListen: () -> Unit,
    onRetry: () -> Unit,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("screen_shared_image"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.pi_image_translation), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            when (state) {
                SharedImageState.Idle, is SharedImageState.Working -> Working(state, onClose)
                is SharedImageState.Result -> ResultContent(state.value, onListen, onClose)
                is SharedImageState.Failed -> Problem(state, onRetry, onClose)
                SharedImageState.NotEntitled -> NotEntitled(onUpgrade, onClose)
                SharedImageState.Closed -> Unit
            }
            if (state !is SharedImageState.NotEntitled) {
                Text(
                    text = stringResource(R.string.pi_is_processed_on_your_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("shared_image_privacy"),
                )
            }
        }
    }
}

@Composable
private fun Working(state: SharedImageState, onClose: () -> Unit) {
    val step = (state as? SharedImageState.Working)?.step ?: SharedImageState.Step.READING
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_image_working"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(
                    stringResource(
                        when (step) {
                            SharedImageState.Step.READING -> R.string.pi_step_reading
                            SharedImageState.Step.RECOGNIZING -> R.string.pi_step_recognizing
                            SharedImageState.Step.TRANSLATING -> R.string.pi_step_translating
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StepLine(stringResource(R.string.pi_line_get), done = step != SharedImageState.Step.READING)
            StepLine(stringResource(R.string.pi_line_recognize), done = step == SharedImageState.Step.TRANSLATING)
            StepLine(stringResource(R.string.pi_line_translate), done = false)
        }
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_image_cancel")) { Text(stringResource(R.string.voice_cancel)) }
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
private fun ResultContent(result: ImageTranslationResult, onListen: () -> Unit, onClose: () -> Unit) {
    AlterLinguaCard {
        Text(stringResource(R.string.pi_extracted_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.extractedText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("shared_image_extracted_text"))
    }

    AlterLinguaCard {
        Text(stringResource(R.string.vn_translation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.translation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("shared_image_translation"))
        OutlinedButton(onClick = onListen, enabled = result.canListen, modifier = Modifier.fillMaxWidth().testTag("shared_image_listen")) {
            Text(
                when {
                    !result.canListen -> stringResource(R.string.vn_listen_no_voice, result.targetLanguage.nativeName)
                    result.playing -> stringResource(R.string.voice_stop)
                    else -> stringResource(R.string.vn_listen)
                },
            )
        }
    }

    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_image_done")) { Text(stringResource(R.string.vn_done)) }
}

@Composable
private fun Problem(state: SharedImageState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    val text = SharedImageMessages.forState(state)
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_image_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.title.string(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text.message.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.canRetry) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("shared_image_retry")) { Text(stringResource(R.string.vn_try_again)) }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_image_close")) { Text(stringResource(R.string.voice_close)) }
}

@Composable
private fun NotEntitled(onUpgrade: () -> Unit, onClose: () -> Unit) {
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_image_not_entitled"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pi_not_entitled_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.pi_not_entitled_msg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth().testTag("shared_image_upgrade")) { Text(stringResource(R.string.lrn_curriculum_upgrade)) }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_image_not_entitled_close")) { Text(stringResource(R.string.voice_close)) }
}
