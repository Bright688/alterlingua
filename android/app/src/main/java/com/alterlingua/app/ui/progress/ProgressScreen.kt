package com.alterlingua.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.progress.DependenceStatus
import com.alterlingua.app.learning.progress.ProgressReport
import com.alterlingua.app.learning.progress.WeekStats
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.UiText
import com.alterlingua.app.ui.components.DependenceChart
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.theme.extendedColors
import java.time.DayOfWeek

@Composable
fun ProgressRoute(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // New messages may have been translated while the tab was away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ProgressScreen(state = state, modifier = modifier)
}

@Composable
fun ProgressScreen(state: ProgressUiState, modifier: Modifier = Modifier) {
    ScreenFrame(
        title = stringResource(R.string.nav_progress),
        testTag = "screen_progress",
        modifier = modifier,
    ) {
        when (state) {
            ProgressUiState.Loading -> Unit
            is ProgressUiState.Ready -> ProgressContent(state.language.displayName, state.report)
        }
    }
}

@Composable
private fun ProgressContent(languageName: String, report: ProgressReport) {
    Text(
        text = stringResource(R.string.pg_progress_in, languageName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Totals(report)
    DependenceCard(report)
    MasteryTrendCard(report)
    ActivityCard(report)
    AssistanceCard(report)
}

// ---- words and states ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Totals(report: ProgressReport) {
    val t = report.totals
    FlowRow(
        modifier = Modifier.testTag("progress_totals"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        Tile(stringResource(R.string.pg_encountered), t.encountered, MaterialTheme.colorScheme.primaryContainer, t.encountered, Modifier.weight(1f), "progress_encountered")
        Tile(stringResource(R.string.mastery_learning), t.learning, MaterialTheme.extendedColors.mastery(MasteryStatus.LEARNING).accent, t.encountered, Modifier.weight(1f), "progress_learning")
        Tile(stringResource(R.string.mastery_familiar), t.familiar, MaterialTheme.extendedColors.mastery(MasteryStatus.FAMILIAR).accent, t.encountered, Modifier.weight(1f), "progress_familiar")
        Tile(stringResource(R.string.mastery_mastered), t.mastered, MaterialTheme.extendedColors.mastery(MasteryStatus.MASTERED).accent, t.encountered, Modifier.weight(1f), "progress_mastered")
    }
}

@Composable
private fun Tile(label: String, value: Int, color: Color, total: Int, modifier: Modifier, tag: String) {
    AlterLinguaCard(modifier = modifier.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else value.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.extendedColors.chipSurface,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

// ---- translation dependence ----

@Composable
private fun DependenceCard(report: ProgressReport) {
    var showDefinition by remember { mutableStateOf(false) }
    AlterLinguaCard {
        Text(stringResource(R.string.home_translation_dependence), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.pg_how_much_of_the_language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val status = report.dependence) {
            DependenceStatus.NoData -> EmptyState(
                tag = "progress_dependence_empty",
                title = stringResource(R.string.pg_no_history_yet),
                body = stringResource(R.string.pg_no_history_body),
            )
            is DependenceStatus.Collecting -> {
                Column(Modifier.testTag("progress_dependence_collecting"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pg_building_your_starting_point), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.pg_words_this_week, minOf(status.wordsThisWeek, status.wordsNeeded), status.wordsNeeded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LinearProgressIndicator(
                        progress = { (status.wordsThisWeek.toFloat() / status.wordsNeeded).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        trackColor = MaterialTheme.extendedColors.chipSurface,
                    )
                    Text(
                        stringResource(R.string.pg_trend_needs, status.weeksNeeded, status.wordsNeeded, status.measuredWeeks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    report.thisWeek.dependencePercent?.let {
                        Text(stringResource(R.string.pg_this_week_so_far, it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("progress_dependence_single"))
                    }
                }
            }
            is DependenceStatus.Ready -> Column(Modifier.testTag("progress_dependence_ready"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${status.current}%", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("progress_dependence_current"))
                    Text(stringResource(R.string.home_of_the_words_you_met), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                DependenceChart(values = status.points.map { it.dependencePercent!! }, height = 120.dp)
                status.points.forEach { week ->
                    Row(Modifier.fillMaxWidth().testTag("progress_dependence_week"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.pg_week, week.weekNumber), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
                        LinearProgressIndicator(
                            progress = { week.dependencePercent!! / 100f },
                            modifier = Modifier.weight(0.5f),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            trackColor = MaterialTheme.extendedColors.chipSurface,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Text("${week.dependencePercent}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.2f))
                    }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.extendedColors.chipSurface) {
                    Text(
                        text = changeText(status).string(),
                        modifier = Modifier.padding(12.dp).testTag("progress_dependence_change"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        TextButton(onClick = { showDefinition = !showDefinition }, modifier = Modifier.testTag("progress_dependence_how")) {
            Text(stringResource(if (showDefinition) R.string.pg_hide_how else R.string.pg_how_calculated))
        }
        if (showDefinition) {
            Text(
                text = stringResource(R.string.pg_definition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("progress_dependence_definition"),
            )
        }
    }
}

/** What the change between the first and latest measured week says, without spin. */
internal fun changeText(status: DependenceStatus.Ready): UiText {
    val first = status.points.first()
    val last = status.points.last()
    val delta = last.dependencePercent!! - first.dependencePercent!!
    return when {
        delta < 0 -> UiText(R.string.pg_change_down, -delta, first.weekNumber, first.dependencePercent!!)
        delta > 0 -> UiText(R.string.pg_change_up, delta, first.weekNumber, first.dependencePercent!!)
        else -> UiText(R.string.pg_change_same, first.weekNumber, first.dependencePercent!!)
    }
}

// ---- mastery trend ----

@Composable
private fun MasteryTrendCard(report: ProgressReport) {
    AlterLinguaCard {
        Text(stringResource(R.string.pg_mastery_trend), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.pg_your_words_and_phrases_by), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!report.masteryTrendReady) {
            EmptyState("progress_mastery_empty", stringResource(R.string.pg_not_enough_history), stringResource(R.string.pg_mastery_empty_body))
            return@AlterLinguaCard
        }
        val weeks = report.weeks.filter { it.snapshot != null }
        val most = weeks.maxOf { it.snapshot!!.let { s -> s.learning + s.familiar + s.mastered } }.coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(120.dp).testTag("progress_mastery_trend"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weeks.forEach { week ->
                val s = week.snapshot!!
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Segment(s.mastered, most, MaterialTheme.extendedColors.mastery(MasteryStatus.MASTERED).accent)
                    Segment(s.familiar, most, MaterialTheme.extendedColors.mastery(MasteryStatus.FAMILIAR).accent)
                    Segment(s.learning, most, MaterialTheme.extendedColors.mastery(MasteryStatus.LEARNING).accent)
                }
            }
        }
        WeekLabels(weeks)
        Text(stringResource(R.string.pg_mastered_familiar_and_learning_top), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Segment(count: Int, most: Int, color: Color) {
    if (count <= 0) return
    Box(Modifier.fillMaxWidth().weight(count.toFloat() / most).background(color))
}

// ---- learning activity ----

@Composable
private fun ActivityCard(report: ProgressReport) {
    val week = report.thisWeek
    AlterLinguaCard {
        Text(stringResource(R.string.pg_learning_activity), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.pg_this_week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.testTag("progress_activity_numbers"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.pg_new_words_met, week.newWords), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.pg_actions_line, week.lessonCards, week.practiceTries, week.helpRequests), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (week.activeWeekdays.size == 1) stringResource(R.string.pg_day_active) else stringResource(R.string.pg_days_active, week.activeWeekdays.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M" to DayOfWeek.MONDAY, "T" to DayOfWeek.TUESDAY, "W" to DayOfWeek.WEDNESDAY, "T" to DayOfWeek.THURSDAY, "F" to DayOfWeek.FRIDAY, "S" to DayOfWeek.SATURDAY, "S" to DayOfWeek.SUNDAY).forEach { (letter, day) ->
                val on = day in week.activeWeekdays
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.chipSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (report.activityTrendReady) {
            Bars(report.weeks, { it.learningActions + it.wordsMet }, MaterialTheme.colorScheme.primaryContainer, "progress_activity_trend")
            Text(stringResource(R.string.pg_words_met_plus_lesson_cards), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            EmptyState("progress_activity_empty", stringResource(R.string.pg_not_enough_history), stringResource(R.string.pg_activity_empty_body))
        }
    }
}

// ---- translation assistance ----

@Composable
private fun AssistanceCard(report: ProgressReport) {
    AlterLinguaCard {
        Text(stringResource(R.string.pg_translation_assistance), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.pg_how_often_translation_covered_something),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.pg_this_week_assistance, report.thisWeek.assistance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("progress_assistance_now"))
        if (report.assistanceTrendReady) {
            Bars(report.weeks, { it.assistance }, MaterialTheme.extendedColors.mastery(MasteryStatus.LEARNING).accent, "progress_assistance_trend")
        } else {
            EmptyState("progress_assistance_empty", stringResource(R.string.pg_not_enough_history), stringResource(R.string.pg_assistance_empty_body))
        }
    }
}

// ---- shared pieces ----

@Composable
private fun EmptyState(tag: String, title: String, body: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bars(weeks: List<WeekStats>, value: (WeekStats) -> Int, color: Color, tag: String) {
    val most = weeks.maxOf(value).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(96.dp).testTag(tag), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { week ->
            val v = value(week)
            Box(Modifier.weight(1f).fillMaxHeight(if (v == 0) 0.02f else (v.toFloat() / most).coerceAtLeast(0.04f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
        }
    }
    WeekLabels(weeks)
}

@Composable
private fun WeekLabels(weeks: List<WeekStats>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weeks.forEach { Text(stringResource(R.string.pg_week_short, it.weekNumber), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
    }
}
