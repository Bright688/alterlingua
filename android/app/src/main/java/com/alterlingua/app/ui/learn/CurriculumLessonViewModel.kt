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
import com.alterlingua.app.learning.curriculum.CurriculumLevelRange
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.learning.curriculum.CurriculumUnitRange
import com.alterlingua.app.learning.curriculum.ForgeLevelState
import com.alterlingua.app.learning.curriculum.ForgeState
import com.alterlingua.app.learning.curriculum.ForgeUnitSummary
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

    private val forgeLevelsState = MutableStateFlow<ForgeState>(ForgeState.Loading)
    /** The Forge's level list: Language -> Level -> Unit -> Lesson, the one shared curriculum for every plan. */
    val forgeLevels: StateFlow<ForgeState> = forgeLevelsState.asStateFlow()

    private val forgeLevelState = MutableStateFlow<ForgeLevelState>(ForgeLevelState.Loading)
    /** The units of whichever level was last opened with [openLevel]. */
    val forgeLevel: StateFlow<ForgeLevelState> = forgeLevelState.asStateFlow()

    /** Set only while [uiState] holds a unit being practiced (The Forge), so [next] routes to [practiceNext]
     * instead of today's own session, and so finishing it can name which unit just finished. */
    private var practicing: CurriculumUnitRange? = null

    /** Loads The Forge's level list for [language] (Premium only; ignored otherwise), or the active target language. */
    fun loadForge(language: Language? = null) {
        forgeLevelsState.value = ForgeState.Loading
        viewModelScope.launch { forgeLevelsState.value = service.forge(language) }
    }

    /** Opens one level: fetches its units, each with its own mastered-word count. */
    fun openLevel(range: CurriculumLevelRange, language: Language? = null) {
        forgeLevelState.value = ForgeLevelState.Loading
        viewModelScope.launch { forgeLevelState.value = service.forgeLevel(range, language) }
    }

    /** Starts practicing one unit directly (The Forge): never touches today's own goal or cursor. */
    fun practiceUnit(unit: ForgeUnitSummary, language: Language? = null) {
        speaker.stop()
        listeningState.value = false
        practicing = unit.range
        state.value = CurriculumSessionState.Loading
        viewModelScope.launch { show(service.practiceUnit(unit, language)) }
    }

    /**
     * Builds (or rebuilds) today's session. [language] picks which of the learner's languages to show (Premium
     * only; ignored otherwise) — null means the active target language.
     */
    fun start(language: Language? = null) {
        practicing = null
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
        val unit = practicing
        viewModelScope.launch {
            show(if (unit != null) service.practiceNext(current, unit, alreadyKnew) else service.next(current, alreadyKnew))
        }
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
