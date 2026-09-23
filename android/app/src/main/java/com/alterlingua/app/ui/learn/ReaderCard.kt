package com.alterlingua.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.learning.assistance.ReaderSegment
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.MasteryChip

@Composable
fun ReaderRoute(viewModel: ReaderViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderCard(
        state = state,
        onInput = viewModel::onInputChanged,
        onShow = viewModel::show,
        onAsk = viewModel::ask,
        onDismissHelp = viewModel::dismissHelp,
        onClear = viewModel::clear,
    )
}

/**
 * On-demand reading on an AlterLingua screen. The text stays in the language being learned. Words are real links inside
 * AlterLingua's own text, so tapping one is supported here; nothing about this works inside WhatsApp's own chat bubbles.
 */
@Composable
fun ReaderCard(
    state: ReaderUiState,
    onInput: (String) -> Unit,
    onShow: () -> Unit,
    onAsk: (ReaderSegment) -> Unit,
    onDismissHelp: () -> Unit,
    onClear: () -> Unit,
) {
    val language = state.language?.displayName
    AlterLinguaCard {
        Text(stringResource(R.string.rd_read_with_help), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = when {
                state.reading -> stringResource(R.string.rd_tap_hint)
                language != null -> stringResource(R.string.rd_paste_hint, language)
                else -> stringResource(R.string.rd_paste_hint_any)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.reading) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth().testTag("reader_input"),
                label = { Text(if (language != null) stringResource(R.string.rd_text_in, language) else stringResource(R.string.rd_text)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions.Default,
            )
            Button(onClick = onShow, enabled = state.input.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("reader_show")) {
                Text(stringResource(R.string.rd_show_text))
            }
        } else {
            ReaderText(state.segments, onAsk)
            state.help?.let { HelpPanel(it, onDismissHelp) }
            TextButton(onClick = onClear, modifier = Modifier.testTag("reader_clear")) { Text(stringResource(R.string.rd_clear_text)) }
        }
    }
}

@Composable
private fun ReaderText(segments: List<ReaderSegment>, onAsk: (ReaderSegment) -> Unit) {
    val link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary))
    val text = buildAnnotatedString {
        for (segment in segments) {
            if (segment.askable) {
                withLink(LinkAnnotation.Clickable(tag = segment.normalized!!, styles = link) { onAsk(segment) }) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("reader_text"))
}

/** The answer for one word (Stitch's word-help sheet: meaning, Listen, learning). */
@Composable
private fun HelpPanel(help: WordHelpState, onDismiss: () -> Unit) {
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().testTag("reader_help"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (help) {
            is WordHelpState.Loading -> {
                Text(help.term, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.rd_finding_the_meaning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is WordHelpState.Shown -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(help.answer.term, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    MasteryChip(help.answer.state)
                }
                Text(
                    text = help.answer.meaning ?: stringResource(R.string.rd_no_meaning),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (help.answer.meaning != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("reader_meaning"),
                )
                Text(
                    text = stringResource(R.string.rd_added_to_your_learning_this),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("reader_added"),
                )
                // Listening arrives with pronunciation practice (a later milestone); it is shown, not working.
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.testTag("reader_listen")) { Text(stringResource(R.string.rd_listen_coming_soon)) }
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.voice_close)) }
    }
}
