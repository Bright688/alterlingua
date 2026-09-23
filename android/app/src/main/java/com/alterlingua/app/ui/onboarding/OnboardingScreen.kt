package com.alterlingua.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.setup.MicrophoneStatus
import com.alterlingua.app.ui.setup.SetupUi
import com.alterlingua.app.ui.setup.rememberSetupUi
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import java.time.LocalTime

/** Everything the screens can ask the ViewModel to do. */
class OnboardingActions(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onFinish: () -> Unit,
    val onNativeSelected: (Language) -> Unit,
    val onDetectSourceChanged: (Boolean) -> Unit,
    val onKeyboardStyleSelected: (com.alterlingua.app.learning.KeyboardStyle) -> Unit,
    val onTargetSelected: (Language) -> Unit,
    val onPurposeSelected: (LearningPurpose) -> Unit,
    val onLevelSelected: (LanguageLevel) -> Unit,
    val onAssistanceModeSelected: (AssistanceMode) -> Unit,
    val onReminderEnabledChanged: (Boolean) -> Unit,
    val onReminderTimeChanged: (LocalTime) -> Unit,
)

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setup = rememberSetupUi()
    // The phone's back gesture goes to the previous step; on the first step it leaves the app as usual.
    BackHandler(enabled = state.canGoBack) { viewModel.back() }
    OnboardingScreen(
        state = state,
        setup = setup,
        actions = OnboardingActions(
            onBack = { viewModel.back() },
            onNext = viewModel::next,
            onFinish = viewModel::finish,
            onNativeSelected = viewModel::onNativeLanguageSelected,
            onDetectSourceChanged = viewModel::onDetectSourceChanged,
            onKeyboardStyleSelected = viewModel::onKeyboardStyleSelected,
            onTargetSelected = viewModel::onTargetLanguageSelected,
            onPurposeSelected = viewModel::onPurposeSelected,
            onLevelSelected = viewModel::onLevelSelected,
            onAssistanceModeSelected = viewModel::onAssistanceModeSelected,
            onReminderEnabledChanged = viewModel::onReminderEnabledChanged,
            onReminderTimeChanged = viewModel::onReminderTimeChanged,
        ),
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    setup: SetupUi = SetupUi.None,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .testTag("screen_onboarding"),
    ) {
        // Wait for any earlier saved answers so the screens never flash default values.
        if (!state.loaded) return@Column

        OnboardingHeader(
            stepNumber = state.stepNumber,
            stepCount = state.stepCount,
            stepTitle = stringResource(state.step.titleRes()),
            canGoBack = state.canGoBack,
            onBack = actions.onBack,
        )

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            label = "onboarding step",
        ) { step ->
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("onboarding_step_${step.name}"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StepContent(step = step, settings = state.settings, actions = actions, setup = setup)
                }
                Box(Modifier.padding(16.dp)) {
                    ContinueButton(
                        text = stringResource(step.buttonRes(state.settings.dailyReminderEnabled, setup)),
                        onClick = if (step == OnboardingStep.COMPLETE) actions.onFinish else actions.onNext,
                        icon = if (step == OnboardingStep.COMPLETE) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    settings: UserSettings,
    actions: OnboardingActions,
    setup: SetupUi,
) {
    when (step) {
        OnboardingStep.SOURCE -> SourceStep(settings, actions.onNativeSelected, actions.onDetectSourceChanged)
        OnboardingStep.KEYBOARD_STYLE -> KeyboardStyleStep(settings, actions.onKeyboardStyleSelected)
        OnboardingStep.TARGET -> TargetStep(settings, actions.onTargetSelected)
        OnboardingStep.PURPOSE -> PurposeStep(settings, actions.onPurposeSelected)
        OnboardingStep.LEVEL -> LevelStep(settings, actions.onLevelSelected)
        OnboardingStep.ASSISTANCE -> AssistanceStep(
            targetLanguage = settings.targetLanguage,
            selected = settings.assistanceMode,
            onSelected = actions.onAssistanceModeSelected,
        )
        OnboardingStep.REMINDER -> ReminderStep(
            enabled = settings.dailyReminderEnabled,
            time = settings.reminderTime,
            onEnabledChanged = actions.onReminderEnabledChanged,
            onTimeChanged = actions.onReminderTimeChanged,
        )
        OnboardingStep.KEYBOARD -> KeyboardStep(setup)
        OnboardingStep.NOTIFICATIONS -> NotificationsStep(setup)
        OnboardingStep.MICROPHONE -> MicrophoneStep(setup)
        OnboardingStep.COMPLETE -> CompleteStep(settings, setup.status)
    }
}

@androidx.annotation.StringRes
private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.SOURCE -> R.string.onb_step_source
    OnboardingStep.KEYBOARD_STYLE -> R.string.set_keyboard_style
    OnboardingStep.TARGET -> R.string.settings_target_language
    OnboardingStep.PURPOSE -> R.string.onb_step_purpose
    OnboardingStep.LEVEL -> R.string.onb_step_level
    OnboardingStep.ASSISTANCE -> R.string.onb_step_assistance
    OnboardingStep.REMINDER -> R.string.onb_step_reminder
    OnboardingStep.KEYBOARD -> R.string.onb_step_keyboard
    OnboardingStep.NOTIFICATIONS -> R.string.onb_step_incoming
    OnboardingStep.MICROPHONE -> R.string.onb_step_microphone
    OnboardingStep.COMPLETE -> R.string.onb_step_complete
}

@androidx.annotation.StringRes
private fun OnboardingStep.buttonRes(reminderOn: Boolean, setup: SetupUi): Int = when (this) {
    OnboardingStep.SOURCE, OnboardingStep.KEYBOARD_STYLE, OnboardingStep.TARGET, OnboardingStep.PURPOSE, OnboardingStep.LEVEL, OnboardingStep.ASSISTANCE -> R.string.action_continue
    OnboardingStep.REMINDER -> if (reminderOn) R.string.onb_set_reminder_continue else R.string.action_continue
    OnboardingStep.KEYBOARD -> if (setup.status.keyboardReady) R.string.action_continue else R.string.onb_skip_for_now
    OnboardingStep.NOTIFICATIONS -> if (setup.status.notificationAccess) R.string.action_continue else R.string.onb_skip_for_now
    OnboardingStep.MICROPHONE -> if (setup.status.microphone == MicrophoneStatus.GRANTED) R.string.action_continue else R.string.onb_continue_without_mic
    OnboardingStep.COMPLETE -> R.string.onb_start_using
}

/** Clock dialog for choosing a reminder time. Uses the phone's 12 or 24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onb_choose_reminder_time),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TimePicker(state = pickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.translation_cancel)) }
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) },
                        modifier = Modifier.testTag("time_picker_ok"),
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    AlterLinguaTheme {
        OnboardingScreen(state = OnboardingUiState(loaded = true), actions = previewActions())
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingLanguagesPreview() {
    AlterLinguaTheme {
        OnboardingScreen(
            state = OnboardingUiState(step = OnboardingStep.SOURCE, loaded = true),
            actions = previewActions(),
        )
    }
}

private fun previewActions() = OnboardingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
