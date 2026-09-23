package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.map.LanguageMapItem
import com.alterlingua.app.learning.map.MasteryCalculator
import java.util.Locale
import kotlin.math.ln

/** The reasons an item can be chosen for the day's lesson. Each contributes named points, so a choice can be explained. */
enum class SignalKind { USEFULNESS, FREQUENCY, RECENCY, MASTERY_NEED, HELP_REQUESTS, REPETITION }

data class SelectionSignal(val kind: SignalKind, val points: Double, val note: String)

/** One item with the score it earned and why. */
data class LessonCandidate(val item: LanguageMapItem, val score: Double, val signals: List<SelectionSignal>) {
    /** The choice in plain words, one line per signal. */
    fun explain(): String = buildString {
        append("${item.displayForm}: score ${String.format(Locale.ROOT, "%.1f", score)}")
        for (signal in signals.filter { it.points != 0.0 }) {
            append('\n').append(if (signal.points > 0) "+" else "-").append(String.format(Locale.ROOT, "%.1f", kotlin.math.abs(signal.points))).append(' ').append(signal.note)
        }
    }
}

enum class ExclusionReason { MASTERED, TAUGHT_RECENTLY, BELOW_MINIMUM }

data class SelectionResult(
    /** The items for the lesson, best first. */
    val chosen: List<LessonCandidate>,
    /** Everything that qualified, ranked, including what was not chosen. */
    val ranked: List<LessonCandidate>,
    val excluded: List<Pair<LanguageMapItem, ExclusionReason>>,
)

/**
 * Every number the selection uses. Score = usefulness (up to 30) + frequency (up to 10, with diminishing returns) +
 * recency (up to 12) + how much the item needs learning (UNKNOWN 10, LEARNING 14, FAMILIAR 6) + translation-help
 * requests (8 each, up to 24) + repetition value (6 if never taught, 10 if a spaced review is due).
 * Frequency is deliberately small, so the most frequent words do not simply win.
 */
data class SelectionConfig(
    val dailyTarget: Int = 3,
    val usefulnessWeight: Double = 30.0,
    val frequencyMax: Double = 10.0,
    val frequencyScale: Double = 4.0,
    val recencyWithin24h: Double = 12.0,
    val recencyWithin3d: Double = 8.0,
    val recencyWithin7d: Double = 4.0,
    val needUnknown: Double = 10.0,
    val needLearning: Double = 14.0,
    val needFamiliar: Double = 6.0,
    val helpPoints: Double = 8.0,
    val helpMax: Double = 24.0,
    val neverTaught: Double = 6.0,
    val reviewDue: Double = 10.0,
    /** An item taught within this many hours is not taught again (so nothing is repeated twice in a day). */
    val minHoursBetweenLessons: Int = 20,
    val learningReviewAfterDays: Int = 2,
    val familiarReviewAfterDays: Int = 5,
    val minimumScore: Double = 20.0,
    val maxOfOneType: Int = 2,
)

/**
 * Chooses about three high-value items for the day from the Personal Language Map. It is a fixed, deterministic ranking
 * (no learned weights): the same map and the same time always give the same lesson. Mastered items are left out; items
 * that already fell back through inactivity qualify again. Items are chosen so they do not overlap (`devis` and
 * `envoyer le devis` would teach the same thing twice) and so a lesson is not three words of the same kind.
 */
