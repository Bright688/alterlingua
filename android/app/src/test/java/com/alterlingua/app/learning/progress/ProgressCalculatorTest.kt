package com.alterlingua.app.learning.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ProgressCalculatorTest {
    private val zone: ZoneId = ZoneOffset.UTC
    /** Sunday 20 September 2026: the last day of a week that began on Monday 14 September. */
    private val today = LocalDate.of(2026, 9, 20)
    private val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val noTotals = ProgressTotals(0, 0, 0, 0, 0)

    private fun day(date: LocalDate, met: Int = 0, assisted: Int = 0, new: Int = 0, help: Int = 0, cards: Int = 0, tries: Int = 0, good: Int = 0, snapshot: MasterySnapshot? = null) =
        DailyActivity(date.toString(), "fr", met, assisted, new, help, cards, tries, good, snapshot)

    private fun weeksAgo(n: Int, weekday: Int = 0) = today.minusDays(6).minusWeeks(n.toLong()).plusDays(weekday.toLong()) // Monday of the week n weeks ago

    private fun build(days: List<DailyActivity>, rules: ProgressRules = ProgressRules()) = ProgressCalculator.build("fr", days, noTotals, now, zone, rules)

    // ---- Translation Dependence: the formula ----

    @Test fun dependenceIsAssistedWordsOverAllWordsMet_asARoundedPercentage() {
        assertEquals(94, ProgressCalculator.dependence(100, 94))
        assertEquals(71, ProgressCalculator.dependence(100, 71))
        assertEquals(49, ProgressCalculator.dependence(100, 49))
        assertEquals(0, ProgressCalculator.dependence(40, 0))
        assertEquals(100, ProgressCalculator.dependence(40, 40))
        assertEquals(33, ProgressCalculator.dependence(30, 10))
        assertEquals(67, ProgressCalculator.dependence(30, 20))
    }

    @Test fun aWeekWithTooFewWords_hasNoPercentage_evenWhenTheRatioIsKnown() {
        assertNull(ProgressCalculator.dependence(19, 19))
        assertEquals(100, ProgressCalculator.dependence(20, 20))
        assertNull(ProgressCalculator.dependence(0, 0))
    }

    @Test fun theMinimumIsConfigurable() {
        assertEquals(50, ProgressCalculator.dependence(10, 5, ProgressRules(minWordsPerWeek = 10)))
    }

    @Test fun assistedIsNeverCountedAboveTheWordsMet() {
        assertEquals(100, ProgressCalculator.dependence(20, 500))
    }

    // ---- what the screen may claim ----

    @Test fun withNoHistory_thereIsNoDependenceAndNothingIsInvented() {
        val report = build(emptyList())
        assertEquals(DependenceStatus.NoData, report.dependence)
        assertFalse(report.masteryTrendReady)
        assertFalse(report.activityTrendReady)
        assertFalse(report.assistanceTrendReady)
        assertEquals(0, report.thisWeek.wordsMet)
        assertNull(report.thisWeek.dependencePercent)
        assertNull(report.thisWeek.snapshot)
    }

    @Test fun oneWeekOfEnoughWords_isCollecting_notATrend() {
        val report = build(listOf(day(weeksAgo(0), met = 30, assisted = 24)))
        val status = report.dependence as DependenceStatus.Collecting
        assertEquals(30, status.wordsThisWeek)
        assertEquals(1, status.measuredWeeks)
        assertEquals(2, status.weeksNeeded)
        assertEquals(80, report.thisWeek.dependencePercent)
    }

    @Test fun someWordsButTooFew_isCollecting_andShowsNoPercentage() {
        val report = build(listOf(day(weeksAgo(0), met = 12, assisted = 12)))
        val status = report.dependence as DependenceStatus.Collecting
        assertEquals(12, status.wordsThisWeek)
        assertEquals(0, status.measuredWeeks)
        assertNull(report.thisWeek.dependencePercent)
    }

    @Test fun twoWeeksOfEnoughWords_giveATrend() {
        val report = build(listOf(day(weeksAgo(1), met = 50, assisted = 47), day(weeksAgo(0), met = 50, assisted = 36)))
        val ready = report.dependence as DependenceStatus.Ready
        assertEquals(listOf(94, 72), ready.points.map { it.dependencePercent })
        assertEquals(94, ready.first)
        assertEquals(72, ready.current)
    }

    @Test fun theExampleFromTheBrief_week1_week4_week8() {
        val days = listOf(
            day(weeksAgo(7), met = 100, assisted = 94),
            day(weeksAgo(4), met = 100, assisted = 71),
            day(weeksAgo(0), met = 100, assisted = 49),
        )
        val ready = build(days).dependence as DependenceStatus.Ready
        assertEquals(listOf(1, 4, 8), ready.points.map { it.weekNumber })
        assertEquals(listOf(94, 71, 49), ready.points.map { it.dependencePercent })
    }

    @Test fun aWeekWithTooFewWordsInTheMiddle_isLeftOutNotEstimated() {
        val days = listOf(day(weeksAgo(2), met = 40, assisted = 36), day(weeksAgo(1), met = 5, assisted = 5), day(weeksAgo(0), met = 40, assisted = 20))
        val ready = build(days).dependence as DependenceStatus.Ready
        assertEquals(listOf(1, 3), ready.points.map { it.weekNumber })
    }

    @Test fun wordsInTheSameWeekAddUp_acrossDays() {
        val days = listOf(day(weeksAgo(0, 0), met = 10, assisted = 10), day(weeksAgo(0, 3), met = 10, assisted = 0), day(weeksAgo(1), met = 20, assisted = 20))
        val ready = build(days).dependence as DependenceStatus.Ready
        assertEquals(listOf(100, 50), ready.points.map { it.dependencePercent })
    }

    // ---- weeks ----

    @Test fun weeksRunMondayToSunday() {
        val sunday = today // 20 September
        val monday = LocalDate.of(2026, 9, 21) // next week
        val report = ProgressCalculator.build("fr", listOf(day(sunday, met = 25, assisted = 25)), noTotals, monday.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), zone)
        assertEquals("the Sunday is in last week, the new Monday starts a new one", 2, report.weeks.size)
        assertEquals(25, report.weeks.first().wordsMet)
        assertEquals(0, report.thisWeek.wordsMet)
        assertEquals(LocalDate.of(2026, 9, 21), report.thisWeek.start)
    }

    @Test fun weekNumbersStartAtTheFirstActiveWeek() {
        val report = build(listOf(day(weeksAgo(3), help = 1)))
        assertEquals(listOf(1, 2, 3, 4), report.weeks.map { it.weekNumber })
    }

    @Test fun onlyTheLatestEightWeeksAreShown_butNumbersKeepCounting() {
        val report = build((0..11).map { day(weeksAgo(it), met = 30, assisted = 15) })
        assertEquals(8, report.weeks.size)
        assertEquals((5..12).toList(), report.weeks.map { it.weekNumber })
    }

    @Test fun aRowWithOnlyASnapshot_isNotActivity() {
        val report = build(listOf(day(weeksAgo(0), snapshot = MasterySnapshot(5, 2, 1, 0))))
        assertEquals(DependenceStatus.NoData, report.dependence)
        assertTrue(report.weeks.all { it.activeWeekdays.isEmpty() })
    }

    @Test fun activeWeekdaysAreThoseWithActivity() {
        val report = build(listOf(day(weeksAgo(0, 0), met = 3), day(weeksAgo(0, 2), cards = 1), day(weeksAgo(0, 5), snapshot = MasterySnapshot(1, 0, 0, 0))))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), report.thisWeek.activeWeekdays)
    }

    // ---- the other trends ----

    @Test fun masteryTrendNeedsTwoWeeksOfSnapshots_andCarriesTheLastOneForward() {
        val one = build(listOf(day(weeksAgo(0), snapshot = MasterySnapshot(10, 5, 3, 1))))
        assertFalse(one.masteryTrendReady)
        val two = build(listOf(day(weeksAgo(2), met = 1, snapshot = MasterySnapshot(4, 3, 1, 0)), day(weeksAgo(0), snapshot = MasterySnapshot(10, 5, 3, 1))))
        assertTrue(two.masteryTrendReady)
        assertEquals("the quiet week keeps the last known state", MasterySnapshot(4, 3, 1, 0), two.weeks[1].snapshot)
        assertEquals(MasterySnapshot(10, 5, 3, 1), two.thisWeek.snapshot)
    }

    @Test fun aSnapshotFromTheLastDayOfTheWeek_winsOverAnEarlierOne() {
        val report = build(listOf(day(weeksAgo(0, 1), snapshot = MasterySnapshot(5, 5, 0, 0)), day(weeksAgo(0, 6), snapshot = MasterySnapshot(8, 4, 3, 1))))
        assertEquals(MasterySnapshot(8, 4, 3, 1), report.thisWeek.snapshot)
    }

    @Test fun assistanceIsAssistedWordsPlusHelpRequests_andNeedsTwoWeeks() {
        val report = build(listOf(day(weeksAgo(1), met = 30, assisted = 20, help = 3), day(weeksAgo(0), met = 30, assisted = 10, help = 1)))
        assertEquals(listOf(23, 11), report.weeks.map { it.assistance })
        assertTrue(report.assistanceTrendReady)
        assertFalse(build(listOf(day(weeksAgo(0), met = 30, assisted = 10))).assistanceTrendReady)
    }

    @Test fun activityCountsLessonCardsPracticeAndHelp() {
        val report = build(listOf(day(weeksAgo(1), cards = 3, tries = 4), day(weeksAgo(0), cards = 3, help = 2, met = 5)))
        assertEquals(7, report.weeks[0].learningActions)
        assertEquals(5, report.weeks[1].learningActions)
        assertTrue(report.activityTrendReady)
    }

    @Test fun deterministic() {
        val days = listOf(day(weeksAgo(1), met = 30, assisted = 20), day(weeksAgo(0), met = 30, assisted = 10))
        assertEquals(build(days), build(days))
    }

    @Test fun theDataHoldsNoText_onlyNumbersAndDates() {
        val types = DailyActivity::class.java.declaredFields.map { it.type.simpleName }.toSet()
        assertTrue("only counts, text for date and language, and a snapshot of counts: $types", types.all { it in setOf("int", "String", "MasterySnapshot") })
        assertEquals(setOf("int"), MasterySnapshot::class.java.declaredFields.map { it.type.simpleName }.toSet())
    }
}
