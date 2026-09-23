package com.alterlingua.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.DependenceChart
import com.alterlingua.app.ui.components.LanguageMapBar
import com.alterlingua.app.ui.components.MetricTile
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.components.label
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import com.alterlingua.app.ui.theme.extendedColors

@Composable
fun HomeRoute(
    onOpenLearn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = androidx.compose.ui.platform.LocalContext.current
    HomeScreen(
        state = state,
        onOpenLearn = onOpenLearn,
        modifier = modifier,
        onOpenVoiceMessage = { context.startActivity(android.content.Intent(context, com.alterlingua.app.speak.SpeakActivity::class.java)) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenLearn: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVoiceMessage: () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.nav_home),
        testTag = "screen_home",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TagPill(stringResource(R.string.home_learning_lang, state.targetLanguage))
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricTile(
                value = state.translationsThisWeek.toString(),
                label = stringResource(R.string.home_messages_translated_this_week),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            MetricTile(
                value = state.newWordsThisWeek.toString(),
                label = stringResource(R.string.home_new_words_this_week),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        AlterLinguaCard {
            Text(
                text = stringResource(R.string.speak_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_speak_in_your_language_hear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(onClick = onOpenVoiceMessage, modifier = Modifier.testTag("home_voice_message")) {
                Text(stringResource(R.string.home_record_a_voice_message))
            }
        }

        AlterLinguaCard {
            TagPill(stringResource(R.string.home_tag_daily_lesson))
            Text(
                text = stringResource(R.string.home_today_s_micro_lesson),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (state.lessonItems.isEmpty()) stringResource(R.string.home_no_lesson_yet) else stringResource(R.string.home_items_minutes, state.lessonItems.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home_lesson_summary"),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.lessonItems.forEach { item ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.extendedColors.chipSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(item.term, style = MaterialTheme.typography.titleMedium)
                            if (item.meaning.isNotBlank()) {
                                Text(
                                    text = "(${item.meaning})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            // Nothing to continue until a lesson exists.
            if (state.lessonItems.isNotEmpty()) {
                Button(onClick = onOpenLearn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_continue_today_s_lesson))
                }
            }
        }

        AlterLinguaCard {
            Text(
                text = stringResource(R.string.home_personal_language_map),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_map_total, state.languageMap.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LanguageMapBar(state.languageMap)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                MasteryStatus.entries.forEach { status ->
                    StatusCount(
                        status = status,
                        count = state.languageMap.countOf(status),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AlterLinguaCard {
            Text(
                text = stringResource(R.string.home_translation_dependence),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val status = state.dependence
            if (status is com.alterlingua.app.learning.progress.DependenceStatus.Ready) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${status.current}%",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_of_the_words_you_met),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                DependenceChart(values = status.points.map { it.dependencePercent!! }, height = 72.dp)
                Text(
                    text = stringResource(R.string.home_week_summary, status.points.first().weekNumber, status.first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // No figure is invented: until there is enough real history this says so.
                Text(
                    text = stringResource(R.string.home_not_enough_history_yet_it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home_dependence_empty"),
                )
            }
        }
    }
}

@Composable
private fun StatusCount(status: MasteryStatus, count: Int, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.extendedColors.mastery(status)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.extendedColors.chipSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
            Text(
                text = status.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AlterLinguaTheme { HomeScreen(state = HomeUiState.empty(), onOpenLearn = {}) }
}
