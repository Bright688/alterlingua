package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The standard curriculum's daily goal and roll-over, kept separately per language. */
class CurriculumProgressServiceTest {

    private fun serviceOn(date: String, settings: FakeUserSettingsRepository = FakeUserSettingsRepository()) =
        CurriculumProgressService(settings, clock = { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }, zone = ZoneOffset.UTC)

    @Test
    fun firstEverUse_startsAtZero_withTodayAsTheActiveDate() = runTest {
        val progress = serviceOn("2026-10-11").progressToday("fr")
        assertEquals(CurriculumProgress(lastActiveDate = "2026-10-11"), progress)
    }

    @Test
    fun theSameDay_isReturnedUnchanged() = runTest {
        val settings = FakeUserSettingsRepository()
        val service = serviceOn("2026-10-11", settings)
        service.progressToday("fr")
        service.recordCompleted("fr")
        val again = service.progressToday("fr")
        assertEquals(1, again.completedToday)
        assertEquals(1, again.cursor)
    }

    @Test
    fun goalMetExactly_rollsIntoTheNextDayWithNoBacklog() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 9, completedToday = 3, backlog = 0, lastActiveDate = "2026-10-10")),
        )
        val tomorrow = serviceOn("2026-10-11", settings).progressToday("fr")
        assertEquals(0, tomorrow.backlog)
        assertEquals(0, tomorrow.completedToday)
        assertEquals(9, tomorrow.cursor)
        assertEquals("2026-10-11", tomorrow.lastActiveDate)
    }

    @Test
    fun aShortfall_becomesTomorrowsBacklog() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 7, completedToday = 1, backlog = 0, lastActiveDate = "2026-10-10")),
        )
        val tomorrow = serviceOn("2026-10-11", settings).progressToday("fr")
        assertEquals(2, tomorrow.backlog) // 3 due - 1 done
        assertEquals(5, tomorrow.goalToday(3)) // 3 (today's own) + 2 (owed)
    }

    @Test
    fun daysWithNoActivityAtAll_eachOweTheirFullGoal() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 0, completedToday = 0, backlog = 0, lastActiveDate = "2026-10-08")),
        )
        // 2026-10-09 and 2026-10-10 were skipped entirely; today is 2026-10-11.
        val today = serviceOn("2026-10-11", settings).progressToday("fr")
        assertEquals(9, today.backlog) // 3 missed days x 3/day
    }

    @Test
    fun recordCompleted_advancesTheCursorAndTodaysCount_rollingOverFirstIfNeeded() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 20, completedToday = 3, backlog = 0, lastActiveDate = "2026-10-10")),
        )
        val service = serviceOn("2026-10-11", settings)
        service.recordCompleted("fr")
        val progress = settings.current.curriculumProgressFor("fr")
        assertEquals(21, progress.cursor)
        assertEquals(1, progress.completedToday)
        assertEquals(0, progress.backlog)
        assertEquals("2026-10-11", progress.lastActiveDate)
    }

    @Test
    fun recordSkipped_advancesTheCursor_withoutTouchingTodaysCompletedCount() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 20, completedToday = 1, backlog = 0, lastActiveDate = "2026-10-11")),
        )
        serviceOn("2026-10-11", settings).recordSkipped("fr", 4)
        val progress = settings.current.curriculumProgressFor("fr")
        assertEquals(24, progress.cursor)
        assertEquals(1, progress.completedToday) // unchanged: a skip is free, not a completion
    }

    @Test
    fun recordSkipped_withZeroOrNegative_doesNothing() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings().withCurriculumProgress("fr", CurriculumProgress(cursor = 5, lastActiveDate = "2026-10-11")),
        )
        serviceOn("2026-10-11", settings).recordSkipped("fr", 0)
        assertEquals(5, settings.current.curriculumProgressFor("fr").cursor)
    }

    @Test
    fun eachLanguage_rollsOverIndependently() = runTest {
        val settings = FakeUserSettingsRepository(
            UserSettings(curriculumDailyGoal = 3)
                .withCurriculumProgress("fr", CurriculumProgress(cursor = 9, completedToday = 0, backlog = 0, lastActiveDate = "2026-10-10"))
                .withCurriculumProgress("es", CurriculumProgress(cursor = 4, completedToday = 3, backlog = 0, lastActiveDate = "2026-10-10")),
        )
        val service = serviceOn("2026-10-11", settings)
        val french = service.progressToday("fr")
        val spanish = service.progressToday("es")
        assertEquals(3, french.backlog)
        assertEquals(0, spanish.backlog)
    }
}
