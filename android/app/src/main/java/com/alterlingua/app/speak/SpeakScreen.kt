package com.alterlingua.app.speak

import androidx.compose.foundation.layout.Arrangement
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.share.SharedVoiceMessages
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string

/**
 * Translated outgoing voice: speak in your language, hear the translation spoken, share it. Follows the voice states of the
 * keyboard's voice translation design (record, understanding, result with Listen), on its own screen because the Android
 * Share sheet cannot be opened from a keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakScreen(
    state: SpeakState,
    onChooseTarget: (Language) -> Unit,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onRetry: () -> Unit,
    onListen: () -> Unit,
    onShare: () -> Unit,
    onRecordAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = state.phase
    val locked = phase is SpeakPhase.Recording || phase is SpeakPhase.Working
    Surface(modifier = modifier.fillMaxSize().testTag("screen_speak"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.speak_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = state.spoken?.let { stringResource(R.string.vm_intro_lang, it.nativeName) } ?: stringResource(R.string.vm_intro_own),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (phase !is SpeakPhase.Result) {
                Text(stringResource(R.string.toolbar_translate_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("speak_targets")) {
                    Languages.supported.filter { it != state.spoken }.forEach { language ->
                        FilterChip(
                            selected = language == state.target,
                            onClick = { onChooseTarget(language) },
                            enabled = !locked,
                            label = { Text(language.nativeName) },
                            modifier = Modifier.testTag("speak_target_${language.code}"),
                        )
                    }
                }
            }

            when (phase) {
                SpeakPhase.Idle -> Button(onClick = onRecord, enabled = state.target != null, modifier = Modifier.fillMaxWidth().testTag("speak_record")) { Text(stringResource(R.string.vm_record)) }
                SpeakPhase.NeedsPermission -> AlterLinguaCard {
                    Column(Modifier.testTag("speak_permission"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.vm_alterlingua_needs_the_microphone_to), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Button(onClick = onAllowMicrophone) { Text(stringResource(R.string.voice_allow)) }
                    }
                }
                is SpeakPhase.Recording -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("speak_recording")) {
                    Text(stringResource(R.string.vm_recording, (phase.elapsedMillis / 1000).toInt()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    LinearProgressIndicator(progress = { phase.level }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth().testTag("speak_stop")) { Text(stringResource(R.string.voice_stop_listening)) }
                }
                SpeakPhase.Working -> AlterLinguaCard {
                    Column(Modifier.testTag("speak_working"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(state.target?.let { stringResource(R.string.vm_working, it.nativeName) } ?: stringResource(R.string.vm_working_generic), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                is SpeakPhase.Result -> Result(phase.value, onListen, onShare, onRecordAgain)
                is SpeakPhase.Problem -> {
                    val text = SharedVoiceMessages.forVoice(phase.failure)
                    AlterLinguaCard {
                        Column(Modifier.testTag("speak_problem"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text.title.string(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(text.message.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            phase.heard?.let { Text(stringResource(R.string.vm_heard_text, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    if (phase.canRetry) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("speak_retry")) { Text(stringResource(R.string.vn_try_again)) }
                    OutlinedButton(onClick = onRecord, modifier = Modifier.fillMaxWidth().testTag("speak_record_again")) { Text(stringResource(R.string.voice_record_again)) }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("speak_close")) { Text(if (phase is SpeakPhase.Result) "Done" else "Cancel") }
            Text(
                text = stringResource(R.string.vm_audio_is_sent_securely_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("speak_privacy"),
            )
        }
    }
}

@Composable
private fun Result(result: SpokenResult, onListen: () -> Unit, onShare: () -> Unit, onRecordAgain: () -> Unit) {
    AlterLinguaCard {
        Text(stringResource(R.string.vm_you_said), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.transcript, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("speak_transcript"))
    }
    AlterLinguaCard {
        Text(result.target.nativeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result.translation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("speak_translation"))
    }
    OutlinedButton(onClick = onListen, modifier = Modifier.fillMaxWidth().testTag("speak_listen")) { Text(if (result.playing) "Stop" else "Listen") }
    Button(onClick = onShare, modifier = Modifier.fillMaxWidth().testTag("speak_share")) { Text(stringResource(R.string.speak_share_title)) }
    Text(
        stringResource(R.string.vm_sharing_opens_android_s_share),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("speak_share_note"),
    )
    OutlinedButton(onClick = onRecordAgain, modifier = Modifier.fillMaxWidth().testTag("speak_record_again")) { Text(stringResource(R.string.voice_record_again)) }
}
