package com.alterlingua.app.ui.components

import androidx.compose.ui.res.stringResource
import com.alterlingua.app.R
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alterlingua.app.learning.Language

/**
 * Menu listing languages by their own names (Français, 中文, 日本語) with the English name as a smaller label, and a tick on the current one.
 * Shared by onboarding and Settings so both behave the same. Test tags are "<prefix>_option_<code>".
 */
@Composable
fun LanguageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<Language>,
    selected: Language,
    onSelected: (Language) -> Unit,
    testTagPrefix: String,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(option.displayName)
                        option.secondaryName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingIcon = if (option == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.lm_selected)) }
                } else {
                    null
                },
                onClick = {
                    onDismiss()
                    onSelected(option)
                },
                modifier = Modifier.testTag("${testTagPrefix}_option_${option.code}"),
            )
        }
    }
}
