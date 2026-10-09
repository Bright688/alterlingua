package com.alterlingua.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.components.MasteryChip
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import com.alterlingua.app.ui.theme.extendedColors

@Composable
fun LearnRoute(
    modifier: Modifier = Modifier,
    viewModel: LearnViewModel = viewModel(factory = AppViewModelProvider.Factory),
    randomViewModel: RandomLessonViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val randomState by randomViewModel.uiState.collectAsStateWithLifecycle()
    // New messages may have been translated while the tab was away: look again for today's lesson.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    // Random lessons are extra practice beyond the one daily lesson (see RandomLessonViewModel): never saved, a fresh
    // one is built each time the learner asks, so "random" is scoped to this one tab visit, not kept across it.
    var showingRandom by rememberSaveable { mutableStateOf(false) }
    if (showingRandom) {
        LearnScreen(
            state = randomState,
            onPrevious = randomViewModel::previous,
            onNext = randomViewModel::next,
            modifier = modifier,
            onRecognition = randomViewModel::recordRecognition,
            footer = {
                OutlinedButton(onClick = { showingRandom = false }, modifier = Modifier.fillMaxWidth().testTag("lrn_back_to_today")) {
                    Text(stringResource(R.string.lrn_back_to_today))
                }
            },
            practice = { card -> PracticeRoute(card) },
        )
    } else {
        LearnScreen(
            state = state,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            modifier = modifier,
            onRecognition = viewModel::recordRecognition,
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { randomViewModel.start(); showingRandom = true },
                        modifier = Modifier.fillMaxWidth().testTag("lrn_random_lesson"),
                    ) { Text(stringResource(R.string.lrn_random_lesson)) }
                    ReaderRoute()
                }
            },
            practice = { card -> PracticeRoute(card) },
        )
    }
}

@Composable
fun LearnScreen(
    state: LearnUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
    /** The learner said whether they already knew the card, before being shown whether they were right (see
     * [com.alterlingua.app.learning.map.LanguageMapService.recordRecognition]): this is the recall signal Adaptive
     * mode needs, separate from and stronger than pronunciation practice's imitation. */
    onRecognition: (LessonCard, Boolean) -> Unit = { _, _ -> },
    /** Extra content under the lesson, e.g. the On-demand reader. */
    footer: @Composable () -> Unit = {},
    /** Listen, Repeat and feedback for a card. The default is the disabled placeholder. */
    practice: @Composable (LessonCard) -> Unit = { PracticePlaceholder() },
) {
    ScreenFrame(
        title = stringResource(R.string.nav_learn),
        testTag = "screen_learn",
        modifier = modifier,
    ) {
        when (state) {
            LearnUiState.Loading -> Unit
            is LearnUiState.Empty -> EmptyLesson(state)
            is LearnUiState.Card -> LessonCardContent(state, onPrevious, onNext, nowMillis, onRecognition, practice)
            is LearnUiState.Complete -> LessonComplete(state.lesson)
        }
        footer()
    }
}

