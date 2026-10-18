package com.alterlingua.app.ui.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.curriculum.CurriculumLevelRange
import com.alterlingua.app.learning.curriculum.CurriculumUnitRange
import com.alterlingua.app.learning.curriculum.ForgeLevelState
import com.alterlingua.app.learning.curriculum.ForgeState
import com.alterlingua.app.learning.curriculum.ForgeUnitSummary
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill

/**
 * The Forge's level list: Language -> Level -> Unit -> Lesson, the one shared curriculum for every plan (CLAUDE.md's
 * own "Final decision": do not create separate Standard and Premium lesson libraries). The CEFR-proxy banding comes
 * from [com.alterlingua.app.learning.curriculum.CurriculumStructure] -- a frequency-based heuristic, not an
 * officially validated placement; see that file's own doc comment.
 *
 * "Continue" keeps today's own goal-driven session exactly as it worked before this screen existed: browsing a
 * level or unit here never touches that cursor or daily goal (see CurriculumLessonService.practiceUnit/practiceNext).
 */
@Composable
fun ForgeLevelsScreen(
    state: ForgeState,
    onOpenLevel: (CurriculumLevelRange) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    ScreenFrame(title = stringResource(R.string.lrn_forge_title), testTag = "screen_forge", modifier = modifier) {
        when (state) {
            ForgeState.Loading -> Unit
            is ForgeState.Levels -> {
                PlanBadge(state.tier)
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag("forge_continue")) {
                    Text(stringResource(R.string.lrn_forge_continue))
                }
                Text(
                    text = stringResource(R.string.lrn_forge_levels_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.levels.forEach { level ->
                    ForgeLevelRow(level.range.level.name, level.range.unitCount, level.isCurrent, onClick = { onOpenLevel(level.range) })
                }
            }
            ForgeState.Unavailable -> CurriculumUnavailable(onRetry)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("forge_back")) {
            Text(stringResource(R.string.lrn_back))
        }
    }
}

@Composable
private fun ForgeLevelRow(label: String, unitCount: Int, isCurrent: Boolean, onClick: () -> Unit) {
    AlterLinguaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("forge_level_$label")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(R.string.lrn_forge_unit_count, unitCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) TagPill(stringResource(R.string.lrn_forge_level_current))
        }
    }
}

/** One opened level's units (The Forge). Tapping a unit practices it directly -- see [ForgeLevelsScreen]'s doc. */
@Composable
fun ForgeLevelScreen(
    state: ForgeLevelState,
    onOpenUnit: (ForgeUnitSummary) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenFrame(title = stringResource(R.string.lrn_forge_title), testTag = "screen_forge_level", modifier = modifier) {
        when (state) {
            ForgeLevelState.Loading -> Unit
            is ForgeLevelState.Loaded -> {
                Text(state.level.level.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                state.units.forEach { unit ->
                    ForgeUnitRow(unit, onClick = { onOpenUnit(unit) })
                }
            }
            ForgeLevelState.Unavailable -> CurriculumUnavailable(onRetry)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("forge_level_back")) {
            Text(stringResource(R.string.lrn_back))
        }
    }
}

/**
 * One unit's Lesson menu: the owner's own worked example (French A1 Unit 3, "Introducing Yourself") lists six
 * exercise types every plan shares -- Vocabulary, Grammar, Listening, Reading, Writing, Speaking. Vocabulary (the
 * existing flashcard flow), Reading and Listening (both real sentences, see ReadingLessonScreen/ListeningLessonScreen)
 * have real content; Grammar, Writing and Speaking are shown honestly as "Coming soon" rather than faked, the same
 * pattern already used for billing's "Not available yet".
 */
@Composable
fun LessonMenuScreen(
    unit: ForgeUnitSummary,
    onOpenVocabulary: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenListening: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenFrame(
        title = stringResource(R.string.lrn_forge_unit_number, unit.range.unitNumber),
        testTag = "screen_lesson_menu",
        modifier = modifier,
    ) {
        LessonExerciseRow(stringResource(R.string.lrn_lesson_vocabulary), enabled = true, onClick = onOpenVocabulary, testTag = "lesson_vocabulary")
        LessonExerciseRow(stringResource(R.string.lrn_lesson_grammar), enabled = false, onClick = {}, testTag = "lesson_grammar")
        LessonExerciseRow(stringResource(R.string.lrn_lesson_listening), enabled = true, onClick = onOpenListening, testTag = "lesson_listening")
        LessonExerciseRow(stringResource(R.string.lrn_lesson_reading), enabled = true, onClick = onOpenReading, testTag = "lesson_reading")
        LessonExerciseRow(stringResource(R.string.lrn_lesson_writing), enabled = false, onClick = {}, testTag = "lesson_writing")
        LessonExerciseRow(stringResource(R.string.lrn_lesson_speaking), enabled = false, onClick = {}, testTag = "lesson_speaking")
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("lesson_menu_back")) {
            Text(stringResource(R.string.lrn_back))
        }
    }
}

@Composable
private fun LessonExerciseRow(label: String, enabled: Boolean, onClick: () -> Unit, testTag: String) {
    AlterLinguaCard(modifier = (if (enabled) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth().testTag(testTag)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(stringResource(R.string.lrn_lesson_coming_soon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ForgeUnitRow(unit: ForgeUnitSummary, onClick: () -> Unit) {
    val mastered = unit.masteredCount == unit.words.size && unit.words.isNotEmpty()
    AlterLinguaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("forge_unit_${unit.range.unitNumber}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.lrn_forge_unit_number, unit.range.unitNumber),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TagPill(
                text = if (mastered) {
                    stringResource(R.string.lrn_forge_unit_mastered)
                } else {
                    stringResource(R.string.lrn_forge_unit_progress, unit.masteredCount, unit.words.size)
                },
                modifier = Modifier.testTag("forge_unit_${unit.range.unitNumber}_progress"),
            )
        }
    }
}
