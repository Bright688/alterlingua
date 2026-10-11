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
}
