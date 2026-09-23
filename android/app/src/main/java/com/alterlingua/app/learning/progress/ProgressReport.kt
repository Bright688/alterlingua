package com.alterlingua.app.learning.progress

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The numbers that decide what the Progress screen may claim. All in one place.
 *
 * @property minWordsPerWeek a week's Translation Dependence is only shown when at least this many words were met that
 *   week; a percentage of 3 words means nothing.
 * @property minWeeksForTrend the trend needs this many such weeks.
 * @property weeksShown how many of the latest weeks the charts cover.
 */
data class ProgressRules(
    val minWordsPerWeek: Int = 20,
    val minWeeksForTrend: Int = 2,
    val weeksShown: Int = 8,
)

/** One calendar week (Monday to Sunday, in the phone's time zone) of one language. */
data class WeekStats(
    /** 1 for the week of the first recorded activity, then 2, 3 and so on. */
    val weekNumber: Int,
    val start: LocalDate,
    val wordsMet: Int,
    val wordsAssisted: Int,
    val newWords: Int,
    val helpRequests: Int,
    val lessonCards: Int,
    val practiceTries: Int,
    val practiceGood: Int,
    val activeWeekdays: Set<DayOfWeek>,
    /** The map's state counts at the last change up to the end of this week; null before anything was recorded. */
    val snapshot: MasterySnapshot?,
    /** Translation Dependence for the week in percent, or null when fewer than the minimum words were met. */
    val dependencePercent: Int?,
    // Pilot metrics (numbers only), see docs/pilot.md.
    val translationsOutgoingText: Int = 0,
    val translationsOutgoingVoice: Int = 0,
    val translationsIncomingText: Int = 0,
    val translationsIncomingVoice: Int = 0,
    val toLearning: Int = 0,
    val toFamiliar: Int = 0,
    val toMastered: Int = 0,
    val lessonsCompleted: Int = 0,
    val actionsFullSupport: Int = 0,
    val actionsAdaptive: Int = 0,
    val actionsOnDemand: Int = 0,
) {
    val translations: Int get() = translationsOutgoingText + translationsOutgoingVoice + translationsIncomingText + translationsIncomingVoice

    /** Times translation covered something this week: words that were not mastered, plus explicit help requests. */
    val assistance: Int get() = wordsAssisted + helpRequests

    /** Things the learner did to learn this week. */
    val learningActions: Int get() = lessonCards + practiceTries + helpRequests
}

/** What the Translation Dependence card can honestly show. */
sealed interface DependenceStatus {
    /** Nothing has been met yet. */
    data object NoData : DependenceStatus

    /** Some words, but not enough weeks (or words per week) for a trend. */
    data class Collecting(val wordsThisWeek: Int, val wordsNeeded: Int, val measuredWeeks: Int, val weeksNeeded: Int) : DependenceStatus

    /** [points] are the weeks with enough words, oldest first. */
    data class Ready(val points: List<WeekStats>) : DependenceStatus {
        val current: Int get() = points.last().dependencePercent!!
        val first: Int get() = points.first().dependencePercent!!
    }
}

/** How many of the learner's words and phrases are in each state right now. */
data class ProgressTotals(val encountered: Int, val newCount: Int, val learning: Int, val familiar: Int, val mastered: Int)

data class ProgressReport(
    val language: String,
    val totals: ProgressTotals,
    val dependence: DependenceStatus,
    /** The latest weeks, oldest first, including empty ones in between. */
    val weeks: List<WeekStats>,
    val thisWeek: WeekStats,
    /** Each chart needs at least two weeks with something to draw; until then it shows an empty state. */
    val masteryTrendReady: Boolean,
    val activityTrendReady: Boolean,
    val assistanceTrendReady: Boolean,
)

/**
 * Turns daily counts into weekly figures and decides what may be shown. Pure and deterministic.
 *
 * TRANSLATION DEPENDENCE of a week = words that were not mastered when met, divided by all words met, times 100.
 * See docs/progress.md, "Milestone 21 detail", for the full definition.
 */
object ProgressCalculator {

