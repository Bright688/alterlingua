package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.FREE_CURRICULUM_TRIAL_WORDS
import com.alterlingua.app.entitlements.PREMIUM_CURRICULUM_DAILY_CAP
import com.alterlingua.app.entitlements.STANDARD_CURRICULUM_DAILY_CAP
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapItem
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
    private val spanish = listOf("hola", "mañana")
    private val mapStore = InMemoryLanguageMapStore()
    private val map = LanguageMapService(mapStore, clock = { 1_000_000L })
    private var learning: Language = Languages.French

    private fun service(
        api: CurriculumApi = FakeCurriculumApi(mapOf("fr" to french, "es" to spanish)),
        settings: FakeUserSettingsRepository = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3)),
        learningLanguages: List<Language> = listOf(learning),
    ) = CurriculumLessonService(
        api = api,
        meanings = FakeMeanings(),
        progress = CurriculumProgressService(settings, clock = { 1_000_000L }),
        entitlements = EntitlementService(settings),
        map = map,
        languages = { LessonLanguages(learning, Languages.English) },
        learningLanguages = { learningLanguages },
        clock = { 1_000_000L },
    )

    @Test
    fun aFreshSession_bringsTodaysGoalAsCards_withMeanings() = runTest {
        val state = service().today() as CurriculumSessionState.InProgress
        assertEquals(listOf("bonjour", "demain", "réunion"), state.cards.map { it.term })
        assertEquals("meaning of bonjour", state.cards[0].meaning)
        assertEquals(3, state.goalToday)
        assertEquals(0, state.position)
        assertEquals(Languages.French, state.languages.current)
        assertEquals(Tier.FREE, state.tier)
    }

    @Test
    fun finishingEveryCard_recordsEachInThePersonalLanguageMap_andEndsTheSession() = runTest {
        val svc = service()
        var state = svc.today() as CurriculumSessionState.InProgress
        repeat(state.cards.size - 1) { state = svc.next(state, alreadyKnew = true) as CurriculumSessionState.InProgress }
        val done = svc.next(state, alreadyKnew = true)

        assertTrue(done is CurriculumSessionState.DoneForToday)
        listOf("bonjour", "demain", "réunion").forEach { word ->
            assertNotNull(map.item(UnitKey("fr", word, UnitType.WORD)))
        }
        assertNull(map.item(UnitKey("fr", "devis", UnitType.WORD))) // not reached yet: today's goal was 3
    }

    @Test
    fun theLearnersOwnAnswer_isRecordedAsARecognitionSignal_likeTheMicroLessonsRecallCheck() = runTest {
        val svc = service()
        val state = svc.today() as CurriculumSessionState.InProgress
        svc.next(state, alreadyKnew = true)
        val bonjour = map.item(UnitKey("fr", "bonjour", UnitType.WORD))!!
        assertEquals(1, bonjour.correctRecognitions)
        assertEquals(0, bonjour.incorrectRecognitions)
    }

    @Test
    fun sayingYouDidNotKnowIt_isRecordedHonestly_notAsIfItWereCorrect() = runTest {
        val svc = service()
        val state = svc.today() as CurriculumSessionState.InProgress
        svc.next(state, alreadyKnew = false)
        val bonjour = map.item(UnitKey("fr", "bonjour", UnitType.WORD))!!
        assertEquals(0, bonjour.correctRecognitions)
        assertEquals(1, bonjour.incorrectRecognitions)
    }

    @Test
    fun theNextSession_continuesWhereTheCursorLeftOff() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3))
        val api = FakeCurriculumApi(mapOf("fr" to french))
        val svc = service(api = api, settings = settings)
        var state = svc.today() as CurriculumSessionState.InProgress
        repeat(state.cards.size - 1) { state = svc.next(state, alreadyKnew = true) as CurriculumSessionState.InProgress }
        svc.next(state, alreadyKnew = true) // finishes today's 3

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
        val state = service(api = api, settings = settings).today() as CurriculumSessionState.DoneForToday
        assertEquals(Languages.French, state.languages.current)
        assertEquals(3, state.goalToday)
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
        val state = service(settings = settings).today() as CurriculumSessionState.DoneForToday
        assertEquals(3, state.goalToday)
    }

    // ---- tier-based daily cap (Standard/Free: normal cap; Premium: much higher) ----

    @Test
    fun aFreeOrStandardUser_isCappedAtTheNormalDailyLimit_evenIfTheyAskedForMore() = runTest {
        listOf(Tier.FREE, Tier.STANDARD).forEach { tier ->
            val settings = FakeUserSettingsRepository(UserSettings(tier = tier, curriculumDailyGoal = 500))
            val state = service(settings = settings).today() as CurriculumSessionState.InProgress
            assertEquals(tier.name, STANDARD_CURRICULUM_DAILY_CAP, state.goalToday)
        }
    }

    @Test
    fun aPremiumUser_isNotCappedAtTheNormalDailyLimit() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, curriculumDailyGoal = 500))
        val state = service(settings = settings).today() as CurriculumSessionState.InProgress
        assertEquals(500, state.goalToday)
        assertTrue(state.goalToday <= PREMIUM_CURRICULUM_DAILY_CAP)
    }

    // ---- language choice (Premium only) ----

    @Test
    fun aPremiumUser_mayPickAnotherLanguageTheyAreLearning() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, curriculumDailyGoal = 3))
        val state = service(settings = settings, learningLanguages = listOf(Languages.French, Languages.Spanish))
            .today(Languages.Spanish) as CurriculumSessionState.InProgress
        assertEquals(Languages.Spanish, state.languages.current)
        assertEquals(listOf("hola", "mañana"), state.cards.map { it.term })
        assertEquals(listOf(Languages.French, Languages.Spanish), state.languages.available)
    }

    @Test
    fun aStandardUser_cannotSwitchLanguage_evenIfAskedTo() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(tier = Tier.STANDARD, curriculumDailyGoal = 3))
        val state = service(settings = settings, learningLanguages = listOf(Languages.French, Languages.Spanish))
            .today(Languages.Spanish) as CurriculumSessionState.InProgress
        assertEquals("a non-Premium override request is ignored", Languages.French, state.languages.current)
        assertEquals("non-Premium never sees a picker, even if it learns several languages", listOf(Languages.French), state.languages.available)
    }

    @Test
    fun aPremiumUser_cannotPickALanguageTheyAreNotLearning() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, curriculumDailyGoal = 3))
        val state = service(settings = settings, learningLanguages = listOf(Languages.French))
            .today(Languages.Japanese) as CurriculumSessionState.InProgress
        assertEquals(Languages.French, state.languages.current)
    }

    // ---- demonstrated mastery skips ahead for free (CLAUDE.md's own "a learner who demonstrates mastery... should
    // be able to skip content they already know", explicitly for every tier, not only Premium) ----

    @Test
    fun aWordAlreadyMastered_isSkippedForFree_neverShownAsACard() = runTest {
        mapStore.update(UnitKey("fr", "demain", UnitType.WORD)) {
            LanguageMapItem("fr", "demain", UnitType.WORD, "demain", masteryState = MasteryStatus.MASTERED)
        }
        val state = service().today() as CurriculumSessionState.InProgress
        assertEquals(listOf("bonjour", "réunion", "devis"), state.cards.map { it.term })
    }

    @Test
    fun skippingAMasteredWord_doesNotCostTodaysGoal_onlyAdvancesTheCursor() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3))
        mapStore.update(UnitKey("fr", "bonjour", UnitType.WORD)) {
            LanguageMapItem("fr", "bonjour", UnitType.WORD, "bonjour", masteryState = MasteryStatus.MASTERED)
        }
        val state = service(settings = settings).today() as CurriculumSessionState.InProgress
        // Still 3 real cards shown (the full goal), even though one extra word had to be scanned past.
        assertEquals(listOf("demain", "réunion", "devis"), state.cards.map { it.term })
        assertEquals(3, state.goalToday)
    }

    @Test
    fun everyWordAlreadyMastered_isTreatedAsDoneForToday_notAnEmptyProgressCard() = runTest {
        french.forEach { word ->
            mapStore.update(UnitKey("fr", word, UnitType.WORD)) {
                LanguageMapItem("fr", word, UnitType.WORD, word, masteryState = MasteryStatus.MASTERED)
            }
        }
        val state = service().today()
        assertTrue(state is CurriculumSessionState.DoneForToday)
    }

    // ---- The Forge: Language -> Level -> Unit -> Lesson, the one shared curriculum for every plan ----

    @Test
    fun forge_bandsTheLanguagesWordListIntoLevels() = runTest {
        val state = service().forge() as ForgeState.Levels
        // french has 5 words: banded into 5 one-word levels (see CurriculumStructureTest's own fewer-than-levels case).
        assertEquals(listOf(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1), state.levels.map { it.range.level })
        assertEquals(Languages.French, state.languages.current)
        assertEquals(Tier.FREE, state.tier)
    }

    @Test
    fun forge_marksOnlyTheLevelContainingTheCursorAsCurrent() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3).withCurriculumProgress("fr", CurriculumProgress(cursor = 2, lastActiveDate = "1970-01-01")),
        )
        val state = service(settings = settings).forge() as ForgeState.Levels
        assertEquals(listOf(false, false, true, false, false), state.levels.map { it.isCurrent })
    }

    @Test
    fun forge_whenBackendFails_isUnavailable() = runTest {
        val api = FakeCurriculumApi(mapOf("fr" to french)).apply { unavailable = true }
        assertEquals(ForgeState.Unavailable, service(api = api).forge())
    }

    @Test
    fun forgeLevel_slicesTheLevelsWordsIntoFixedSizeUnits_withMasteredCounts() = runTest {
        val words = (1..45).map { "word$it" }
        val api = FakeCurriculumApi(mapOf("fr" to words))
        mapStore.update(UnitKey("fr", "word1", UnitType.WORD)) {
            LanguageMapItem("fr", "word1", UnitType.WORD, "word1", masteryState = MasteryStatus.MASTERED)
        }
        mapStore.update(UnitKey("fr", "word2", UnitType.WORD)) {
            LanguageMapItem("fr", "word2", UnitType.WORD, "word2", masteryState = MasteryStatus.MASTERED)
        }
        val range = CurriculumLevelRange(CefrLevel.A1, 0, 45)
        val state = service(api = api).forgeLevel(range) as ForgeLevelState.Loaded
        assertEquals(listOf(20, 20, 5), state.units.map { it.words.size })
        assertEquals(2, state.units[0].masteredCount) // word1 and word2 are both in the first unit
        assertEquals(0, state.units[1].masteredCount)
    }

    @Test
    fun forgeLevel_whenBackendFails_isUnavailable() = runTest {
        val api = FakeCurriculumApi(mapOf("fr" to french)).apply { unavailable = true }
        assertEquals(ForgeLevelState.Unavailable, service(api = api).forgeLevel(CurriculumLevelRange(CefrLevel.A1, 0, 5)))
    }

    @Test
    fun practiceUnit_opensASessionForThatUnitsOwnWords_withMeanings() = runTest {
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.A1, 1, 0, 2), listOf("bonjour", "demain"), masteredCount = 0)
        val state = service().practiceUnit(unit) as CurriculumSessionState.InProgress
        assertEquals(listOf("bonjour", "demain"), state.cards.map { it.term })
        assertEquals("meaning of bonjour", state.cards[0].meaning)
    }

    @Test
    fun practiceUnit_stillRespectsTheFreeTrialLimit() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings().withCurriculumProgress("fr", CurriculumProgress(cursor = FREE_CURRICULUM_TRIAL_WORDS, lastActiveDate = "1970-01-01")),
        )
        val unit = ForgeUnitSummary(CurriculumUnitRange(CefrLevel.C2, 1, 2980, 3000), listOf("rare"), masteredCount = 0)
        val state = service(settings = settings).practiceUnit(unit)
        assertEquals(CurriculumSessionState.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS), state)
    }

    @Test
    fun practiceNext_recordsTheSameLanguageMapSignal_butNeverTouchesTheCursorOrDailyGoal() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 3))
        val svc = service(settings = settings)
        val unitRange = CurriculumUnitRange(CefrLevel.A1, 1, 0, 2)
        val unit = ForgeUnitSummary(unitRange, listOf("bonjour", "demain"), masteredCount = 0)
        var state = svc.practiceUnit(unit) as CurriculumSessionState.InProgress
        state = svc.practiceNext(state, unitRange, alreadyKnew = true) as CurriculumSessionState.InProgress
        val done = svc.practiceNext(state, unitRange, alreadyKnew = false)

        assertTrue(done is CurriculumSessionState.UnitPracticeComplete)
        assertEquals(CefrLevel.A1, (done as CurriculumSessionState.UnitPracticeComplete).level)
        assertEquals(1, done.unitNumber)
        assertNotNull(map.item(UnitKey("fr", "bonjour", UnitType.WORD)))
        assertNotNull(map.item(UnitKey("fr", "demain", UnitType.WORD)))
        // Practicing a unit must never cost or grant anything against today's own goal/cursor.
        assertEquals(0, settings.current.curriculumProgressFor("fr").cursor)
        assertEquals(0, settings.current.curriculumProgressFor("fr").completedToday)
    }
}