class LessonSelector(
    private val config: SelectionConfig = SelectionConfig(),
    private val calculator: MasteryCalculator = MasteryCalculator(),
) {
    fun select(items: List<LanguageMapItem>, nowMillis: Long): SelectionResult {
        val excluded = mutableListOf<Pair<LanguageMapItem, ExclusionReason>>()
        val ranked = mutableListOf<LessonCandidate>()
        for (item in items) {
            val state = calculator.evaluate(item.evidence, nowMillis).state // as of now, including time unseen
            when {
                state == MasteryStatus.MASTERED -> excluded += item to ExclusionReason.MASTERED
                item.lastLessonAt > 0 && nowMillis - item.lastLessonAt < config.minHoursBetweenLessons * HOUR_MILLIS -> excluded += item to ExclusionReason.TAUGHT_RECENTLY
                else -> {
                    val candidate = score(item, state, nowMillis)
                    if (candidate.score < config.minimumScore) excluded += item to ExclusionReason.BELOW_MINIMUM else ranked += candidate
                }
            }
        }
        ranked.sortWith(
            compareByDescending<LessonCandidate> { it.score }
                .thenByDescending { it.item.usefulness }
                .thenByDescending { it.item.lastSeen }
                .thenBy { it.item.normalized },
        )
        return SelectionResult(choose(ranked), ranked, excluded)
    }

    private fun score(item: LanguageMapItem, state: MasteryStatus, now: Long): LessonCandidate {
        val signals = mutableListOf<SelectionSignal>()

        val usefulness = usefulnessOf(item)
        signals += SelectionSignal(SignalKind.USEFULNESS, config.usefulnessWeight * usefulness, "useful ${kind(item)} (usefulness ${String.format(Locale.ROOT, "%.2f", usefulness)})")

        val frequency = minOf(config.frequencyMax, config.frequencyScale * ln(1.0 + item.exposureCount) / ln(2.0))
        signals += SelectionSignal(SignalKind.FREQUENCY, frequency, "met ${item.exposureCount} time${if (item.exposureCount == 1) "" else "s"} (counts for little)")

        val hoursSinceSeen = if (item.lastSeen <= 0) Double.MAX_VALUE else (now - item.lastSeen).coerceAtLeast(0) / HOUR_MILLIS.toDouble()
        val recency = when {
            hoursSinceSeen <= 24 -> config.recencyWithin24h to "seen in the last day"
            hoursSinceSeen <= 72 -> config.recencyWithin3d to "seen in the last 3 days"
            hoursSinceSeen <= 168 -> config.recencyWithin7d to "seen in the last week"
            else -> 0.0 to "not seen recently"
        }
        signals += SelectionSignal(SignalKind.RECENCY, recency.first, recency.second)

        // "Still learning it" needs evidence of studying (a lesson, or a recognition right or wrong). An item that only
        // reached LEARNING by being met often is still new to the learner, so frequency is not counted twice.
        val studied = item.lessonEncounters + item.correctRecognitions + item.incorrectRecognitions > 0
        val need = when (state) {
            MasteryStatus.UNKNOWN -> config.needUnknown to "new to you"
            MasteryStatus.LEARNING -> if (studied) config.needLearning to "you are still learning it" else config.needUnknown to "new to you"
            MasteryStatus.FAMILIAR -> config.needFamiliar to "familiar, worth a review"
            MasteryStatus.MASTERED -> 0.0 to "mastered"
        }
        signals += SelectionSignal(SignalKind.MASTERY_NEED, need.first, need.second)

        val help = minOf(item.helpRequests * config.helpPoints, config.helpMax)
        signals += SelectionSignal(SignalKind.HELP_REQUESTS, help, "you asked for its translation ${item.helpRequests} time${if (item.helpRequests == 1) "" else "s"}")

        val repetition = when {
            item.lastLessonAt <= 0 -> config.neverTaught to "not taught in a lesson yet"
            reviewDue(item, state, now) -> config.reviewDue to "due for a spaced review"
            else -> 0.0 to "taught recently"
        }
        signals += SelectionSignal(SignalKind.REPETITION, repetition.first, repetition.second)

        return LessonCandidate(item, signals.sumOf { it.points }, signals)
    }

    private fun reviewDue(item: LanguageMapItem, state: MasteryStatus, now: Long): Boolean {
        val days = if (state == MasteryStatus.FAMILIAR) config.familiarReviewAfterDays else config.learningReviewAfterDays
        return now - item.lastLessonAt >= days * DAY_MILLIS
    }

    /** The stored usefulness, or a guess from the kind of unit for older rows that have none. */
    private fun usefulnessOf(item: LanguageMapItem): Double = if (item.usefulness > 0.0) item.usefulness.coerceAtMost(1.0) else when (item.type) {
        UnitType.EXPRESSION -> 0.85
        UnitType.PHRASE -> 0.55
        UnitType.WORD -> 0.35
    }

    private fun kind(item: LanguageMapItem) = when (item.type) {
        UnitType.WORD -> "word"
        UnitType.PHRASE -> "phrase"
        UnitType.EXPRESSION -> "expression"
    }

    /** Best first, skipping overlaps and not letting one type fill the lesson unless nothing else is available. */
    private fun choose(ranked: List<LessonCandidate>): List<LessonCandidate> {
        val chosen = mutableListOf<LessonCandidate>()
        fun take(limitPerType: Int) {
            for (candidate in ranked) {
                if (chosen.size >= config.dailyTarget) return
                if (candidate in chosen) continue
                if (chosen.any { overlaps(it.item, candidate.item) }) continue
                if (chosen.count { it.item.type == candidate.item.type } >= limitPerType) continue
                chosen += candidate
            }
        }
        take(config.maxOfOneType)
        take(Int.MAX_VALUE)
        return chosen
    }

    /** True when one unit's words are contained in the other's (`devis` inside `envoyer le devis`). */
    private fun overlaps(a: LanguageMapItem, b: LanguageMapItem): Boolean {
        if (a.language != b.language) return false
        val x = a.normalized
        val y = b.normalized
        return x == y || contains(x, y) || contains(y, x)
    }

    private fun contains(whole: String, part: String): Boolean {
        if (part.length >= whole.length) return false
        return if (whole.contains(' ') || part.contains(' ')) " $whole ".contains(" $part ") else whole.contains(part) && !whole.any { it == ' ' } && isCjk(part)
    }

    private fun isCjk(text: String) = text.any { Character.UnicodeScript.of(it.code) in CJK }

    private companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        val CJK = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
    }
}

/** The learner-facing wording of a selection signal ("You asked for its translation"), or null if it is not worth showing. */
fun learningReasonFor(signal: SelectionSignal): LessonReason? = when (signal.kind) {
    SignalKind.USEFULNESS -> if (signal.points >= 20) LessonReason.USEFUL else null
    SignalKind.FREQUENCY -> null
    SignalKind.RECENCY -> if (signal.points >= 8) LessonReason.RECENT else null
    SignalKind.MASTERY_NEED -> if (signal.note.startsWith("you are still learning")) LessonReason.STILL_LEARNING else if (signal.note.startsWith("new")) LessonReason.NEW else LessonReason.WORTH_REVIEW
    SignalKind.HELP_REQUESTS -> if (signal.points > 0) LessonReason.ASKED_TRANSLATION else null
    SignalKind.REPETITION -> if (signal.note.startsWith("due")) LessonReason.TIME_TO_REVIEW else null
}
