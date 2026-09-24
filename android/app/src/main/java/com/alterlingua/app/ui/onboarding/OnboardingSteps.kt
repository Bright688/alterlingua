package com.alterlingua.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.setup.SetupStatus
import com.alterlingua.app.ui.setup.accessibilityServiceStatusLabel
import com.alterlingua.app.ui.setup.keyboardStatusLabel
import com.alterlingua.app.ui.setup.microphoneStatusLabel
import com.alterlingua.app.ui.setup.notificationStatusLabel
import com.alterlingua.app.ui.setup.overlayPermissionStatusLabel
import com.alterlingua.app.ui.components.LanguageMenu
import com.alterlingua.app.ui.components.NavIcons
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.theme.extendedColors
import java.time.LocalTime

@Composable
internal fun StepTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// ---------------------------------------------------------------------------------------------
// Steps 2 to 5: source language, target language, reason and level
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SourceStep(
    settings: UserSettings,
    onNativeSelected: (Language) -> Unit,
    onDetectChanged: (Boolean) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_source_title),
        subtitle = stringResource(R.string.onb_source_subtitle),
    )
    LanguageCard(
        label = stringResource(R.string.settings_source_language),
        language = settings.nativeLanguage,
        options = Languages.forNativeSelection,
        highlighted = false,
        onSelected = onNativeSelected,
        testTag = "native_language",
        modifier = Modifier.fillMaxWidth(),
    )
    AlterLinguaCard {
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
                    text = stringResource(R.string.settings_detect_source_help, settings.nativeLanguage.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.detectSourceAutomatically,
                onCheckedChange = onDetectChanged,
                modifier = Modifier.testTag("detect_source"),
            )
        }
    }
    NoteRow(Icons.Filled.Info, stringResource(R.string.onb_promise))
}

@Composable
internal fun TargetStep(
    settings: UserSettings,
    onTargetSelected: (Language) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_target_title),
        subtitle = stringResource(R.string.onb_target_subtitle),
    )
    LanguageCard(
        label = stringResource(R.string.settings_target_language),
        language = settings.targetLanguage,
        options = Languages.forLearningSelection,
        highlighted = true,
        onSelected = onTargetSelected,
        testTag = "target_language",
        modifier = Modifier.fillMaxWidth(),
    )
    // The pair, by native names, so the direction is clear (for example Español → Deutsch).
    Text(
        text = "${if (settings.detectSourceAutomatically) "AUTO" else settings.nativeLanguage.displayName} → ${settings.targetLanguage.displayName}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("language_pair"),
    )
    NoteRow(Icons.Filled.Info, stringResource(R.string.settings_target_language_help))
}

@Composable
internal fun PurposeStep(
    settings: UserSettings,
    onPurposeSelected: (LearningPurpose) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_purpose_title, settings.targetLanguage.displayName),
        subtitle = stringResource(R.string.onb_purpose_subtitle),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
        val purposes = listOf(
            LearningPurpose.WORK to OnboardingIcons.Work,
            LearningPurpose.TRAVEL to OnboardingIcons.Flight,
            LearningPurpose.FAMILY to Icons.Filled.Favorite,
            LearningPurpose.STUDY to NavIcons.Learn,
        )
        purposes.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (purpose, icon) ->
                    PurposeChip(
                        label = purpose.label(),
                        icon = icon,
                        selected = settings.purpose == purpose,
                        onSelect = { onPurposeSelected(purpose) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("purpose_${purpose.name}"),
                    )
                }
            }
        }
    }
}

