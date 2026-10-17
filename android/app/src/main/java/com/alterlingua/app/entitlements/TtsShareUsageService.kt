package com.alterlingua.app.entitlements

import com.alterlingua.app.storage.UserSettingsRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Tracks and enforces each tier's monthly cap on giving a just-translated keyboard message a voice and sharing it
 * (see [EntitlementService.ttsShareLimitPerMonth]: Free 10, Standard 50, Premium 1,000). Mirrors the day-rollover
 * shape of [com.alterlingua.app.learning.curriculum.CurriculumProgressService], but by calendar month and with a
 * single global counter rather than one per language: there is one phone, in one time zone, and only one allowance
 * to track here, so a resettable month-stamped count (like [com.alterlingua.app.learning.curriculum.CurriculumProgress.lastActiveDate])
 * is simpler than a rolling window.
 */
class TtsShareUsageService(
    private val settings: UserSettingsRepository,
    private val entitlements: EntitlementService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private fun currentMonth(): String = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone)).toString()

    /** Whether a translated message may be given a voice and shared right now. */
    suspend fun access(tier: Tier): TtsShareAccess {
        val limit = entitlements.ttsShareLimitPerMonth(tier)
        return if (usedThisMonth() < limit) TtsShareAccess.Allowed else TtsShareAccess.LimitReached(limit)
    }

    /** How many of this month's allowance remain for [tier]. */
    suspend fun remaining(tier: Tier): Int = (entitlements.ttsShareLimitPerMonth(tier) - usedThisMonth()).coerceAtLeast(0)

    private suspend fun usedThisMonth(): Int {
        val current = settings.settings.first()
        return if (current.ttsShareMonth == currentMonth()) current.ttsShareUsedThisMonth else 0
    }

    /** Records one use, rolling the counter over first if the calendar month has changed since the last one. */
    suspend fun recordUse() {
        settings.update {
            val month = currentMonth()
            val used = if (it.ttsShareMonth == month) it.ttsShareUsedThisMonth else 0
            it.copy(ttsShareMonth = month, ttsShareUsedThisMonth = used + 1)
        }
    }
}
