package com.alterlingua.app.entitlements

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val JANUARY_15_2026 = 1768435200000L // 2026-01-15T00:00:00Z
private const val FEBRUARY_1_2026 = 1769904000000L // 2026-02-01T00:00:00Z

class TtsShareUsageServiceTest {
    private val utc = ZoneId.of("UTC")

    private fun service(settings: FakeUserSettingsRepository, now: Long) =
        TtsShareUsageService(settings, EntitlementService(settings), clock = { now }, zone = utc)

    @Test
    fun aFreeUser_underTheLimit_isAllowed() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH - 1))
        assertEquals(TtsShareAccess.Allowed, service(settings, JANUARY_15_2026).access(Tier.FREE))
    }

    @Test
    fun aFreeUser_atTheLimit_isStopped() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH))
        assertEquals(TtsShareAccess.LimitReached(FREE_TTS_SHARES_PER_MONTH), service(settings, JANUARY_15_2026).access(Tier.FREE))
    }

    @Test
    fun standardHasItsOwnHigherLimit() = runTest {
        val underLimit = FakeUserSettingsRepository(UserSettings(tier = Tier.STANDARD, ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH + 5))
        assertEquals(TtsShareAccess.Allowed, service(underLimit, JANUARY_15_2026).access(Tier.STANDARD))

        val atLimit = FakeUserSettingsRepository(UserSettings(tier = Tier.STANDARD, ttsShareMonth = "2026-01", ttsShareUsedThisMonth = STANDARD_TTS_SHARES_PER_MONTH))
        assertEquals(TtsShareAccess.LimitReached(STANDARD_TTS_SHARES_PER_MONTH), service(atLimit, JANUARY_15_2026).access(Tier.STANDARD))
    }

    @Test
    fun premiumHasItsOwnMuchHigherLimit_notUnlimited() = runTest {
        val underLimit = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, ttsShareMonth = "2026-01", ttsShareUsedThisMonth = STANDARD_TTS_SHARES_PER_MONTH + 10))
        assertEquals(TtsShareAccess.Allowed, service(underLimit, JANUARY_15_2026).access(Tier.PREMIUM))

        val atLimit = FakeUserSettingsRepository(UserSettings(tier = Tier.PREMIUM, ttsShareMonth = "2026-01", ttsShareUsedThisMonth = PREMIUM_TTS_SHARES_PER_MONTH))
        assertEquals(TtsShareAccess.LimitReached(PREMIUM_TTS_SHARES_PER_MONTH), service(atLimit, JANUARY_15_2026).access(Tier.PREMIUM))
        assertEquals(0, service(atLimit, JANUARY_15_2026).remaining(Tier.PREMIUM))
    }

    @Test
    fun aNewCalendarMonth_resetsTheCount() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH))
        assertEquals(TtsShareAccess.Allowed, service(settings, FEBRUARY_1_2026).access(Tier.FREE))
        assertEquals(FREE_TTS_SHARES_PER_MONTH, service(settings, FEBRUARY_1_2026).remaining(Tier.FREE))
    }

    @Test
    fun recordUse_incrementsAndStampsTheMonth() = runTest {
        val settings = FakeUserSettingsRepository()
        val svc = service(settings, JANUARY_15_2026)
        svc.recordUse()
        assertEquals("2026-01", settings.current.ttsShareMonth)
        assertEquals(1, settings.current.ttsShareUsedThisMonth)
        svc.recordUse()
        assertEquals(2, settings.current.ttsShareUsedThisMonth)
    }

    @Test
    fun recordUse_inANewMonth_startsTheCountOverRatherThanAdding() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH))
        service(settings, FEBRUARY_1_2026).recordUse()
        assertEquals("2026-02", settings.current.ttsShareMonth)
        assertEquals(1, settings.current.ttsShareUsedThisMonth)
    }

    @Test
    fun remaining_countsDownFromTheLimit() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = 2))
        assertEquals(FREE_TTS_SHARES_PER_MONTH - 2, service(settings, JANUARY_15_2026).remaining(Tier.FREE))
    }

    @Test
    fun remaining_neverGoesBelowZero() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(ttsShareMonth = "2026-01", ttsShareUsedThisMonth = FREE_TTS_SHARES_PER_MONTH + 3))
        assertEquals(0, service(settings, JANUARY_15_2026).remaining(Tier.FREE))
    }
}
