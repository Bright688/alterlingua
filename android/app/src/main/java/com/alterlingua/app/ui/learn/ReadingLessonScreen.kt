package com.alterlingua.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The Reading exercise: real sentences (see ReadingLessonService and backend/app/lessons/reading/data/SOURCE.md),
 * one at a time -- tap the card (or "Show translation") to reveal its meaning before moving on, the same active-recall
 * shape as the Vocabulary curriculum's own flashcards (CurriculumLessonScreen), but without a daily goal or mastery
 * tracking: a Reading session is just today's chosen set of sentences, start to finish.
 */
@Composable
fun ReadingLessonScreen(
    state: ReadingSessionState,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onListen: () -> Unit = {},
    listening: Boolean = false,
    canListen: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.lrn_lesson_reading),
        testTag = "screen_reading",
        modifier = modifier,
    ) {
        when (state) {
            ReadingSessionState.Loading -> Unit
            is ReadingSessionState.InProgress -> ReadingCardContent(state, onNext, onListen, listening, canListen)
            ReadingSessionState.Finished -> ReadingFinished(onAgain)
            ReadingSessionState.Unavailable -> ReadingUnavailable(onRetry)
        }
        footer()
    }
}

@Composable
private fun ReadingCardContent(
    state: ReadingSessionState.InProgress,
    onNext: () -> Unit,
    onListen: () -> Unit,
    listening: Boolean,
    canListen: Boolean,
) {
    // Revealing is per sentence: moving to the next one always starts face down again.
    var revealed by rememberSaveable(state.position) { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.lrn_reading_progress, state.position + 1, state.cards.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("reading_progress"),
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
    AlterLinguaCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reading_card")
            .clickable(enabled = !revealed) { revealed = true },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = card.sentence,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("reading_sentence"),
            )
            OutlinedButton(onClick = onListen, enabled = canListen, modifier = Modifier.testTag("reading_listen")) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(stringResource(if (listening) R.string.voice_stop_listening else R.string.prac_play))
            }
        }
        if (revealed) {
            Text(
                text = card.translation?.takeIf { it.isNotBlank() } ?: stringResource(R.string.lrn_meaning_not_available_right_now),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("reading_translation"),
            )
        }
    }

    if (!revealed) {
        OutlinedButton(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().testTag("reading_show_translation")) {
            Text(stringResource(R.string.lrn_reading_show_translation))
        }
    }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("reading_next")) {
        Text(stringResource(R.string.lrn_reading_next))
    }
}

@Composable
private fun ReadingFinished(onAgain: () -> Unit) {
    AlterLinguaCard(modifier = Modifier.testTag("reading_finished")) {
        Text(
            text = stringResource(R.string.lrn_reading_finished_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().testTag("reading_again")) {
        Text(stringResource(R.string.lrn_reading_again))
    }
}

@Composable
private fun ReadingUnavailable(onRetry: () -> Unit) {
    AlterLinguaCard {
        Column(modifier = Modifier.fillMaxWidth().testTag("reading_unavailable"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lrn_reading_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("reading_retry")) {
        Text(stringResource(R.string.lrn_curriculum_retry))
    }
}
