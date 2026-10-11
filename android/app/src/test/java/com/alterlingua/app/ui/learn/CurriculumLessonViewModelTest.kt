package com.alterlingua.app.ui.learn

import android.app.Activity
import com.alterlingua.app.billing.BillingManager
import com.alterlingua.app.billing.BillingPeriod
import com.alterlingua.app.billing.SubscriptionOffer
import com.alterlingua.app.billing.TierOffers
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.curriculum.CefrLevel
import com.alterlingua.app.learning.curriculum.CurriculumApi
import com.alterlingua.app.learning.curriculum.CurriculumLessonService
import com.alterlingua.app.learning.curriculum.CurriculumPageResult
import com.alterlingua.app.learning.curriculum.CurriculumProgressService
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.learning.curriculum.CurriculumUnitRange
import com.alterlingua.app.learning.curriculum.ForgeLevelState
import com.alterlingua.app.learning.curriculum.ForgeState
import com.alterlingua.app.learning.curriculum.ForgeUnitSummary
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeApi(private val words: List<String>) : CurriculumApi {
    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int) =
        CurriculumPageResult.Success(words.drop(offset).take(limit), words.size)
}

private class FakeMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
}

private class FakeSpeaker(private val canSpeakAnything: Boolean = true) : Speaker {
    val spoken = mutableListOf<Pair<String, Language>>()
    var stopped = false

    override suspend fun canSpeak(language: Language) = canSpeakAnything
    override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean {
        spoken += text to language
        return canSpeakAnything
    }
    override suspend fun synthesizeToFile(text: String, language: Language, file: File) = false
    override fun stop() { stopped = true }
    override fun shutdown() = Unit
}

private class FakeBillingManager(offers: List<TierOffers> = emptyList()) : BillingManager {
    override val offers = MutableStateFlow(offers)
    override val ready = MutableStateFlow(true)
    val purchased = mutableListOf<SubscriptionOffer>()
    override fun purchase(activity: Activity, offer: SubscriptionOffer) {
        purchased += offer
    }
}

class CurriculumLessonViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 2))
    private val map = LanguageMapService(InMemoryLanguageMapStore())

    private fun viewModel(
        words: List<String> = listOf("un", "deux", "trois"),
        speaker: Speaker = FakeSpeaker(),
        billingManager: BillingManager? = null,
    ) = CurriculumLessonViewModel(
        CurriculumLessonService(
            api = FakeApi(words),
            meanings = FakeMeanings(),
            progress = CurriculumProgressService(settings),
            entitlements = EntitlementService(settings),
            map = map,
            languages = { LessonLanguages(Languages.French, Languages.English) },
        ),
        speaker,
        settings,
        billingManager,
    )

    @Test
    fun start_loadsTodaysSession() = runTest {
        val vm = viewModel()
        assertEquals(CurriculumSessionState.Loading, vm.uiState.value)
        vm.start()
        val state = vm.uiState.value as CurriculumSessionState.InProgress
        assertEquals(listOf("un", "deux"), state.cards.map { it.term })
    }

    @Test
    fun next_advancesThroughTheSession_thenFinishes() = runTest {
        val vm = viewModel()
        vm.start()
        vm.next(alreadyKnew = true)
        assertTrue((vm.uiState.value as CurriculumSessionState.InProgress).position == 1)
        vm.next(alreadyKnew = false)
        assertTrue(vm.uiState.value is CurriculumSessionState.DoneForToday)
    }

    @Test
    fun next_beforeStart_doesNothing() = runTest {
        val vm = viewModel()
        vm.next(alreadyKnew = true)
        assertEquals(CurriculumSessionState.Loading, vm.uiState.value)
    }

    @Test
    fun canListen_reflectsWhetherThePhoneHasAVoice_forTheCardsLanguage() = runTest {
        val vm = viewModel(speaker = FakeSpeaker(canSpeakAnything = false))
        vm.start()
        assertFalse(vm.canListen.value)
    }

    @Test
    fun listen_speaksTheCurrentCardsTerm_inTheLanguageItIsShownIn() = runTest {
        val speaker = FakeSpeaker()
        val vm = viewModel(speaker = speaker)
        vm.start()
        vm.listen()
        assertEquals(listOf("un" to Languages.French), speaker.spoken)
        assertTrue(vm.listening.value)
    }

    @Test
    fun listen_again_stopsInsteadOfStartingANewUtterance() = runTest {
        val speaker = FakeSpeaker()
        val vm = viewModel(speaker = speaker)
        vm.start()
        vm.listen()
        vm.listen()
        assertFalse(vm.listening.value)
        assertTrue(speaker.stopped)
    }

    @Test
    fun tier_reflectsTheSavedSubscription() = runTest {
        settings.update { it.copy(tier = Tier.STANDARD) }
        val vm = viewModel()
        assertEquals(Tier.STANDARD, vm.tier.value)
    }

    @Test
    fun offers_reflectsWhatGooglePlaysCatalogHasRightNow() = runTest {
        val standardOffer = SubscriptionOffer("standard_monthly", Tier.STANDARD, BillingPeriod.MONTHLY, "$3.99", "token")
        val catalog = listOf(TierOffers(Tier.STANDARD, standardOffer, null), TierOffers(Tier.PREMIUM, null, null))
        val vm = viewModel(billingManager = FakeBillingManager(catalog))
        assertEquals(catalog, vm.offers.value)
    }

    @Test
    fun offers_isEmpty_whenThereIsNoBillingManager() = runTest {
        val vm = viewModel(billingManager = null)
        assertEquals(emptyList<TierOffers>(), vm.offers.value)
    }

    @Test
    fun subscribe_startsThatExactOffersPurchaseFlow() = runTest {
        val billing = FakeBillingManager()
        val vm = viewModel(billingManager = billing)
        val offer = SubscriptionOffer("premium_annual", Tier.PREMIUM, BillingPeriod.ANNUAL, "$79.99", "token")
        vm.subscribe(activity = object : Activity() {}, offer)
        assertEquals(listOf(offer), billing.purchased)
    }

    // ---- The Forge: Language -> Level -> Unit -> Lesson ----

    @Test
    fun loadForge_populatesTheLevelList() = runTest {
        val vm = viewModel()
        vm.loadForge()
        val state = vm.forgeLevels.value as ForgeState.Levels
        assertEquals(3, state.levels.size) // "un","deux","trois": 3 words banded into 3 one-word levels
    }

    @Test
    fun openLevel_populatesItsOwnUnits() = runTest {
        val vm = viewModel()
        vm.loadForge()
        val range = (vm.forgeLevels.value as ForgeState.Levels).levels.first().range
        vm.openLevel(range)
        val level = vm.forgeLevel.value as ForgeLevelState.Loaded
        assertEquals(listOf("un"), level.units.first().words)
    }

    @Test
    fun practiceUnit_thenNext_neverTouchesTodaysOwnCursor() = runTest {
        val vm = viewModel()
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 2), listOf("un", "deux"), masteredCount = 0)
        vm.practiceUnit(unit)
        val inProgress = vm.uiState.value as CurriculumSessionState.InProgress
        assertEquals(listOf("un", "deux"), inProgress.cards.map { it.term })
        vm.next(alreadyKnew = true)
        vm.next(alreadyKnew = true)
        assertTrue(vm.uiState.value is CurriculumSessionState.UnitPracticeComplete)
        assertEquals(0, settings.current.curriculumProgressFor("fr").cursor)
    }

    @Test
    fun start_afterPracticingAUnit_returnsToTodaysOwnSession_notPracticeMode() = runTest {
        val vm = viewModel()
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 1), listOf("un"), masteredCount = 0)
        vm.practiceUnit(unit)
        vm.next(alreadyKnew = true) // finishes the one-word unit -> UnitPracticeComplete, cursor still untouched
        vm.start()
        vm.next(alreadyKnew = true) // back in "today" mode: this one really does advance the cursor
        assertEquals(1, settings.current.curriculumProgressFor("fr").cursor)
    }
}
