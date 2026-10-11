package com.alterlingua.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.curriculum.CurriculumLanguageChoice
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.theme.extendedColors

/**
 * Standard and Premium lessons: a large, shared, online word list, separate from and in addition to the free daily
 * micro-lesson drawn from real conversations (see CurriculumLessonService). A FREE user is stopped once the trial's
 * word count is reached, with [onUpgrade] offered instead of more words. Premium may switch which of its languages
 * it is seeing ([onLanguageSelected]); Standard and the FREE trial only ever show the active target language.
 */
@Composable
fun CurriculumLessonScreen(
    state: CurriculumSessionState,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onUpgrade: () -> Unit,
    onLanguageSelected: (Language) -> Unit = {},
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.set_standard_curriculum),
        testTag = "screen_curriculum",
        modifier = modifier,
    ) {
        when (state) {
            CurriculumSessionState.Loading -> Unit
            is CurriculumSessionState.InProgress -> {
                PlanBadge(state.tier)
                LanguagePicker(state.languages, onLanguageSelected)
                CurriculumCardContent(state, onNext)
            }
            is CurriculumSessionState.DoneForToday -> {
                PlanBadge(state.tier)
                LanguagePicker(state.languages, onLanguageSelected)
                CurriculumDone(state)
            }
            is CurriculumSessionState.TrialLimitReached -> {
                PlanBadge(Tier.FREE)
                CurriculumPaywall(state, onUpgrade)
            }
            CurriculumSessionState.Unavailable -> CurriculumUnavailable(onRetry)
        }
        footer()
    }
}

/** Which plan the learner is on right now, so Free trial / Standard / Premium is always visible, not implied. */
@Composable
private fun PlanBadge(tier: Tier) {
    val label = when (tier) {
        Tier.FREE -> stringResource(R.string.set_plan_free_trial)
        Tier.STANDARD -> stringResource(R.string.set_plan_standard)
        Tier.PREMIUM -> stringResource(R.string.set_plan_premium)
    }
    TagPill(label, modifier = Modifier.testTag("curriculum_plan_badge"))
}

/** Premium only: switch which learning language's curriculum is shown. Hidden when there is only one to show. */
@Composable
private fun LanguagePicker(choice: CurriculumLanguageChoice, onLanguageSelected: (Language) -> Unit) {
    if (choice.available.size <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("curriculum_language_picker"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choice.available.forEach { language ->
            FilterChip(
                selected = language == choice.current,
                onClick = { onLanguageSelected(language) },
                label = { Text(language.displayName) },
                shape = CircleShape,
                modifier = Modifier.testTag("curriculum_language_${language.code}"),
            )
        }
    }
}

@Composable
private fun CurriculumCardContent(state: CurriculumSessionState.InProgress, onNext: () -> Unit) {
    TagPill(stringResource(R.string.lrn_curriculum_progress, state.position + 1, state.cards.size))

    // Step indicator: one segment per card in today's session.
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
    AlterLinguaCard {
        Text(
            text = card.term,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("curriculum_term"),
        )
        if (card.meaning != null) {
            Text(
                text = card.meaning,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("curriculum_meaning"),
            )
        } else {
            Text(
                text = stringResource(R.string.lrn_meaning_not_available_right_now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("curriculum_meaning_missing"),
            )
        }
    }

    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("curriculum_learned")) {
        Text(stringResource(R.string.lrn_curriculum_learned))
    }
}

@Composable
private fun CurriculumDone(state: CurriculumSessionState.DoneForToday) {
    AlterLinguaCard {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("curriculum_done"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.lrn_curriculum_done_today_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_curriculum_done_today_body, state.goalToday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CurriculumPaywall(state: CurriculumSessionState.TrialLimitReached, onUpgrade: () -> Unit) {
    AlterLinguaCard {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("curriculum_paywall"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.lrn_curriculum_trial_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_curriculum_trial_body, state.limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth().testTag("curriculum_upgrade")) {
        Text(stringResource(R.string.lrn_curriculum_upgrade))
    }
}

@Composable
private fun CurriculumUnavailable(onRetry: () -> Unit) {
    AlterLinguaCard {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("curriculum_unavailable"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.lrn_curriculum_unavailable_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("curriculum_retry")) {
        Text(stringResource(R.string.lrn_curriculum_retry))
    }
}

/**
 * The placeholder shown by "View plans" (CLAUDE.md sections 31-32: real Google Play Billing is postponed past this
 * milestone). Scaffolding only: no purchase flow, just an honest "not yet" so the trial limit is never a dead end.
 */
@Composable
fun ComingSoonDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_coming_soon_title)) },
        text = { Text(stringResource(R.string.set_coming_soon_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        modifier = Modifier.testTag("coming_soon_dialog"),
    )
}
