package com.alterlingua.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.accessibility.LiveChatReading
import com.alterlingua.app.billing.BillingManager
import com.alterlingua.app.billing.SubscriptionOffer
import com.alterlingua.app.billing.TierOffers
import com.alterlingua.app.entitlements.Feature
import com.alterlingua.app.entitlements.PREMIUM_CURRICULUM_DAILY_CAP
import com.alterlingua.app.entitlements.STANDARD_CURRICULUM_DAILY_CAP
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.entitlements.TierPolicy
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
import kotlinx.coroutines.flow.flowOf
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
    val liveChatTranslationEnabled: Boolean = false,
    val liveChatTranslationConsentGiven: Boolean = false,
    val learningFromMessages: Boolean = true,
    /** The language AlterLingua itself is shown in (independent of the source and target languages). */
    val appLanguage: Language = UserSettings().appLanguage,
    val detectSourceAutomatically: Boolean = true,
    val autoTranslateEnabled: Boolean = false,
    /** The typing styles of the language typed in, when it has more than one (中文, 日本語); empty for every other language. */
    val keyboardStyleChoices: List<KeyboardStyleChoice> = emptyList(),
    /** How the latest incoming message ended (no message text). */
    val lastIncoming: IncomingOutcome? = null,
    /** What live chat-screen translation has been doing, as numbers only; null until it has read a screen. */
    val liveChatReading: LiveChatReading? = null,
    /** The result of the last "Delete all learning data": null before, true when everything was removed. */
    val dataErased: Boolean? = null,
    /** The chat apps whose voice notes may be captured while listening (Android package names). */
    val voiceCaptureApps: Set<String> = emptySet(),
    /** Whether a captured voice note is transcribed and translated as soon as it ends. */
    val translateCapturedNotes: Boolean = true,
    /** Whether a quiet "Voice note captured" notification is posted for each captured note. */
    val notifyOnCapturedNotes: Boolean = true,
    /** True while a listening session for voice notes is running right now. */
    val voiceCaptureListening: Boolean = false,
    /** How many Standard/Premium-lesson words a day the user wants to learn (see CurriculumProgressService). */
    val curriculumDailyGoal: Int = UserSettings.DEFAULT_CURRICULUM_DAILY_GOAL,
    /** How many items the free daily micro-lesson should pick each day (see LessonService). */
    val microLessonDailyGoal: Int = UserSettings.DEFAULT_MICRO_LESSON_DAILY_GOAL,
    /** The current subscription tier, so Settings can show Premium's higher curriculum-goal option only to Premium. */
    val tier: Tier = Tier.FREE,
    /** What Google Play's catalog looks like right now for Standard and Premium (see Settings -> Plan). */
    val offers: List<TierOffers> = emptyList(),
)

