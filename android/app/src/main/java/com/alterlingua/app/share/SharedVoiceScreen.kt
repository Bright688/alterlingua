package com.alterlingua.app.share

import androidx.compose.foundation.layout.Arrangement
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.components.MasteryChip
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.learning.engine.UnitType

/**
 * The Voice Translation screen for a voice note shared into AlterLingua (Stitch: "Incoming Voice Note — Share Flow":
 * process, result and error states).
 */
@Composable
fun SharedVoiceScreen(
    state: SharedVoiceState,
    onListen: () -> Unit,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("screen_shared_voice"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.vn_voice_note), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            when (state) {
                SharedVoiceState.Idle, is SharedVoiceState.Working -> Working(state, onClose)
                is SharedVoiceState.Result -> ResultContent(state.value, onListen, onReview, onClose)
                is SharedVoiceState.Failed -> Problem(state, onRetry, onClose)
                SharedVoiceState.Closed -> Unit
            }
            Text(
                text = stringResource(R.string.vn_audio_is_sent_securely_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("shared_voice_privacy"),
            )
        }
    }
}

@Composable
private fun Working(state: SharedVoiceState, onClose: () -> Unit) {
    val step = (state as? SharedVoiceState.Working)?.step ?: SharedVoiceState.Step.READING
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_voice_working"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(
                    stringResource(
                        when (step) {
                            SharedVoiceState.Step.READING -> R.string.vn_step_reading
                            SharedVoiceState.Step.TRANSLATING -> R.string.vn_step_translating
                            SharedVoiceState.Step.FINDING_LANGUAGE -> R.string.vn_step_finding
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StepLine(stringResource(R.string.vn_line_get), done = step != SharedVoiceState.Step.READING)
            StepLine(stringResource(R.string.vn_line_transcribe), done = step == SharedVoiceState.Step.FINDING_LANGUAGE)
            StepLine(stringResource(R.string.vn_line_find), done = false)
        }
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_voice_cancel")) { Text(stringResource(R.string.voice_cancel)) }
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
private fun ResultContent(result: VoiceNoteResult, onListen: () -> Unit, onReview: () -> Unit, onClose: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TagPill(stringResource(R.string.vn_original_lang, result.originalLanguage?.nativeName ?: result.originalCode.uppercase()))
        TagPill(stringResource(R.string.vn_your_language, result.userLanguage.nativeName))
    }

    if (result.unclear) {
        AlterLinguaCard {
            Text(
                stringResource(R.string.vn_unclear_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("shared_voice_unclear"),
            )
        }
    }

    AlterLinguaCard {
        Text(stringResource(R.string.vn_original_transcript), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.transcript, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("shared_voice_transcript"))
    }

    AlterLinguaCard {
        Text(stringResource(R.string.vn_translation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (result.alreadyInYourLanguage) {
            Text(stringResource(R.string.sv_same_language, result.userLanguage.nativeName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("shared_voice_same_language"))
        } else {
            Text(result.translation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("shared_voice_translation"))
        }
        OutlinedButton(onClick = onListen, enabled = result.canListen, modifier = Modifier.fillMaxWidth().testTag("shared_voice_listen")) {
            Text(
                when {
                    !result.canListen -> stringResource(R.string.vn_listen_no_voice, result.userLanguage.nativeName)
                    result.playing -> stringResource(R.string.voice_stop)
                    else -> stringResource(R.string.vn_listen)
                },
            )
        }
    }

    if (result.usefulUnits.isNotEmpty()) {
        AlterLinguaCard {
            Text(stringResource(R.string.vn_useful_words_and_phrases), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            result.usefulUnits.forEachIndexed { index, unit ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().testTag("shared_voice_unit"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(unit.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            unit.meaning ?: stringResource(R.string.vn_no_meaning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (result.savedToMap) MasteryChip(unit.state)
                }
            }
            Text(
                text = if (result.savedToMap) stringResource(R.string.vn_saved, result.usefulUnits.size)
                else stringResource(R.string.vn_not_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("shared_voice_saved"),
            )
        }
    }

    Button(onClick = onReview, enabled = result.savedToMap && result.usefulUnits.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("shared_voice_review")) {
        Text(stringResource(R.string.vn_review_useful_language))
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_voice_done")) { Text(stringResource(R.string.vn_done)) }
}

@Composable
private fun Problem(state: SharedVoiceState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    val text = SharedVoiceMessages.forState(state)
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("shared_voice_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.title.string(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text.message.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.heard?.let {
                Text(stringResource(R.string.vn_what_was_heard), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (state.canRetry) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("shared_voice_retry")) { Text(stringResource(R.string.vn_try_again)) }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("shared_voice_close")) { Text(stringResource(R.string.voice_close)) }
}