/** How the language typed in is typed, for languages with more than one way (日本語: kana keys or romaji). */
@Composable
internal fun KeyboardStyleStep(
    settings: UserSettings,
    onStyleSelected: (KeyboardStyle) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_style_title, settings.nativeLanguage.displayName),
        subtitle = stringResource(R.string.onb_style_subtitle),
    )
    val selected = settings.keyboardStyleFor(settings.nativeLanguage.code)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
        KeyboardStyle.forLanguage(settings.nativeLanguage.code).forEach { style ->
            StyleCard(
                title = stringResource(
                    when (style) {
                        KeyboardStyle.STROKE -> R.string.kbs_stroke
                        KeyboardStyle.ZHUYIN -> R.string.kbs_zhuyin
                        KeyboardStyle.PINYIN_26 -> R.string.kbs_pinyin_26
                        KeyboardStyle.KANA -> R.string.kbs_kana
                        KeyboardStyle.ROMAJI -> R.string.kbs_romaji
                    },
                ),
                description = stringResource(
                    when (style) {
                        KeyboardStyle.STROKE -> R.string.kbsd_stroke
                        KeyboardStyle.ZHUYIN -> R.string.kbsd_zhuyin
                        KeyboardStyle.PINYIN_26 -> R.string.kbsd_pinyin_26
                        KeyboardStyle.KANA -> R.string.kbsd_kana
                        KeyboardStyle.ROMAJI -> R.string.kbsd_romaji
                    },
                ),
                selected = selected == style,
                onSelect = { onStyleSelected(style) },
                modifier = Modifier.testTag("keyboard_style_${style.name}"),
            )
        }
    }
}

@Composable
internal fun LevelStep(
    settings: UserSettings,
    onLevelSelected: (LanguageLevel) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_level_title, settings.targetLanguage.displayName),
        subtitle = stringResource(R.string.onb_level_subtitle),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
        LanguageLevel.entries.forEach { level ->
            LevelCard(
                level = level,
                selected = settings.level == level,
                onSelect = { onLevelSelected(level) },
                modifier = Modifier.testTag("level_${level.name}"),
            )
        }
    }
}

