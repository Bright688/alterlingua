package com.alterlingua.app.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the setup status up to date. The screens call [refresh] every time the app comes back to the front, because
 * the user changes these things in Android's own screens, which the app cannot watch directly.
 */
class SetupViewModel(
    private val checker: SetupChecker,
    private val repository: UserSettingsRepository,
) : ViewModel() {

    private val showMicrophoneRationale = MutableStateFlow(false)
    private val refreshCount = MutableStateFlow(0)

    val status: StateFlow<SetupStatus> = combine(
        repository.settings.map { it.microphonePermissionAsked }.distinctUntilChanged(),
        showMicrophoneRationale,
        refreshCount,
    ) { askedBefore, rationale, _ -> readStatus(askedBefore, rationale) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, readStatus(askedBefore = false, rationale = false))

    /**
     * Re-reads everything from Android. [showMicrophoneRationale] is Android's answer to "should we explain the
     * microphone again?", which only an Activity can ask, so the screen passes it in.
     */
    fun refresh(showMicrophoneRationale: Boolean) {
        this.showMicrophoneRationale.value = showMicrophoneRationale
        refreshCount.update { it + 1 }
    }

    /** Remember that the system microphone question is about to be shown, so a later "no" can be told from "never asked". */
    fun onMicrophoneRequested() {
        viewModelScope.launch { repository.update { it.copy(microphonePermissionAsked = true) } }
    }

    /** Turns the floating translation bubble on (needs "Display over other apps", requested separately by the caller right after). */
    fun onFloatingTranslationEnabled() {
        viewModelScope.launch { repository.update { it.copy(floatingTranslationEnabled = true) } }
    }

    /**
     * The user read the in-app disclosure for live chat-screen translation and agreed to turn it on (shown in full
     * on its own onboarding/Settings step before this is ever called — see CLAUDE.md section 39 and the Google Play
     * Accessibility API policy's in-app disclosure and consent requirement). Android's own Accessibility permission
     * is requested separately right after, by the caller.
     */
    fun onLiveChatTranslationAgreed() {
        viewModelScope.launch { repository.update { it.copy(liveChatTranslationEnabled = true, liveChatTranslationConsentGiven = true) } }
    }

    private fun readStatus(askedBefore: Boolean, rationale: Boolean) = SetupStatus(
        keyboardEnabled = checker.keyboardEnabled(),
        keyboardSelected = checker.keyboardSelected(),
        notificationAccess = checker.notificationAccessGranted(),
        postNotifications = checker.postNotificationsAllowed(),
        microphone = microphoneStatus(checker.microphoneGranted(), askedBefore, rationale),
        overlayPermission = checker.overlayPermissionGranted(),
        accessibilityServiceEnabled = checker.accessibilityServiceEnabled(),
    )
}
