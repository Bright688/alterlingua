package com.alterlingua.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.localization.AppLanguage
import com.alterlingua.app.ui.theme.extendedColors
import java.util.Locale

/**
 * First launch, before the rest of onboarding: the user chooses the language AlterLingua itself is shown in (Stitch L1). Each
 * language is listed by its own name with "choose your language" written in that language, so anyone can find theirs. The
 * source and target languages are chosen in the following steps; this choice changes neither.
 *
 * The phone's language is preselected when AlterLingua offers it. Nothing is saved until Continue.
 */
@Composable
fun AppLanguageScreen(
    onConfirm: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Languages.fromCode(Locale.getDefault().language) ?: Languages.English) }
    // Everything on this screen after the language list is shown in the language currently selected.
    val chosenContext = remember(selected) { AppLanguage.wrap(context, selected) }

    Surface(modifier = modifier.fillMaxSize().testTag("screen_app_language"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.systemBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = chosenContext.getString(R.string.choose_language_prompt),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("app_language_title"),
                )
                Text(
                    text = chosenContext.getString(R.string.app_language_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Languages.supported.forEach { language ->
                        val on = language == selected
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else MaterialTheme.extendedColors.card,
                            border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { selected = language }
                                .testTag("app_language_${language.code}"),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    // The language's own name, with "choose your language" in that language beneath it.
                                    Text(language.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        AppLanguage.wrap(context, language).getString(R.string.choose_language_prompt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (on) Icon(Icons.Filled.Check, contentDescription = chosenContext.getString(R.string.language_selected), tint = MaterialTheme.colorScheme.primaryContainer)
                            }
                        }
                    }
                }
                Text(
                    text = chosenContext.getString(R.string.app_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth().testTag("app_language_continue")) {
                Text(chosenContext.getString(R.string.action_continue))
            }
        }
    }
}
