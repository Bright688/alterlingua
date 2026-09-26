package com.alterlingua.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.capture.ChatApp

/**
 * The installed chat apps whose voice notes AlterLingua may capture, one checkbox each. Used by onboarding and Settings, so
 * the choice looks and works the same in both. Ticking an app starts nothing.
 */
@Composable
fun ChatAppChoices(installed: List<ChatApp>, chosen: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.testTag("chat_app_choices")) {
        if (installed.isEmpty()) {
            Text(stringResource(R.string.vc_no_apps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        installed.forEach { app ->
            val checked = app.packageName in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) { onToggle(app.packageName) }
                    .padding(vertical = 2.dp)
                    .testTag("chat_app_${app.packageName}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(app.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
