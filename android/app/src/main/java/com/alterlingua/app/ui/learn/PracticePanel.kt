package com.alterlingua.app.ui.learn

import androidx.activity.compose.rememberLauncherForActivityResult
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.pronunciation.PracticePhase
import com.alterlingua.app.learning.pronunciation.PracticeState
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict
import com.alterlingua.app.share.SharedVoiceMessages
import com.alterlingua.app.ui.UiText
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.components.AlterLinguaCard

/** Pronunciation practice for the card on screen. It starts over whenever the card changes. */
@Composable
fun PracticeRoute(card: LessonCard, viewModel: PracticeViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(card.key) { viewModel.bind(card) }
    // Leaving the card, the lesson or the tab stops any recording and deletes its audio.
    DisposableEffect(Unit) { onDispose { viewModel.release() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::permissionAnswered)
    PracticePanel(
        state = state,
        onListen = viewModel::listen,
        onRepeat = viewModel::repeat,
        onStop = viewModel::stop,
        onAllowMicrophone = { permission.launch(Manifest.permission.RECORD_AUDIO) },
    )
}

/**
 * Listen, Repeat and simple feedback (Stitch: "Part G — Pronunciation"). The design shows percentages, pitch and vowel
 * analysis; those need an acoustic analysis provider AlterLingua does not have, so this shows only what a speech
 * recognizer can honestly tell: whether it understood the word.
 */
@Composable
fun PracticePanel(
    state: PracticeState,
    onListen: () -> Unit,
    onRepeat: () -> Unit,
    onStop: () -> Unit,
    onAllowMicrophone: () -> Unit,
) {
    val phase = state.phase
    val recording = phase is PracticePhase.Recording
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("lesson_practice")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onListen,
                enabled = state.canListen && !recording && phase !is PracticePhase.Assessing,
                modifier = Modifier.weight(1f).testTag("lesson_listen"),
            ) { Text(if (state.listening) "Stop" else "Listen") }
            if (recording) {
                Button(onClick = onStop, modifier = Modifier.weight(1f).testTag("lesson_repeat")) { Text(stringResource(R.string.voice_stop_listening)) }
            } else {
                OutlinedButton(
                    onClick = onRepeat,
                    enabled = phase !is PracticePhase.Assessing,
                    modifier = Modifier.weight(1f).testTag("lesson_repeat"),
                ) { Text(stringResource(if (phase is PracticePhase.Feedback || phase is PracticePhase.Problem) R.string.prac_try_again else R.string.lrn_repeat)) }
            }
        }
        if (!state.canListen) {
            Text(
                stringResource(R.string.prac_listen_isn_t_available_this),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("lesson_listen_unavailable"),
            )
        }
        when (phase) {
            PracticePhase.Idle -> Text(
                stringResource(R.string.prac_listen_then_tap_repeat_and),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PracticePhase.NeedsPermission -> AlterLinguaCard {
                Column(Modifier.testTag("lesson_practice_permission"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.prac_to_practise_alterlingua_needs_the), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = onAllowMicrophone) { Text(stringResource(R.string.voice_allow)) }
                }
            }
            is PracticePhase.Recording -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("lesson_practice_recording")) {
                Text(stringResource(R.string.prac_listening, state.term), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(progress = { phase.level }, modifier = Modifier.fillMaxWidth())
            }
            PracticePhase.Assessing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("lesson_practice_assessing")) {
                Text(stringResource(R.string.prac_checking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is PracticePhase.Feedback -> Feedback(phase)
            is PracticePhase.Problem -> AlterLinguaCard {
                val text = SharedVoiceMessages.forVoice(phase.failure)
                Column(Modifier.testTag("lesson_practice_problem"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text.title.string(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text.message.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Feedback(phase: PracticePhase.Feedback) {
    val feedback = phase.feedback
    val (title, detail) = feedbackText(feedback.verdict, feedback.heard)
    AlterLinguaCard {
        Column(Modifier.testTag("lesson_practice_feedback"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title.string(),
                style = MaterialTheme.typography.titleLarge,
                color = if (feedback.verdict == PronunciationVerdict.GOOD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("lesson_practice_verdict"),
            )
            Text(detail.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.prac_this_checks_whether_speech_recognition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The words for each outcome. There are no percentages: a speech recognizer cannot give a trustworthy one. */
internal fun feedbackText(verdict: PronunciationVerdict, heard: String?): Pair<UiText, UiText> = when (verdict) {
    PronunciationVerdict.GOOD -> UiText(R.string.prac_good) to UiText(R.string.prac_understood)
    PronunciationVerdict.CLOSE -> UiText(R.string.prac_nearly) to UiText(R.string.prac_heard_retry, heard.orEmpty())
    PronunciationVerdict.TRY_AGAIN -> UiText(R.string.prac_try_again) to UiText(R.string.prac_heard_retry, heard.orEmpty())
    PronunciationVerdict.NOT_HEARD -> UiText(R.string.prac_try_again) to UiText(R.string.prac_not_heard)
}