/** No lesson yet: it appears once messages have been translated (Stitch: Empty Learn). */
@Composable
private fun EmptyLesson(state: LearnUiState.Empty) {
    AlterLinguaCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lesson_empty"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.lrn_no_lesson_today_yet), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_empty_body, state.language.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.lrn_use_the_alterlingua_keyboard_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One card of the lesson (Stitch: Learn / Today's Lesson). */
@Composable
private fun LessonCardContent(
    state: LearnUiState.Card,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    nowMillis: Long,
    onRecognition: (LessonCard, Boolean) -> Unit,
    practice: @Composable (LessonCard) -> Unit,
) {
    val card = state.lesson.current
    TagPill(stringResource(R.string.lrn_item_of, state.index + 1, state.total))

    // Step indicator: one segment per card.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(state.total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i <= state.index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.cardBorder),
            )
        }
    }

    AlterLinguaCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = LessonText.kindLabel(card).string().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(if (card.kind == CardKind.WORD_CARD) "lesson_word_card" else "lesson_phrase_card"),
            )
            MasteryChip(card.masteryState)
        }
        Text(
            text = card.term,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("lesson_term"),
        )
        if (card.meaning != null) {
            Text(
                text = card.meaning,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("lesson_meaning"),
            )
        } else {
            Text(
                text = stringResource(R.string.lrn_meaning_not_available_right_now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("lesson_meaning_missing"),
            )
        }

        HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)

        Text(stringResource(R.string.lrn_where_you_met_it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = LessonText.metParts(card, nowMillis).map { it.string() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("lesson_context"),
        )
        LessonText.helpLine(card)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (card.context.reasons.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.extendedColors.chipSurface) {
                Text(
                    text = stringResource(R.string.lrn_why_this_item) + card.context.reasons.map { LessonText.reason(it).string() }.joinToString(" · "),
                    modifier = Modifier
                        .padding(12.dp)
                        .testTag("lesson_reasons"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (card.meaning != null) {
        RecallCheck(card, onRecognition)
    }

    practice(card)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPrevious, enabled = state.canGoBack, modifier = Modifier.weight(1f).testTag("lesson_back")) { Text(stringResource(R.string.action_back)) }
        Button(onClick = onNext, modifier = Modifier.weight(1f).testTag("lesson_next")) {
            Text(stringResource(if (state.isLast) R.string.lrn_finish_lesson else R.string.lrn_next_item))
        }
    }
}

/**
 * "Did you already know this?" (Stitch: Learn / recall check). The one honest way AlterLingua has today to ask the
 * learner to recall a word rather than just repeat it after hearing it, so Adaptive mode has real recognition
 * evidence to work from. A self-report, not a quiz; answering is optional and the question disappears once answered,
 * once per card (going back and returning asks again, matching the daily lesson's own "going back records nothing").
 */
@Composable
private fun RecallCheck(card: LessonCard, onRecognition: (LessonCard, Boolean) -> Unit) {
    var answered by rememberSaveable(card.key) { mutableStateOf(false) }
    if (answered) return
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("lesson_recall"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lrn_recall_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { answered = true; onRecognition(card, true) },
                    modifier = Modifier.weight(1f).testTag("lesson_recall_yes"),
                ) { Text(stringResource(R.string.lrn_recall_yes)) }
                OutlinedButton(
                    onClick = { answered = true; onRecognition(card, false) },
                    modifier = Modifier.weight(1f).testTag("lesson_recall_no"),
                ) { Text(stringResource(R.string.lrn_recall_no)) }
            }
        }
    }
}

/** What shows where practice goes when no practice is provided (previews and screen tests). */
@Composable
private fun PracticePlaceholder() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).testTag("lesson_listen")) { Text(stringResource(R.string.voice_listen)) }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).testTag("lesson_repeat")) { Text(stringResource(R.string.lrn_repeat)) }
    }
}

/** Done for today (Stitch: Lesson Complete). */
@Composable
private fun LessonComplete(lesson: DailyLesson) {
    AlterLinguaCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lesson_complete"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(stringResource(R.string.lrn_done_for_today), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_complete_body, lesson.cards.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    lesson.cards.forEach { card ->
        AlterLinguaCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(card.term, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    card.meaning?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MasteryChip(card.masteryState)
            }
        }
    }
}

private fun previewCard(term: String, type: UnitType, meaning: String?) = LessonCard(
    key = UnitKey("fr", term, type), term = term, kind = if (type == UnitType.WORD) CardKind.WORD_CARD else CardKind.PHRASE_CARD,
    meaning = meaning, meaningLanguage = meaning?.let { "en" },
    context = LessonContext(4, InteractionKind.INCOMING_MESSAGE, System.currentTimeMillis(), 1, listOf(com.alterlingua.app.learning.lessons.LessonReason.ASKED_TRANSLATION, com.alterlingua.app.learning.lessons.LessonReason.USEFUL)),
    masteryState = MasteryStatus.LEARNING,
)

@Preview(showBackground = true)
@Composable
private fun LearnCardPreview() {
    AlterLinguaTheme {
        LearnScreen(
            state = LearnUiState.Card(DailyLesson("2026-09-20", "fr", listOf(previewCard("devis", UnitType.WORD, "quotation"), previewCard("avant midi", UnitType.PHRASE, "before noon")))),
            onPrevious = {}, onNext = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LearnEmptyPreview() {
    AlterLinguaTheme { LearnScreen(state = LearnUiState.Empty(Languages.French), onPrevious = {}, onNext = {}) }
}
