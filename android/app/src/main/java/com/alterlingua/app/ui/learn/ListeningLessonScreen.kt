package com.alterlingua.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.alterlingua.app.learning.reading.ReadingSessionState
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.theme.extendedColors

/**
 * The Listening exercise: the same real sentences as Reading (see ReadingLessonService) and the same backend
 * endpoint -- no separate content exists or is needed, since the task here is simply to hear a sentence before
 * reading it, not a different source of text. Audio-first: the sentence (and its translation) stays hidden until
 * "Show text" is tapped, so the learner listens before reading, the reverse emphasis of Reading's own card (text
 * visible immediately, translation hidden).
 */
@Composable
fun ListeningLessonScreen(
    state: ReadingSessionState,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onListen: () -> Unit = {},
    onReveal: () -> Unit = {},
    listening: Boolean = false,
    canListen: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.lrn_lesson_listening),
        testTag = "screen_listening",
        modifier = modifier,
    ) {
        when (state) {
            ReadingSessionState.Loading -> Unit
            is ReadingSessionState.InProgress -> ListeningCardContent(state, onNext, onListen, onReveal, listening, canListen)
            ReadingSessionState.Finished -> ListeningFinished(onAgain)
            ReadingSessionState.Unavailable -> ListeningUnavailable(onRetry)
        }
        footer()
    }
}

@Composable
private fun ListeningCardContent(
    state: ReadingSessionState.InProgress,
    onNext: () -> Unit,
    onListen: () -> Unit,
    onReveal: () -> Unit,
    listening: Boolean,
    canListen: Boolean,
) {
    // Revealing is per sentence: moving to the next one always starts face down again.
    var revealed by rememberSaveable(state.position) { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.lrn_reading_progress, state.position + 1, state.cards.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("listening_progress"),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(state.cards.size) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i <= state.position) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.cardBorder),
            )
        }
    }

    val card = state.current
    AlterLinguaCard(modifier = Modifier.fillMaxWidth().testTag("listening_card")) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onListen,
                enabled = canListen,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("listening_listen"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    stringResource(if (listening) R.string.voice_stop_listening else R.string.prac_play),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (revealed) {
                Text(
                    text = card.sentence,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("listening_sentence"),
                )
                Text(
                    text = card.translation?.takeIf { it.isNotBlank() } ?: stringResource(R.string.lrn_meaning_not_available_right_now),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("listening_translation"),
                )
            }
        }
    }

    if (!revealed) {
        OutlinedButton(
            onClick = { revealed = true; onReveal() },
            modifier = Modifier.fillMaxWidth().testTag("listening_show_text"),
        ) {
            Text(stringResource(R.string.lrn_listening_show_text))
        }
    }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("listening_next")) {
        Text(stringResource(R.string.lrn_reading_next))
    }
}

@Composable
private fun ListeningFinished(onAgain: () -> Unit) {
    AlterLinguaCard(modifier = Modifier.testTag("listening_finished")) {
        Text(
            text = stringResource(R.string.lrn_listening_finished_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().testTag("listening_again")) {
        Text(stringResource(R.string.lrn_listening_again))
    }
}

@Composable
private fun ListeningUnavailable(onRetry: () -> Unit) {
    AlterLinguaCard {
        Column(modifier = Modifier.fillMaxWidth().testTag("listening_unavailable"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lrn_listening_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("listening_retry")) {
        Text(stringResource(R.string.lrn_curriculum_retry))
    }
}