/** Shows the saved settings and saves changes made here, including switching languages (CLAUDE.md section 6.20). */
class SettingsViewModel(
    private val repository: UserSettingsRepository,
    lastIncoming: Flow<IncomingOutcome?> = emptyFlow(),
    liveChatReading: Flow<LiveChatReading?> = emptyFlow(),
    /** Whether a voice-note listening session is running (see VoiceCaptureState). */
    voiceCaptureListening: Flow<Boolean> = emptyFlow(),
    /** Standard and Premium are sold as two separate Google Play subscriptions (CLAUDE.md section 32). */
    private val billingManager: BillingManager? = null,
    /** Removes everything learned from messages and practice; true when every part was removed. */
    private val eraseLearningData: suspend () -> Boolean = { true },
) : ViewModel() {

    private val erased = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            repository.settings,
            lastIncoming.onStart { emit(null) },
            erased,
            liveChatReading.onStart { emit(null) },
            voiceCaptureListening.onStart { emit(false) },
        ) { it, incoming, dataErased, liveReading, listening ->
            baseState(it, incoming, dataErased, liveReading, listening)
        },
        billingManager?.offers ?: flowOf(emptyList()),
    ) { state, offers -> state.copy(offers = offers) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private fun baseState(
        it: UserSettings,
        incoming: IncomingOutcome?,
        dataErased: Boolean?,
        liveReading: LiveChatReading?,
        listening: Boolean,
    ): SettingsUiState {
        return SettingsUiState(
            nativeLanguage = it.nativeLanguage,
            targetLanguage = it.targetLanguage,
            assistanceMode = it.assistanceMode,
            dailyReminderOn = it.dailyReminderEnabled,
            reminderTime = it.reminderTime,
            incomingTranslation = it.incomingTranslationEnabled,
            liveChatTranslationEnabled = it.liveChatTranslationEnabled,
            liveChatTranslationConsentGiven = it.liveChatTranslationConsentGiven,
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
            liveChatReading = liveReading,
            dataErased = dataErased,
            voiceCaptureApps = it.voiceCaptureApps,
            translateCapturedNotes = it.translateCapturedNotes,
            notifyOnCapturedNotes = it.notifyOnCapturedNotes,
            voiceCaptureListening = listening,
            curriculumDailyGoal = it.curriculumDailyGoal,
            microLessonDailyGoal = it.microLessonDailyGoal,
            tier = it.tier,
        )
    }

    /** Starts Google Play's own purchase sheet for this offer. */
    fun subscribe(activity: Activity, offer: SubscriptionOffer) {
        billingManager?.purchase(activity, offer)
    }

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

    /**
     * Turns live chat-screen translation on or off (needs Android's Accessibility permission, granted separately).
     * Off by default. The Settings screen always shows the in-app consent explanation before calling this with
     * `true`; this call itself is what records that consent was given, so it is not asked again on this device.
     */
    fun onLiveChatTranslationChanged(enabled: Boolean) = save {
        it.copy(liveChatTranslationEnabled = enabled, liveChatTranslationConsentGiven = it.liveChatTranslationConsentGiven || enabled)
    }

    fun onLearningFromMessagesChanged(enabled: Boolean) = save { it.copy(learningFromMessagesEnabled = enabled) }

    /** Changes how many Standard/Premium-lesson words a day the user wants to learn, capped by the current tier. */
    fun onCurriculumDailyGoalChanged(goal: Int) = save {
        val cap = if (TierPolicy.includes(it.tier, Feature.CURRICULUM_PREMIUM)) PREMIUM_CURRICULUM_DAILY_CAP else STANDARD_CURRICULUM_DAILY_CAP
        it.copy(curriculumDailyGoal = goal.coerceIn(1, cap))
    }

    /** Changes how many items the free daily micro-lesson should pick each day. */
    fun onMicroLessonDailyGoalChanged(goal: Int) = save { it.copy(microLessonDailyGoal = goal.coerceAtLeast(1)) }

    /** Turns automatic translation of captured voice notes on or off (off: a note is sent only when the user opens it). */
    fun onTranslateCapturedNotesChanged(enabled: Boolean) = save { it.copy(translateCapturedNotes = enabled) }

    /** Mutes or unmutes the "Voice note captured" notification (the note itself still appears on the keyboard). */
    fun onNotifyOnCapturedNotesChanged(enabled: Boolean) = save { it.copy(notifyOnCapturedNotes = enabled) }

    /** Adds or removes a chat app from those whose voice notes may be captured. Takes effect the next time listening starts. */
    fun onVoiceCaptureAppToggled(packageName: String) = save {
        it.copy(voiceCaptureApps = if (packageName in it.voiceCaptureApps) it.voiceCaptureApps - packageName else it.voiceCaptureApps + packageName)
    }

    /** Confirmed "Delete all learning data": removes the words, progress and lesson. The settings stay. */
    fun onEraseLearningData() {
        viewModelScope.launch { erased.value = eraseLearningData() }
    }

    private fun save(change: (UserSettings) -> UserSettings) {
        viewModelScope.launch { repository.update(change) }
    }
}
