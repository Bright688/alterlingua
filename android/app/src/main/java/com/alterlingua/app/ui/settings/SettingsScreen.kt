package com.alterlingua.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.onboarding.rememberFormattedTime
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.ui.components.LanguageMenu
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.setup.SetupSettingsRows
import com.alterlingua.app.ui.setup.SetupUi
import com.alterlingua.app.ui.setup.accessibilityServiceStatusLabel
import com.alterlingua.app.ui.setup.rememberSetupUi
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import com.alterlingua.app.ui.theme.extendedColors

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setup = rememberSetupUi()
    SettingsScreen(
        state = state,
        setup = setup,
        onNativeLanguageSelected = viewModel::onNativeLanguageSelected,
        onTargetLanguageSelected = viewModel::onTargetLanguageSelected,
        onAssistanceModeSelected = viewModel::onAssistanceModeSelected,
        onDailyReminderChanged = viewModel::onDailyReminderChanged,
        onIncomingTranslationChanged = viewModel::onIncomingTranslationChanged,
        onLiveChatTranslationChanged = viewModel::onLiveChatTranslationChanged,
        onLearningFromMessagesChanged = viewModel::onLearningFromMessagesChanged,
        onEraseLearningData = viewModel::onEraseLearningData,
        onAppLanguageSelected = viewModel::onAppLanguageSelected,
        onDetectSourceChanged = viewModel::onDetectSourceChanged,
        onAutoTranslateChanged = viewModel::onAutoTranslateChanged,
        onKeyboardStyleSelected = viewModel::onKeyboardStyleSelected,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNativeLanguageSelected: (Language) -> Unit,
    onTargetLanguageSelected: (Language) -> Unit,
    onAssistanceModeSelected: (AssistanceMode) -> Unit,
    onDailyReminderChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    setup: SetupUi = SetupUi.None,
    onIncomingTranslationChanged: (Boolean) -> Unit = {},
    onLiveChatTranslationChanged: (Boolean) -> Unit = {},
    onLearningFromMessagesChanged: (Boolean) -> Unit = {},
    onEraseLearningData: () -> Unit = {},
    onAppLanguageSelected: (Language) -> Unit = {},
    onDetectSourceChanged: (Boolean) -> Unit = {},
    onAutoTranslateChanged: (Boolean) -> Unit = {},
    onKeyboardStyleSelected: (Language, com.alterlingua.app.learning.KeyboardStyle) -> Unit = { _, _ -> },
) {
    var confirmErase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmErase) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.set_delete_all_learning_data)) },
            text = {
                Text(
                    stringResource(R.string.set_this_removes_your_words_your),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmErase = false
                        onEraseLearningData()
                    },
                    modifier = Modifier.testTag("settings_erase_confirm"),
                ) { Text(stringResource(R.string.key_backspace)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmErase = false }, modifier = Modifier.testTag("settings_erase_cancel")) { Text(stringResource(R.string.voice_cancel)) }
            },
        )
    }
    var showLicences by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showLicences) LicencesDialog(onDismiss = { showLicences = false })
    var showLiveChatConsent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showLiveChatConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLiveChatConsent = false },
            title = { Text(stringResource(R.string.set_live_chat_translation)) },
            text = { Text(stringResource(R.string.set_live_chat_consent_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showLiveChatConsent = false
                        onLiveChatTranslationChanged(true)
                    },
                    modifier = Modifier.testTag("live_chat_consent_agree"),
                ) { Text(stringResource(R.string.set_turn_on)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLiveChatConsent = false },
                    modifier = Modifier.testTag("live_chat_consent_cancel"),
                ) { Text(stringResource(R.string.voice_cancel)) }
            },
        )
    }
    ScreenFrame(
        title = stringResource(R.string.nav_settings),
        testTag = "screen_settings",
        modifier = modifier,
    ) {
        SectionLabel(stringResource(R.string.settings_languages_title))
        AlterLinguaCard {
            LanguageRow(
                label = stringResource(R.string.settings_app_language),
                selected = state.appLanguage,
                options = Languages.supported,
                onSelected = onAppLanguageSelected,
                testTag = "settings_app_language",
            )
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            LanguageRow(
                label = stringResource(R.string.settings_source_language),
                selected = state.nativeLanguage,
                options = Languages.forNativeSelection,
                onSelected = onNativeLanguageSelected,
                testTag = "settings_native_language",
            )
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_detect_source),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_detect_source_help, state.nativeLanguage.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.detectSourceAutomatically,
                    onCheckedChange = onDetectSourceChanged,
                    modifier = Modifier.testTag("settings_detect_source"),
                )
            }
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_translate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_auto_translate_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoTranslateEnabled,
                    onCheckedChange = onAutoTranslateChanged,
                    modifier = Modifier.testTag("settings_auto_translate"),
                )
            }
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            LanguageRow(
                label = stringResource(R.string.settings_target_language),
                selected = state.targetLanguage,
                options = Languages.forLearningSelection,
                onSelected = onTargetLanguageSelected,
                testTag = "settings_target_language",
            )
            Text(
                text = stringResource(R.string.settings_target_language_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_languages_independent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_languages_independent"),
            )
        }

        SectionLabel(stringResource(R.string.onb_step_assistance))
        AlterLinguaCard {
            Column(Modifier.selectableGroup()) {
                ModeOption(
                    title = stringResource(R.string.mode_full_support),
                    description = stringResource(R.string.onb_translate_everything_for_me_best),
                    selected = state.assistanceMode == AssistanceMode.FULL_SUPPORT,
                    onSelect = { onAssistanceModeSelected(AssistanceMode.FULL_SUPPORT) },
                )
                ModeOption(
                    title = stringResource(R.string.mode_adaptive),
                    description = stringResource(R.string.set_help_me_less_as_i),
                    selected = state.assistanceMode == AssistanceMode.ADAPTIVE,
                    onSelect = { onAssistanceModeSelected(AssistanceMode.ADAPTIVE) },
                )
                ModeOption(
                    title = stringResource(R.string.mode_on_demand),
                    description = stringResource(R.string.onb_translate_only_when_i_ask),
                    selected = state.assistanceMode == AssistanceMode.ON_DEMAND,
                    onSelect = { onAssistanceModeSelected(AssistanceMode.ON_DEMAND) },
                )
            }
            Text(
                text = stringResource(R.string.set_you_can_change_this_at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel(stringResource(R.string.set_learning))
        AlterLinguaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onb_daily_reminder),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.set_remind_me_at, rememberFormattedTime(state.reminderTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.dailyReminderOn,
                    onCheckedChange = onDailyReminderChanged,
                )
            }
            Text(
                text = stringResource(R.string.onb_reminders_start_once_notifications_are),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.set_learn_from_my_messages),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.set_learn_desc, state.targetLanguage.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.learningFromMessages,
                    onCheckedChange = onLearningFromMessagesChanged,
                    modifier = Modifier.testTag("learning_from_messages_switch"),
                )
            }
        }

        SectionLabel(stringResource(R.string.onb_incoming_messages))
        AlterLinguaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.set_translate_whatsapp_messages),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.set_whatsapp_desc, state.nativeLanguage.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.incomingTranslation,
                    onCheckedChange = onIncomingTranslationChanged,
                    modifier = Modifier.testTag("incoming_translation_switch"),
                )
            }
            Text(
                text = stringResource(R.string.set_it_only_reads_the_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.lastIncoming?.let {
                Text(
                    text = incomingOutcomeText(it).string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("incoming_last_outcome"),
                )
            }
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.set_live_chat_translation),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.set_live_chat_translation_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.liveChatTranslationEnabled,
                    onCheckedChange = { turningOn ->
                        if (turningOn && !state.liveChatTranslationConsentGiven) showLiveChatConsent = true else onLiveChatTranslationChanged(turningOn)
                    },
                    modifier = Modifier.testTag("live_chat_translation_switch"),
                )
            }
            if (state.liveChatTranslationEnabled && !setup.status.accessibilityServiceEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    com.alterlingua.app.ui.setup.SetupStatusChip(
                        accessibilityServiceStatusLabel(setup.status),
                        done = false,
                        modifier = Modifier.testTag("live_chat_translation_permission_status"),
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = setup.actions.onOpenAccessibilitySettings,
                        modifier = Modifier.testTag("live_chat_translation_permission_action"),
                    ) { Text(stringResource(R.string.setup_app_settings)) }
                }
            }
            if (state.liveChatTranslationEnabled && setup.status.accessibilityServiceEnabled) {
                // Numbers only, never text: shows whether a chat screen is actually being read and how far it got.
                val reading = state.liveChatReading
                Text(
                    text = if (reading == null) {
                        stringResource(R.string.set_live_chat_reading_none)
                    } else {
                        stringResource(
                            R.string.set_live_chat_reading_stats,
                            reading.reads, reading.skipped, reading.items, reading.textBoxes, reading.messages, reading.captions,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("live_chat_reading"),
                )
            }
        }

        if (state.keyboardStyleChoices.isNotEmpty()) {
            SectionLabel(stringResource(R.string.set_keyboard_style))
            AlterLinguaCard {
                state.keyboardStyleChoices.forEach { choice ->
                    KeyboardStyleRow(choice.language, choice.selected, choice.options) { onKeyboardStyleSelected(choice.language, it) }
                }
                Text(
                    text = stringResource(R.string.set_keyboard_style_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel(stringResource(R.string.set_privacy))
        AlterLinguaCard {
            Text(
                text = stringResource(R.string.set_alterlingua_keeps_only_the_words),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            InfoRow(stringResource(R.string.set_export_my_learning_data), stringResource(R.string.set_available_later))
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            androidx.compose.material3.TextButton(
                onClick = { confirmErase = true },
                modifier = Modifier.fillMaxWidth().testTag("settings_erase"),
            ) { Text(stringResource(R.string.set_delete_all_learning_data2)) }
            state.dataErased?.let {
                Text(
                    text = stringResource(if (it) R.string.set_erased else R.string.set_erase_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_erase_result"),
                )
            }
        }

        androidx.compose.material3.TextButton(
            onClick = { showLicences = true },
            modifier = Modifier.fillMaxWidth().testTag("settings_licences"),
        ) { Text(stringResource(R.string.set_licences_row)) }

        SectionLabel(stringResource(R.string.set_setup))
        AlterLinguaCard {
            SetupSettingsRows(setup)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** A settings row showing a language by its own name; tapping it opens the language menu. */
@Composable
private fun LanguageRow(
    label: String,
    selected: Language,
    options: List<Language>,
    onSelected: (Language) -> Unit,
    testTag: String,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { menuOpen = true }
                .padding(vertical = 4.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = selected.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                selected.secondaryName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        LanguageMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            options = options,
            selected = selected,
            onSelected = onSelected,
            testTagPrefix = testTag,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AlterLinguaTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onNativeLanguageSelected = {},
            onTargetLanguageSelected = {},
            onAssistanceModeSelected = {},
            onDailyReminderChanged = {},
        )
    }
}

/** The licences of the open source software AlterLingua ships (Mozc, librime and what they use), read from the app's assets. */
@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val missing = stringResource(R.string.set_licences_missing)
    val text = androidx.compose.runtime.remember {
        try {
            context.assets.open("licenses/NOTICES.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: java.io.IOException) {
            missing
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_licences_title)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).testTag("licences_text"),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.testTag("licences_close")) {
                Text(stringResource(R.string.set_licences_close))
            }
        },
    )
}

@Composable
private fun keyboardStyleName(style: com.alterlingua.app.learning.KeyboardStyle): String = stringResource(
    when (style) {
        com.alterlingua.app.learning.KeyboardStyle.STROKE -> R.string.kbs_stroke
        com.alterlingua.app.learning.KeyboardStyle.ZHUYIN -> R.string.kbs_zhuyin
        com.alterlingua.app.learning.KeyboardStyle.PINYIN_26 -> R.string.kbs_pinyin_26
        com.alterlingua.app.learning.KeyboardStyle.KANA -> R.string.kbs_kana
        com.alterlingua.app.learning.KeyboardStyle.ROMAJI -> R.string.kbs_romaji
    },
)

/** How one language is typed (for example 日本語: kana keys or romaji); tapping opens the list. */
@Composable
private fun KeyboardStyleRow(
    language: Language,
    selected: com.alterlingua.app.learning.KeyboardStyle,
    options: List<com.alterlingua.app.learning.KeyboardStyle>,
    onSelected: (com.alterlingua.app.learning.KeyboardStyle) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { menuOpen = true }
                .padding(vertical = 4.dp)
                .testTag("settings_keyboard_style_${language.code}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = keyboardStyleName(selected),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { style ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(keyboardStyleName(style)) },
                    onClick = {
                        menuOpen = false
                        onSelected(style)
                    },
                    modifier = Modifier.testTag("settings_keyboard_style_${language.code}_${style.name}"),
                )
            }
        }
    }
}
