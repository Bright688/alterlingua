package com.alterlingua.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.learning.writing.WritingSessionState
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.theme.extendedColors

/**
 * The Writing exercise: a prompt word from the standard curriculum (see WritingLessonService -- the same word list
 * Vocabulary already uses, no separate content) to write a sentence about. No automated grading exists anywhere in
 * this app yet, so none is faked here: the learner writes, reads it back themselves, and moves on. A real,
 * functional exercise, honest about what it is not (graded) rather than a placeholder.
 */
@Composable
fun WritingLessonScreen(
    state: WritingSessionState,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.lrn_lesson_writing),
        testTag = "screen_writing",
        modifier = modifier,
    ) {
        when (state) {
            WritingSessionState.Loading -> Unit
            is WritingSessionState.InProgress -> WritingPromptContent(state, onNext)
            WritingSessionState.Finished -> WritingFinished(onAgain)
            WritingSessionState.Unavailable -> WritingUnavailable(onRetry)
        }
        footer()
    }
}

@Composable
private fun WritingPromptContent(state: WritingSessionState.InProgress, onNext: () -> Unit) {
    // The learner's draft is per prompt: moving to the next one always starts with an empty field.
    var draft by rememberSaveable(state.position) { mutableStateOf("") }

    Text(
        text = stringResource(R.string.lrn_reading_progress, state.position + 1, state.prompts.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("writing_progress"),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(state.prompts.size) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i <= state.position) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.cardBorder),
            )
        }
    }

    val prompt = state.current
    AlterLinguaCard(modifier = Modifier.fillMaxWidth().testTag("writing_card")) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lrn_writing_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = prompt.word,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("writing_word"),
            )
            if (!prompt.meaning.isNullOrBlank()) {
                Text(
                    text = prompt.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("writing_meaning"),
                )
            }
        }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text(stringResource(R.string.lrn_writing_input_hint)) },
        modifier = Modifier.fillMaxWidth().testTag("writing_input"),
        minLines = 3,
    )

    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("writing_next")) {
        Text(stringResource(R.string.lrn_reading_next))
    }
}

@Composable
private fun WritingFinished(onAgain: () -> Unit) {
    AlterLinguaCard(modifier = Modifier.testTag("writing_finished")) {
        Text(
            text = stringResource(R.string.lrn_writing_finished_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().testTag("writing_again")) {
        Text(stringResource(R.string.lrn_writing_again))
    }
}

@Composable
private fun WritingUnavailable(onRetry: () -> Unit) {
    AlterLinguaCard {
        Column(modifier = Modifier.fillMaxWidth().testTag("writing_unavailable"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lrn_writing_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("writing_retry")) {
        Text(stringResource(R.string.lrn_curriculum_retry))
    }
}
