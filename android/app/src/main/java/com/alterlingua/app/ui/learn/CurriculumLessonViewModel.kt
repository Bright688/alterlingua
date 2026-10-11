package com.alterlingua.app.ui.learn

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.billing.BillingManager
import com.alterlingua.app.billing.SubscriptionOffer
import com.alterlingua.app.billing.TierOffers
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.curriculum.CurriculumLessonService
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Standard/Premium lessons screen's state (CLAUDE.md: "apart from getting from the conversation... normal
 * lessons"). Unlike the daily micro-lesson, nothing here is saved beyond the progress counters in
 * [CurriculumLessonService]: a fresh session for today is built each time [start] is called.
 *
 * Listening uses the phone's own voice directly (not the shared pronunciation-practice flow, which is scoped to the
 * single active target language): Premium can switch which language it is seeing, so Listen must speak whichever
 * language the current card is actually in.
 *
 * Standard and Premium are two separate Google Play subscriptions (CLAUDE.md section 32), each with its own card in
 * the Learn tab and its own price here ([offers]) — tapping either card's own Subscribe button starts that exact
 * product's purchase flow ([subscribe]), never a shared or merged one.
 */
class CurriculumLessonViewModel(
    private val service: CurriculumLessonService,
    private val speaker: Speaker,
    settings: UserSettingsRepository,
    private val billingManager: BillingManager?,
) : ViewModel() {

    private val state = MutableStateFlow<CurriculumSessionState>(CurriculumSessionState.Loading)
    val uiState: StateFlow<CurriculumSessionState> = state.asStateFlow()

    private val listeningState = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningState.asStateFlow()

    private val canListenState = MutableStateFlow(false)
    val canListen: StateFlow<Boolean> = canListenState.asStateFlow()

    /** The current plan, so the Learn tab's Standard/Premium cards can show "Open" or their own Subscribe buttons. */
    val tier: StateFlow<Tier> = settings.settings.map { it.tier }.stateIn(viewModelScope, SharingStarted.Eagerly, Tier.FREE)

    /** What Google Play's catalog looks like right now for Standard and Premium. */
    val offers: StateFlow<List<TierOffers>> = (billingManager?.offers ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Builds (or rebuilds) today's session. [language] picks which of the learner's languages to show (Premium
     * only; ignored otherwise) — null means the active target language.
     */
    fun start(language: Language? = null) {
        speaker.stop()
        listeningState.value = false
        state.value = CurriculumSessionState.Loading
        viewModelScope.launch { show(service.today(language)) }
    }

    /** The learner said whether they already knew the current card, then moved on: records both and advances. */
    fun next(alreadyKnew: Boolean) {
        val current = state.value as? CurriculumSessionState.InProgress ?: return
        speaker.stop()
        listeningState.value = false
        viewModelScope.launch { show(service.next(current, alreadyKnew)) }
    }

    /** Listen: the phone's own voice reads the current card's term, in the language it is actually shown in. */
    fun listen() {
        val current = state.value as? CurriculumSessionState.InProgress ?: return
        if (listeningState.value) {
            speaker.stop()
            listeningState.value = false
            return
        }
        val started = speaker.speak(current.current.term, current.languages.current) { listeningState.value = false }
        if (started) listeningState.value = true
    }

    /** Starts Google Play's own purchase sheet for this exact Standard or Premium offer. */
    fun subscribe(activity: Activity, offer: SubscriptionOffer) {
        billingManager?.purchase(activity, offer)
    }

    private suspend fun show(result: CurriculumSessionState) {
        state.value = result
        val language = (result as? CurriculumSessionState.InProgress)?.languages?.current
        canListenState.value = language != null && speaker.canSpeak(language)
    }

    override fun onCleared() {
        speaker.stop()
    }
}
