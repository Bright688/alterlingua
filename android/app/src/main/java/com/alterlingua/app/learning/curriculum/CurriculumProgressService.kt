package com.alterlingua.app.learning.curriculum

import com.alterlingua.app.storage.UserSettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * Carries a language's curriculum progress into today. If a day has passed since [CurriculumProgress.lastActiveDate],
 * whatever was left of that day's goal becomes backlog added to today's goal, and [CurriculumProgress.completedToday]
 * starts again at zero. A day with no activity at all still owes its full goal, so nothing set out to be learned is
 * silently dropped: the learner sees it folded into a later day's total instead.
 */
class CurriculumProgressService(
    private val settings: UserSettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    /** How many words a day the learner currently wants from the standard curriculum. */
    suspend fun dailyGoal(): Int = settings.settings.first().curriculumDailyGoal

    /** [languageCode]'s progress, rolled over to today when it was last touched on an earlier day. Saves any roll-over. */
    suspend fun progressToday(languageCode: String): CurriculumProgress {
        val before = settings.settings.first()
        val rolled = rollOver(before.curriculumProgressFor(languageCode), before.curriculumDailyGoal, today())
        if (rolled != before.curriculumProgressFor(languageCode)) {
            settings.update { it.withCurriculumProgress(languageCode, rolled) }
        }
        return rolled
    }

    /** One more word of [languageCode]'s curriculum learned: advances the cursor and today's completed count. */
    suspend fun recordCompleted(languageCode: String) {
        settings.update {
            val rolled = rollOver(it.curriculumProgressFor(languageCode), it.curriculumDailyGoal, today())
            it.withCurriculumProgress(languageCode, rolled.copy(cursor = rolled.cursor + 1, completedToday = rolled.completedToday + 1))
        }
    }

    /**
     * [count] words already mastered (Personal Language Map) were found ahead of the cursor and skipped for free:
     * advances the cursor past them without counting them against today's goal (CLAUDE.md's own "a learner who
     * demonstrates mastery through real-world communication should be able to skip content they already know" --
     * for every tier, not only Premium).
     */
    suspend fun recordSkipped(languageCode: String, count: Int) {
        if (count <= 0) return
        settings.update {
            val rolled = rollOver(it.curriculumProgressFor(languageCode), it.curriculumDailyGoal, today())
            it.withCurriculumProgress(languageCode, rolled.copy(cursor = rolled.cursor + count))
        }
    }

    internal fun rollOver(progress: CurriculumProgress, dailyGoal: Int, today: LocalDate): CurriculumProgress {
        val lastDate = progress.lastActiveDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (lastDate == null) return progress.copy(lastActiveDate = today.toString())
        val daysElapsed = ChronoUnit.DAYS.between(lastDate, today)
        if (daysElapsed <= 0) return progress
        val shortfallYesterday = (progress.goalToday(dailyGoal) - progress.completedToday).coerceAtLeast(0)
        val fullyMissedDays = (daysElapsed - 1).coerceAtLeast(0)
        val backlog = shortfallYesterday + dailyGoal * fullyMissedDays.toInt()
        return progress.copy(completedToday = 0, backlog = backlog, lastActiveDate = today.toString())
    }
}
