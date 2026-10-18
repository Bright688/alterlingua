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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.learning.speaking.SpeakingSessionState
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.theme.extendedColors

/**
 * The Speaking exercise: the same real sentences as Reading and Listening, said aloud through the pronunciation
 * engine that already existed in this codebase (PracticeRoute/PracticeViewModel, built for the daily lesson but
 * never wired into a screen) -- on-device text-to-speech for a model, the microphone for a repeat, and real
 * on-device speech-recognition feedback, never a fabricated AI score.
 */
@Composable
fun SpeakingLessonScreen(
    state: SpeakingSessionState,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onAgain: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.lrn_lesson_speaking),
        testTag = "screen_speaking",
        modifier = modifier,
    ) {
        when (state) {
            SpeakingSessionState.Loading -> Unit
            is SpeakingSessionState.InProgress -> SpeakingCardContent(state, onNext)
            SpeakingSessionState.Finished -> SpeakingFinished(onAgain)
            SpeakingSessionState.Unavailable -> SpeakingUnavailable(onRetry)
        }
        footer()
    }
}

@Composable
private fun SpeakingCardContent(state: SpeakingSessionState.InProgress, onNext: () -> Unit) {
    Text(
        text = stringResource(R.string.lrn_reading_progress, state.position + 1, state.cards.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("speaking_progress"),
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

    PracticeRoute(card = state.current)

    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("speaking_next")) {
        Text(stringResource(R.string.lrn_reading_next))
    }
}

@Composable
private fun SpeakingFinished(onAgain: () -> Unit) {
    AlterLinguaCard(modifier = Modifier.testTag("speaking_finished")) {
        Text(
            text = stringResource(R.string.lrn_speaking_finished_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().testTag("speaking_again")) {
        Text(stringResource(R.string.lrn_speaking_again))
    }
}

@Composable
private fun SpeakingUnavailable(onRetry: () -> Unit) {
    AlterLinguaCard {
        Column(modifier = Modifier.fillMaxWidth().testTag("speaking_unavailable"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lrn_speaking_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("speaking_retry")) {
        Text(stringResource(R.string.lrn_curriculum_retry))
    }
}
