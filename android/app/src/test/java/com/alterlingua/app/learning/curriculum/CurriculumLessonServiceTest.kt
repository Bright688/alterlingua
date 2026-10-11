package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.FREE_CURRICULUM_TRIAL_WORDS
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCurriculumApi(private val byLanguage: Map<String, List<String>>) : CurriculumApi {
    var unavailable = false
    val requestedOffsets = mutableListOf<Int>()

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): CurriculumPageResult {
        requestedOffsets += offset
        if (unavailable) return CurriculumPageResult.Unavailable
        val all = byLanguage[languageCode].orEmpty()
        return CurriculumPageResult.Success(all.drop(offset).take(limit), all.size)
    }
}

private class FakeMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = "meaning of $unit"
}

class CurriculumLessonServiceTest {

    private val french = listOf("bonjour", "demain", "réunion", "devis", "acompte")
    private val mapStore = InMemoryLanguageMapStore()
    private val map = LanguageMapService(mapStore, clock = { 1_000_000L })
    private var learning: Language = Languages.French

    private fun service(
        api: CurriculumApi = FakeCurriculumApi(mapOf("fr" to french)),
        settings: FakeUserSettingsRepository = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3)),
    ) = CurriculumLessonService(
        api = api,
        meanings = FakeMeanings(),
        progress = CurriculumProgressService(settings, clock = { 1_000_000L }),
        entitlements = EntitlementService(settings),
        map = map,
        languages = { LessonLanguages(learning, Languages.English) },
        clock = { 1_000_000L },
    )

    @Test
    fun aFreshSession_bringsTodaysGoalAsCards_withMeanings() = runTest {
        val state = service().today() as CurriculumSessionState.InProgress
        assertEquals(listOf("bonjour", "demain", "réunion"), state.cards.map { it.term })
        assertEquals("meaning of bonjour", state.cards[0].meaning)
        assertEquals(3, state.goalToday)
        assertEquals(0, state.position)
    }

    @Test
    fun finishingEveryCard_recordsEachInThePersonalLanguageMap_andEndsTheSession() = runTest {
        val svc = service()
        var state = svc.today() as CurriculumSessionState.InProgress
        repeat(state.cards.size - 1) { state = svc.next(state) as CurriculumSessionState.InProgress }
        val done = svc.next(state)

        assertTrue(done is CurriculumSessionState.DoneForToday)
        listOf("bonjour", "demain", "réunion").forEach { word ->
            assertNotNull(map.item(UnitKey("fr", word, UnitType.WORD)))
        }
        assertNull(map.item(UnitKey("fr", "devis", UnitType.WORD))) // not reached yet: today's goal was 3
    }

    @Test
    fun theNextSession_continuesWhereTheCursorLeftOff() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3))
        val api = FakeCurriculumApi(mapOf("fr" to french))
        val svc = service(api = api, settings = settings)
        var state = svc.today() as CurriculumSessionState.InProgress
        repeat(state.cards.size - 1) { state = svc.next(state) as CurriculumSessionState.InProgress }
        svc.next(state) // finishes today's 3

        assertEquals(3, settings.current.curriculumProgressFor("fr").cursor)
        assertEquals(listOf(0), api.requestedOffsets) // only the first page was ever fetched today
    }

    @Test
    fun oncTodaysGoalIsAlreadyMet_noNewPageIsFetched() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 3, completedToday = 3, lastActiveDate = "1970-01-01")),
        )
        val api = FakeCurriculumApi(mapOf("fr" to french))
        val state = service(api = api, settings = settings).today()
        assertEquals(CurriculumSessionState.DoneForToday(Languages.French, 3), state)
        assertTrue(api.requestedOffsets.isEmpty())
    }

    @Test
    fun aFreeUser_pastTheTrialLimit_seesThePaywallState_notAnyWords() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = FREE_CURRICULUM_TRIAL_WORDS, lastActiveDate = "1970-01-01")),
        )
        val state = service(settings = settings).today()
        assertEquals(CurriculumSessionState.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS), state)
    }

    @Test
    fun aBackendFailure_isReportedAsUnavailable_neverAsNoWords() = runTest {
        val api = FakeCurriculumApi(mapOf("fr" to french)).apply { unavailable = true }
        assertEquals(CurriculumSessionState.Unavailable, service(api = api).today())
    }

    @Test
    fun reachingTheEndOfTheWholeList_isTreatedAsDoneForToday() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = french.size, lastActiveDate = "1970-01-01")),
        )
        val state = service(settings = settings).today()
        assertEquals(CurriculumSessionState.DoneForToday(Languages.French, 3), state)
    }
}