    fun build(
        language: String,
        days: List<DailyActivity>,
        totals: ProgressTotals,
        nowMillis: Long,
        zone: ZoneId,
        rules: ProgressRules = ProgressRules(),
    ): ProgressReport {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val thisWeekStart = weekStart(today)
        val active = days.filter { it.hasActivity }
        val firstWeekStart = active.minOfOrNull { weekStart(LocalDate.parse(it.date)) } ?: thisWeekStart
        val shownFrom = maxOf(firstWeekStart, thisWeekStart.minusWeeks((rules.weeksShown - 1).toLong()))

        val byDate = days.associateBy { LocalDate.parse(it.date) }
        val weeks = generateSequence(shownFrom) { it.plusWeeks(1) }.takeWhile { !it.isAfter(thisWeekStart) }.map { start ->
            val end = start.plusDays(6)
            val inWeek = (0..6).mapNotNull { byDate[start.plusDays(it.toLong())] }
            val met = inWeek.sumOf { it.wordsMet }
            val assisted = inWeek.sumOf { it.wordsAssisted }
            WeekStats(
                weekNumber = (ChronoUnit.WEEKS.between(firstWeekStart, start) + 1).toInt(),
                start = start,
                wordsMet = met,
                wordsAssisted = assisted,
                newWords = inWeek.sumOf { it.newWords },
                helpRequests = inWeek.sumOf { it.helpRequests },
                lessonCards = inWeek.sumOf { it.lessonCards },
                practiceTries = inWeek.sumOf { it.practiceTries },
                practiceGood = inWeek.sumOf { it.practiceGood },
                activeWeekdays = inWeek.filter { it.hasActivity }.map { LocalDate.parse(it.date).dayOfWeek }.toSet(),
                snapshot = days.filter { it.snapshot != null && !LocalDate.parse(it.date).isAfter(end) }.maxByOrNull { it.date }?.snapshot,
                dependencePercent = dependence(met, assisted, rules),
                translationsOutgoingText = inWeek.sumOf { it.translationsOutgoingText },
                translationsOutgoingVoice = inWeek.sumOf { it.translationsOutgoingVoice },
                translationsIncomingText = inWeek.sumOf { it.translationsIncomingText },
                translationsIncomingVoice = inWeek.sumOf { it.translationsIncomingVoice },
                toLearning = inWeek.sumOf { it.toLearning },
                toFamiliar = inWeek.sumOf { it.toFamiliar },
                toMastered = inWeek.sumOf { it.toMastered },
                lessonsCompleted = inWeek.sumOf { it.lessonsCompleted },
                actionsFullSupport = inWeek.sumOf { it.actionsFullSupport },
                actionsAdaptive = inWeek.sumOf { it.actionsAdaptive },
                actionsOnDemand = inWeek.sumOf { it.actionsOnDemand },
            )
        }.toList()

        val thisWeek = weeks.last()
        val measured = weeks.filter { it.dependencePercent != null }
        val everMet = days.sumOf { it.wordsMet } > 0
        val status = when {
            !everMet -> DependenceStatus.NoData
            measured.size >= rules.minWeeksForTrend -> DependenceStatus.Ready(measured)
            else -> DependenceStatus.Collecting(thisWeek.wordsMet, rules.minWordsPerWeek, measured.size, rules.minWeeksForTrend)
        }
        return ProgressReport(
            language = language,
            totals = totals,
            dependence = status,
            weeks = weeks,
            thisWeek = thisWeek,
            masteryTrendReady = weeks.count { it.snapshot != null } >= 2,
            activityTrendReady = weeks.count { it.learningActions + it.wordsMet > 0 } >= 2,
            assistanceTrendReady = weeks.count { it.assistance > 0 } >= 2,
        )
    }

    /** The week's percentage, or null without enough words to mean anything. */
    fun dependence(wordsMet: Int, wordsAssisted: Int, rules: ProgressRules = ProgressRules()): Int? =
        if (wordsMet < rules.minWordsPerWeek || wordsMet <= 0) null else Math.round(wordsAssisted.coerceIn(0, wordsMet) * 100.0 / wordsMet).toInt()

    /** The Monday of the week that contains [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
