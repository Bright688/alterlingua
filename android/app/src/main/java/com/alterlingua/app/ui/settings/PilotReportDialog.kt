package com.alterlingua.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import kotlinx.coroutines.launch

/**
 * The pilot report consent (`docs/pilot.md` B4): shown before the report can be built, every time, not just once —
 * so what is being shared is never a forgotten checkbox from weeks ago.
 */
@Composable
fun PilotConsentDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_pilot_report_row)) },
        text = { Text(stringResource(R.string.set_pilot_consent_body)) },
        confirmButton = {
            TextButton(onClick = onAgree, modifier = Modifier.testTag("pilot_consent_agree")) {
                Text(stringResource(R.string.set_pilot_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("pilot_consent_cancel")) {
                Text(stringResource(R.string.voice_cancel))
            }
        },
    )
}

/**
 * Builds the pilot report and hands it to Android's own Share sheet (`docs/pilot.md` B3): nothing is ever sent
 * automatically or to anywhere of AlterLingua's own choosing. The exact JSON is shown before sharing, not hidden
 * behind the button.
 */
@Composable
fun PilotReportDialog(onDismiss: () -> Unit, viewModel: PilotReportViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.set_pilot_share_title)
    val scope = rememberCoroutineScope()
    var participant by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_pilot_report_row)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = participant,
                    onValueChange = { participant = it },
                    label = { Text(stringResource(R.string.set_pilot_participant_code)) },
                    supportingText = { Text(stringResource(R.string.set_pilot_participant_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.testTag("pilot_participant_code"),
                )
                val current = report
                when {
                    building -> CircularProgressIndicator(modifier = Modifier.testTag("pilot_report_building"))
                    current != null -> Text(
                        text = current,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).testTag("pilot_report_preview"),
                    )
                    else -> TextButton(
                        onClick = {
                            building = true
                            scope.launch {
                                report = viewModel.build(participant)
                                building = false
                            }
                        },
                        modifier = Modifier.testTag("pilot_report_generate"),
                    ) { Text(stringResource(R.string.set_pilot_generate)) }
                }
            }
        },
        confirmButton = {
            val toShare = report
            TextButton(
                enabled = toShare != null,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, toShare)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                    onDismiss()
                },
                modifier = Modifier.testTag("pilot_report_share"),
            ) { Text(stringResource(R.string.set_pilot_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("pilot_report_close")) { Text(stringResource(R.string.voice_cancel)) }
        },
    )
}
