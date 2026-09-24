package com.alterlingua.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.storage.UserSettingsRepository
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The onboarding screens, in order. Choosing the app language (step 1 of 13) comes before these, on its own screen, so the steps
 * here are numbered from 2: source language, target language, reason, current level, assistance mode, reminder, keyboard,
 * incoming translation, floating translation, live chat-screen translation, microphone, and the last screen.
 * [OnboardingStep.KEYBOARD_STYLE] (how to type, for 中文 and 日本語) is only shown when the language chosen in the first step has
 * more than one typing style; otherwise it is skipped. [OnboardingStep.FLOATING_TRANSLATION] and
 * [OnboardingStep.LIVE_CHAT_TRANSLATION] are both optional and off by default (CLAUDE.md section 39): each one's own button is
 * the only thing that turns it on, and "Skip for now" always works regardless.
 */
enum class OnboardingStep { SOURCE, KEYBOARD_STYLE, TARGET, PURPOSE, LEVEL, ASSISTANCE, REMINDER, KEYBOARD, NOTIFICATIONS, FLOATING_TRANSLATION, LIVE_CHAT_TRANSLATION, MICROPHONE, COMPLETE }

/**
 * [settings] is the draft the user is editing. [loaded] turns true once any earlier saved answers
 * have been read, so the screens never flash default values first.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SOURCE,
    val settings: UserSettings = UserSettings(),
    val loaded: Boolean = false,
) {
    /** The screens that apply to this user, in order (the typing-style screen only for languages with more than one style). */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter {
            it != OnboardingStep.KEYBOARD_STYLE || KeyboardStyle.forLanguage(settings.nativeLanguage.code).isNotEmpty()
        }

    /** Step 1 is the app-language screen shown before onboarding, so these start at 2. */
    val stepNumber: Int get() = steps.indexOf(step).coerceAtLeast(0) + 2
    val stepCount: Int get() = steps.size + 1
    val canGoBack: Boolean get() = step != OnboardingStep.SOURCE
    val isLastStep: Boolean get() = step == OnboardingStep.COMPLETE
}

/**
 * Drives onboarding. Answers are saved each time the user continues, so closing the app part-way
 * keeps them. Only [finish] marks onboarding as completed. The current step survives rotation and
 * the system closing the app in the background.
 */
class OnboardingViewModel(
    private val repository: UserSettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = savedState.get<String>(KEY_STEP)
                ?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
                ?: OnboardingStep.SOURCE,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.settings.first()
            _uiState.update { it.copy(settings = saved, loaded = true) }
        }
    }

    fun onNativeLanguageSelected(language: Language) = edit { it.withNativeLanguage(language) }

    fun onDetectSourceChanged(enabled: Boolean) = edit { it.copy(detectSourceAutomatically = enabled) }

    /** Picks how the language typed in is typed (for example pinyin on nine keys). Only that language's style changes. */
    fun onKeyboardStyleSelected(style: KeyboardStyle) =
        edit { it.copy(keyboardStyles = it.keyboardStyles + (it.nativeLanguage.code to style)) }

    fun onTargetLanguageSelected(language: Language) = edit { it.withTargetLanguage(language) }

    fun onPurposeSelected(purpose: LearningPurpose) = edit { it.copy(purpose = purpose) }

    fun onLevelSelected(level: LanguageLevel) = edit { it.copy(level = level) }

    fun onAssistanceModeSelected(mode: AssistanceMode) = edit { it.copy(assistanceMode = mode) }

    fun onReminderEnabledChanged(enabled: Boolean) = edit { it.copy(dailyReminderEnabled = enabled) }

    fun onReminderTimeChanged(time: LocalTime) = edit { it.copy(reminderTime = time) }

    /** Saves the answers so far and moves to the next screen. Does nothing on the last screen. */
    fun next() {
        val state = _uiState.value
        if (state.isLastStep) return
        saveDraft(state.settings)
        setStep(state.steps.first { it.ordinal > state.step.ordinal })
    }

    /** Goes to the previous screen. Returns false when already on the first screen. */
    fun back(): Boolean {
        val state = _uiState.value
        if (!state.canGoBack) return false
        setStep(state.steps.last { it.ordinal < state.step.ordinal })
        return true
    }

    /** Saves everything and marks onboarding as completed. The app then opens the main screens. */
    fun finish() {
        val draft = _uiState.value.settings
        viewModelScope.launch {
            repository.update { stored ->
                draft.copy(
                    onboardingCompleted = true,
                    microphonePermissionAsked = stored.microphonePermissionAsked,
                    incomingTranslationEnabled = stored.incomingTranslationEnabled,
                    learningFromMessagesEnabled = stored.learningFromMessagesEnabled,
                    floatingTranslationEnabled = stored.floatingTranslationEnabled,
                    liveChatTranslationEnabled = stored.liveChatTranslationEnabled,
                    liveChatTranslationConsentGiven = stored.liveChatTranslationConsentGiven,
                )
            }
        }
    }

    private fun edit(change: (UserSettings) -> UserSettings) =
        _uiState.update { it.copy(settings = change(it.settings)) }

    private fun setStep(step: OnboardingStep) {
        savedState[KEY_STEP] = step.name
        _uiState.update { it.copy(step = step) }
    }

    private fun saveDraft(draft: UserSettings) {
        viewModelScope.launch {
            // Keep the stored "completed" flag and the microphone flag as they are: onboarding does not own them.
            repository.update { stored ->
                draft.copy(
                    onboardingCompleted = stored.onboardingCompleted,
                    microphonePermissionAsked = stored.microphonePermissionAsked,
                    incomingTranslationEnabled = stored.incomingTranslationEnabled,
                    learningFromMessagesEnabled = stored.learningFromMessagesEnabled,
                    floatingTranslationEnabled = stored.floatingTranslationEnabled,
                    liveChatTranslationEnabled = stored.liveChatTranslationEnabled,
                    liveChatTranslationConsentGiven = stored.liveChatTranslationConsentGiven,
                )
            }
        }
    }

    private companion object {
        const val KEY_STEP = "onboarding_step_name"
    }
}
