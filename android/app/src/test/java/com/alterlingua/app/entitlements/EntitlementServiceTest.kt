package com.alterlingua.app.entitlements

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.curriculum.CurriculumProgress
import com.alterlingua.app.testing.FakeUserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementServiceTest {

    @Test
    fun aFreeUser_belowTheTrialLimit_isAllowed() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings().withCurriculumProgress("fr", CurriculumProgress(cursor = 10)))
        val service = EntitlementService(settings)
        assertEquals(CurriculumAccess.Allowed, service.curriculumAccess("fr"))
    }

    @Test
    fun aFreeUser_atTheTrialLimit_isStopped() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings().withCurriculumProgress("fr", CurriculumProgress(cursor = FREE_CURRICULUM_TRIAL_WORDS)),
        )
        val service = EntitlementService(settings)
        assertEquals(CurriculumAccess.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS), service.curriculumAccess("fr"))
    }

    @Test
    fun aStandardOrPremiumUser_isNeverStopped_evenPastTheTrialCount() = runTest {
        listOf(Tier.STANDARD, Tier.PREMIUM).forEach { tier ->
            val settings = FakeUserSettingsRepository(
                UserSettings(tier = tier).withCurriculumProgress("fr", CurriculumProgress(cursor = 10_000)),
            )
            assertEquals(tier.name, CurriculumAccess.Allowed, EntitlementService(settings).curriculumAccess("fr"))
        }
    }

    @Test
    fun theTrialLimit_isPerLanguage_notSharedAcrossLanguages() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings()
                .withCurriculumProgress("fr", CurriculumProgress(cursor = FREE_CURRICULUM_TRIAL_WORDS))
                .withCurriculumProgress("es", CurriculumProgress(cursor = 0)),
        )
        val service = EntitlementService(settings)
        assertEquals(CurriculumAccess.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS), service.curriculumAccess("fr"))
        assertEquals(CurriculumAccess.Allowed, service.curriculumAccess("es"))
    }

    @Test
    fun tier_defaultsToFree() = runTest {
        assertEquals(Tier.FREE, EntitlementService(FakeUserSettingsRepository()).tier())
    }

    @Test
    fun maxCurriculumDailyGoal_isTheNormalCapForFreeAndStandard_muchHigherForPremium() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(STANDARD_CURRICULUM_DAILY_CAP, service.maxCurriculumDailyGoal(Tier.FREE))
        assertEquals(STANDARD_CURRICULUM_DAILY_CAP, service.maxCurriculumDailyGoal(Tier.STANDARD))
        assertEquals(PREMIUM_CURRICULUM_DAILY_CAP, service.maxCurriculumDailyGoal(Tier.PREMIUM))
    }

    @Test
    fun onlyPremium_mayPickAnyLearningLanguageForTheCurriculum() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(false, service.canPickAnyLearningLanguage(Tier.FREE))
        assertEquals(false, service.canPickAnyLearningLanguage(Tier.STANDARD))
        assertEquals(true, service.canPickAnyLearningLanguage(Tier.PREMIUM))
    }

    // ---- the owner's tier comparison table, as policy (CLAUDE.md pasted content): none of these are backed by a
    // real AI Learning Engine yet -- this is the policy it will read from once one is built. ----

    @Test
    fun aiConversationMinutesPerDay_matchesTheTierTable() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(5, service.aiConversationMinutesPerDay(Tier.FREE))
        assertEquals(15, service.aiConversationMinutesPerDay(Tier.STANDARD))
        assertEquals(60, service.aiConversationMinutesPerDay(Tier.PREMIUM))
    }

    @Test
    fun speakingExerciseLevel_matchesTheTierTable() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(AssistanceLevel.LIMITED, service.speakingExerciseLevel(Tier.FREE))
        assertEquals(AssistanceLevel.BASIC, service.speakingExerciseLevel(Tier.STANDARD))
        assertEquals(AssistanceLevel.ADVANCED, service.speakingExerciseLevel(Tier.PREMIUM))
    }

    @Test
    fun pronunciationFeedbackLevel_isNoneForFree_unlikeOtherDimensions() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(AssistanceLevel.NONE, service.pronunciationFeedbackLevel(Tier.FREE))
        assertEquals(AssistanceLevel.BASIC, service.pronunciationFeedbackLevel(Tier.STANDARD))
        assertEquals(AssistanceLevel.ADVANCED, service.pronunciationFeedbackLevel(Tier.PREMIUM))
    }

    @Test
    fun aiExerciseLevel_andAiExplanationLevel_areLimitedForFreeAndStandard_extendedOnlyForPremium() {
        val service = EntitlementService(FakeUserSettingsRepository())
        listOf(Tier.FREE, Tier.STANDARD).forEach { tier ->
            assertEquals(tier.name, AssistanceLevel.LIMITED, service.aiExerciseLevel(tier))
            assertEquals(tier.name, AssistanceLevel.LIMITED, service.aiExplanationLevel(tier))
        }
        assertEquals(AssistanceLevel.EXTENDED, service.aiExerciseLevel(Tier.PREMIUM))
        assertEquals(AssistanceLevel.EXTENDED, service.aiExplanationLevel(Tier.PREMIUM))
    }

    @Test
    fun lessonRecommendationLevel_andLearningAnalyticsLevel_gradeAcrossAllThreeTiers() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(AssistanceLevel.BASIC, service.lessonRecommendationLevel(Tier.FREE))
        assertEquals(AssistanceLevel.STANDARD, service.lessonRecommendationLevel(Tier.STANDARD))
        assertEquals(AssistanceLevel.ADVANCED, service.lessonRecommendationLevel(Tier.PREMIUM))
        assertEquals(AssistanceLevel.BASIC, service.learningAnalyticsLevel(Tier.FREE))
        assertEquals(AssistanceLevel.STANDARD, service.learningAnalyticsLevel(Tier.STANDARD))
        assertEquals(AssistanceLevel.ADVANCED, service.learningAnalyticsLevel(Tier.PREMIUM))
    }

    @Test
    fun adaptiveDifficultyLevel_isNoneForFree_basicForStandard_advancedForPremium() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(AssistanceLevel.NONE, service.adaptiveDifficultyLevel(Tier.FREE))
        assertEquals(AssistanceLevel.BASIC, service.adaptiveDifficultyLevel(Tier.STANDARD))
        assertEquals(AssistanceLevel.ADVANCED, service.adaptiveDifficultyLevel(Tier.PREMIUM))
    }

    @Test
    fun isAdFree_onlyForPremium() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(false, service.isAdFree(Tier.FREE))
        assertEquals(false, service.isAdFree(Tier.STANDARD))
        assertEquals(true, service.isAdFree(Tier.PREMIUM))
    }

    @Test
    fun ttsShareLimitPerMonth_matchesTheOwnersThreeTierNumbers() {
        val service = EntitlementService(FakeUserSettingsRepository())
        assertEquals(FREE_TTS_SHARES_PER_MONTH, service.ttsShareLimitPerMonth(Tier.FREE))
        assertEquals(STANDARD_TTS_SHARES_PER_MONTH, service.ttsShareLimitPerMonth(Tier.STANDARD))
        assertEquals(null, service.ttsShareLimitPerMonth(Tier.PREMIUM))
    }
}
