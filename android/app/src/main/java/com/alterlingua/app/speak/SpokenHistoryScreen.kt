package com.alterlingua.app.speak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.ui.components.AlterLinguaCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Past translated voice messages (recorded or typed), newest first. Added at the owner's explicit request -- see
 * the privacy note on [SpokenHistoryEntry]. */
@Composable
fun SpokenHistoryScreen(
    state: SpokenHistoryUiState,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.sph_clear_all)) },
            text = { Text(stringResource(R.string.sph_clear_all_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }, modifier = Modifier.testTag("sph_clear_confirm")) {
                    Text(stringResource(R.string.sph_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.testTag("sph_clear_cancel")) { Text(stringResource(R.string.voice_cancel)) }
            },
        )
    }
    Surface(modifier = modifier.fillMaxSize().testTag("screen_spoken_history"), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.systemBarsPadding().fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("sph_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.sph_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("sph_loading"))
                }
                state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sph_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("sph_empty"),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("sph_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.entries, key = { it.id }) { entry -> HistoryRow(entry, onDelete) }
                    }
                    TextButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("sph_clear_all"),
                    ) { Text(stringResource(R.string.sph_clear_all)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: SpokenHistoryEntry, onDelete: (Long) -> Unit) {
    AlterLinguaCard(modifier = Modifier.testTag("sph_entry_${entry.id}")) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(formatted(entry.createdAtMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    entry.transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("sph_transcript_${entry.id}"),
                )
                Spacer(Modifier.height(6.dp))
                Text(entry.targetLanguage.nativeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    entry.translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("sph_translation_${entry.id}"),
                )
            }
            TextButton(onClick = { onDelete(entry.id) }, modifier = Modifier.testTag("sph_delete_${entry.id}")) {
                Text(stringResource(R.string.sph_delete))
            }
        }
    }
}

private fun formatted(millis: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
