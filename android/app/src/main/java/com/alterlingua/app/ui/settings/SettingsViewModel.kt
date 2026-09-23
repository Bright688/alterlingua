package com.alterlingua.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.notifications.IncomingOutcome
import com.alterlingua.app.storage.UserSettingsRepository
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A language with more than one way of typing, the style in use and the styles on offer. */
data class KeyboardStyleChoice(val language: Language, val selected: KeyboardStyle, val options: List<KeyboardStyle>)

data class SettingsUiState(
    val nativeLanguage: Language = UserSettings().nativeLanguage,
    val targetLanguage: Language = UserSettings().targetLanguage,
    val assistanceMode: AssistanceMode = AssistanceMode.FULL_SUPPORT,
    val dailyReminderOn: Boolean = true,
    val reminderTime: LocalTime = UserSettings.DEFAULT_REMINDER_TIME,
    val incomingTranslation: Boolean = true,
    val floatingTranslationEnabled: Boolean = false,
    val learningFromMessages: Boolean = true,
    /** The language AlterLingua itself is shown in (independent of the source and target languages). */
    val appLanguage: Language = UserSettings().appLanguage,
    val detectSourceAutomatically: Boolean = true,
    val autoTranslateEnabled: Boolean = false,
    /** The typing styles of the language typed in, when it has more than one (中文, 日本語); empty for every other language. */
    val keyboardStyleChoices: List<KeyboardStyleChoice> = emptyList(),
    /** How the latest incoming message ended (no message text). */
    val lastIncoming: IncomingOutcome? = null,
    /** The result of the last "Delete all learning data": null before, true when everything was removed. */
    val dataErased: Boolean? = null,
)

/** Shows the saved settings and saves changes made here, including switching languages (CLAUDE.md section 6.20). */
class SettingsViewModel(
    private val repository: UserSettingsRepository,
    lastIncoming: Flow<IncomingOutcome?> = emptyFlow(),
    /** Removes everything learned from messages and practice; true when every part was removed. */
    private val eraseLearningData: suspend () -> Boolean = { true },
) : ViewModel() {

    private val erased = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, lastIncoming.onStart { emit(null) }, erased) { it, incoming, dataErased ->
        SettingsUiState(
            nativeLanguage = it.nativeLanguage,
            targetLanguage = it.targetLanguage,
            assistanceMode = it.assistanceMode,
            dailyReminderOn = it.dailyReminderEnabled,
            reminderTime = it.reminderTime,
            incomingTranslation = it.incomingTranslationEnabled,
            floatingTranslationEnabled = it.floatingTranslationEnabled,
            learningFromMessages = it.learningFromMessagesEnabled,
            appLanguage = it.appLanguage,
            detectSourceAutomatically = it.detectSourceAutomatically,
            autoTranslateEnabled = it.autoTranslateEnabled,
            // Only for the language typed in ("My language"), and only when it has more than one way of typing (中文, 日本語).
            keyboardStyleChoices = listOfNotNull(
                it.keyboardStyleFor(it.nativeLanguage.code)?.let { current ->
                    KeyboardStyleChoice(it.nativeLanguage, current, KeyboardStyle.forLanguage(it.nativeLanguage.code))
                },
            ),
            lastIncoming = incoming,
            dataErased = dataErased,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    /** Changes the language the user writes in. Picking the current learning language swaps the two. */
    fun onNativeLanguageSelected(language: Language) = save { it.withNativeLanguage(language) }

    /** Changes the language the user wants to communicate and learn in. Picking the current native language swaps the two. */
    fun onTargetLanguageSelected(language: Language) = save { it.withTargetLanguage(language) }

    /** Changes the language AlterLingua is shown in. Nothing else changes: not the source or target language, not the maps or progress. */
    fun onAppLanguageSelected(language: Language) = save { it.copy(appLanguage = language, appLanguageChosen = true) }

    /** Turns automatic detection of the source language on or off. */
    fun onDetectSourceChanged(enabled: Boolean) = save { it.copy(detectSourceAutomatically = enabled) }

    /** Turns the keyboard's automatic translate-as-you-pause-typing on or off (CLAUDE.md 17: off by default). */
    fun onAutoTranslateChanged(enabled: Boolean) = save { it.copy(autoTranslateEnabled = enabled) }

    /** Changes how [language] is typed (for example kana keys or romaji). Only that language's style changes; it is used whenever it is the language typed in. */
    fun onKeyboardStyleSelected(language: Language, style: KeyboardStyle) =
        save { it.copy(keyboardStyles = it.keyboardStyles + (language.code to style)) }

    fun onAssistanceModeSelected(mode: AssistanceMode) = save { it.copy(assistanceMode = mode) }

    fun onDailyReminderChanged(enabled: Boolean) = save { it.copy(dailyReminderEnabled = enabled) }

    fun onIncomingTranslationChanged(enabled: Boolean) = save { it.copy(incomingTranslationEnabled = enabled) }

    /** Turns the floating translation bubble on or off (needs "Display over other apps"; off by default). */
    fun onFloatingTranslationChanged(enabled: Boolean) = save { it.copy(floatingTranslationEnabled = enabled) }

    fun onLearningFromMessagesChanged(enabled: Boolean) = save { it.copy(learningFromMessagesEnabled = enabled) }

    /** Confirmed "Delete all learning data": removes the words, progress and lesson. The settings stay. */
    fun onEraseLearningData() {
        viewModelScope.launch { erased.value = eraseLearningData() }
    }

    private fun save(change: (UserSettings) -> UserSettings) {
        viewModelScope.launch { repository.update(change) }
    }
}