/** A tappable card showing one language. Tapping opens a menu of the supported languages. */
@Composable
private fun LanguageCard(
    label: String,
    language: Language,
    options: List<Language>,
    highlighted: Boolean,
    onSelected: (Language) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable(role = Role.DropdownList) { menuOpen = true }
                .testTag(testTag),
            shape = MaterialTheme.shapes.large,
            color = if (highlighted) MaterialTheme.extendedColors.navIndicator else MaterialTheme.extendedColors.card,
            border = if (highlighted) null else BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (highlighted) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Smaller English label under the native name (empty for English, which needs none).
                Text(
                    text = language.secondaryName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LanguageMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            options = options,
            selected = language,
            onSelected = onSelected,
            testTagPrefix = testTag,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Step 3: Assistance mode
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AssistanceStep(
    targetLanguage: Language,
    selected: AssistanceMode,
    onSelected: (AssistanceMode) -> Unit,
) {
    StepTitle(
        title = stringResource(R.string.onb_assistance_mode),
        subtitle = stringResource(R.string.onb_how_much_translation_help_do),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
        ModeCard(
            icon = Icons.Filled.CheckCircle,
            title = AssistanceMode.FULL_SUPPORT.label(),
            tag = stringResource(R.string.onb_recommended),
            tagHighlighted = true,
            description = stringResource(R.string.onb_translate_everything_for_me_best),
            selected = selected == AssistanceMode.FULL_SUPPORT,
            onSelect = { onSelected(AssistanceMode.FULL_SUPPORT) },
            modifier = Modifier.testTag("mode_FULL_SUPPORT"),
        )
        ModeCard(
            icon = OnboardingIcons.TrendingUp,
            title = AssistanceMode.ADAPTIVE.label(),
            tag = stringResource(R.string.onb_smart_fade),
            tagHighlighted = false,
            description = stringResource(R.string.onb_adaptive_desc, targetLanguage.displayName),
            selected = selected == AssistanceMode.ADAPTIVE,
            onSelect = { onSelected(AssistanceMode.ADAPTIVE) },
            modifier = Modifier.testTag("mode_ADAPTIVE"),
        )
        ModeCard(
            icon = OnboardingIcons.TouchApp,
            title = AssistanceMode.ON_DEMAND.label(),
            tag = stringResource(R.string.onb_manual),
            tagHighlighted = false,
            description = stringResource(R.string.onb_translate_only_when_i_ask),
            selected = selected == AssistanceMode.ON_DEMAND,
            onSelect = { onSelected(AssistanceMode.ON_DEMAND) },
            modifier = Modifier.testTag("mode_ON_DEMAND"),
        )
    }
    NoteRow(Icons.Filled.Settings, stringResource(R.string.onb_you_can_change_this_anytime))
}

// ---------------------------------------------------------------------------------------------
// Step 4: Daily reminder
// ---------------------------------------------------------------------------------------------

private val ReminderPresets = listOf(
    "Morning" to LocalTime.of(8, 30),
    "Evening" to LocalTime.of(20, 0),
    "Night" to LocalTime.of(22, 0),
)

@Composable
internal fun ReminderStep(
    enabled: Boolean,
    time: LocalTime,
    onEnabledChanged: (Boolean) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    StepTitle(
        title = stringResource(R.string.onb_daily_micro_lesson),
        subtitle = stringResource(R.string.onb_lock_in_retention_with_an),
    )

    AlterLinguaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onb_daily_reminder),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.onb_remind_me_to_do_my),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                modifier = Modifier.testTag("reminder_switch"),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(if (enabled) R.string.onb_reminder_scheduled else R.string.onb_reminder_off),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = rememberFormattedTime(time),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("reminder_time"),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderPresets.forEach { (name, presetTime) ->
                    PresetChip(
                        name = name,
                        time = presetTime,
                        selected = time == presetTime,
                        enabled = enabled,
                        onSelect = { onTimeChanged(presetTime) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextButton(
                onClick = { pickerOpen = true },
                enabled = enabled,
                modifier = Modifier.testTag("reminder_custom_time"),
            ) { Text(stringResource(R.string.onb_choose_another_time)) }
        }
    }

    AlterLinguaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onb_what_you_ll_review_daily),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.onb_seconds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ReviewLine(1, stringResource(R.string.onb_review_phrase))
        ReviewLine(2, stringResource(R.string.onb_review_pron))
        ReviewLine(3, stringResource(R.string.onb_review_recall))
    }

    NoteRow(Icons.Filled.Info, stringResource(R.string.onb_reminders_start_once_notifications_are))

    if (pickerOpen) {
        TimePickerDialog(
            initial = time,
            onConfirm = {
                pickerOpen = false
                onTimeChanged(it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun PresetChip(
    name: String,
    time: LocalTime,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            onClick = onSelect,
            role = Role.RadioButton,
        ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.chipSurface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.labelMedium)
            Text(rememberFormattedTime(time), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReviewLine(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberDot(number)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Step 5: All set
// ---------------------------------------------------------------------------------------------

@Composable
internal fun CompleteStep(settings: UserSettings, setup: SetupStatus = SetupStatus()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.extendedColors.card,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(
                text = stringResource(R.string.onb_you_re_all_set),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.onb_ready_body, settings.nativeLanguage.displayName, settings.targetLanguage.displayName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.extendedColors.chipSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.onb_your_setup),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryRow(stringResource(R.string.settings_languages_title), "${settings.nativeLanguage.displayName} → ${settings.targetLanguage.displayName}")
            SummaryRow(stringResource(R.string.onb_summary_purpose), settings.purpose.label())
            SummaryRow(stringResource(R.string.onb_summary_level), settings.level.label())
            SummaryRow(stringResource(R.string.onb_summary_assistance), settings.assistanceMode.label())
            SummaryRow(
                stringResource(R.string.onb_daily_reminder),
                if (settings.dailyReminderEnabled) rememberFormattedTime(settings.reminderTime) else stringResource(R.string.onb_off),
            )
            SummaryRow(stringResource(R.string.onb_step_keyboard), keyboardStatusLabel(setup))
            SummaryRow(stringResource(R.string.onb_incoming_messages), notificationStatusLabel(setup))
            SummaryRow(stringResource(R.string.set_floating_translation), overlayPermissionStatusLabel(setup))
            SummaryRow(stringResource(R.string.set_live_chat_translation), accessibilityServiceStatusLabel(setup))
            SummaryRow(stringResource(R.string.onb_step_microphone), microphoneStatusLabel(setup))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.onb_change_it_any_time),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.onb_anything_you_skipped_can_be),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.extendedColors.card,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusChip(value)
        }
    }
}
