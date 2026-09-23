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

    private fun readStatus(askedBefore: Boolean, rationale: Boolean) = SetupStatus(
        keyboardEnabled = checker.keyboardEnabled(),
        keyboardSelected = checker.keyboardSelected(),
        notificationAccess = checker.notificationAccessGranted(),
        postNotifications = checker.postNotificationsAllowed(),
        microphone = microphoneStatus(checker.microphoneGranted(), askedBefore, rationale),
    )
}
