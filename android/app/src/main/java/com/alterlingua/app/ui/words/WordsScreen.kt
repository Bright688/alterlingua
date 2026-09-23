package com.alterlingua.app.ui.words

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.WordEntry
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.MasteryChip
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.label
import com.alterlingua.app.ui.theme.AlterLinguaTheme

@Composable
fun WordsRoute(
    modifier: Modifier = Modifier,
    viewModel: WordsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WordsScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        modifier = modifier,
    )
}

@Composable
fun WordsScreen(
    state: WordsUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (MasteryStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenFrame(
        title = stringResource(R.string.nav_words),
        testTag = "screen_words",
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.wd_search, state.summary.total)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter == null,
                onClick = { onFilterSelected(null) },
                label = { Text(stringResource(R.string.wd_all, state.summary.total)) },
                shape = CircleShape,
            )
            MasteryStatus.entries.forEach { status ->
                FilterChip(
                    selected = state.filter == status,
                    onClick = { onFilterSelected(status) },
                    label = { Text(stringResource(R.string.wd_status_count, status.label(), state.summary.countOf(status))) },
                    shape = CircleShape,
                )
            }
        }

        Text(
            text = stringResource(R.string.wd_from_your_conversations),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val visible = state.visibleWords
        if (visible.isEmpty()) {
            Text(
                text = stringResource(if (state.isEmpty) R.string.wd_empty else R.string.wd_no_matching_words),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            visible.forEach { word -> WordRow(word) }
        }
    }
}

@Composable
private fun WordRow(word: WordEntry, modifier: Modifier = Modifier) {
    AlterLinguaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.term,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (word.phonetic.isNotBlank()) {
                    Text(
                        text = word.phonetic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MasteryChip(word.status)
        }
        if (word.meaning.isNotBlank()) {
            Text(
                text = word.meaning,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.wd_encounters, word.encounters),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WordsScreenPreview() {
    AlterLinguaTheme {
        WordsScreen(
            state = WordsUiState(com.alterlingua.app.learning.LanguageMapSummary(1, 2, 1, 1), listOf(WordEntry("devis", "", "quotation", MasteryStatus.LEARNING, 8))),
            onQueryChange = {},
            onFilterSelected = {},
        )
    }
}
