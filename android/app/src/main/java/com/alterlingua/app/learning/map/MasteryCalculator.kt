package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.MasteryStatus
import java.util.Locale

/** The counts a mastery score is computed from. Nothing else influences it. */
data class MasteryEvidence(
    val exposures: Int = 0,
    val lessonEncounters: Int = 0,
    val correctRecognitions: Int = 0,
    val incorrectRecognitions: Int = 0,
    val helpRequests: Int = 0,
    /** When the unit was last met, in milliseconds; 0 if unknown. Used only for the inactivity rule. */
    val lastSeenMillis: Long = 0,
    /** When translation help was last asked for, in milliseconds; 0 if never. Used only for the recent-help rule. */
    val lastHelpMillis: Long = 0,
    /** Practice attempts in which the speech recognizer understood the learner saying the unit. */
    val pronunciationGood: Int = 0,
)

/**
 * The numbers behind the calculation. Every rule is one of these, so the whole calculation can be read here.
 *
 * Score = 0.5 per exposure (at most 12) + 4 per lesson encounter (at most 16) + 15 per correct recognition (at most 75)
 *         - 12 per incorrect recognition - 3 per translation-help request (at most 15) - an inactivity penalty,
 *         kept between 0 and 100.
 *
 * State from the score: UNKNOWN below 10, LEARNING from 10, FAMILIAR from 40, MASTERED from 75. Two gates stop
 * shortcuts (and a third rule: asking for a unit's translation within the last 14 days holds it at FAMILIAR, since a learner who
 * just asked what a word means does not fully know it): FAMILIAR needs at least 2 correct recognitions, and MASTERED needs at least 4 correct recognitions with
 * an accuracy of at least 80%. So meeting a word many times can never make it more than LEARNING (exposure counts for at
 * most 12 points and proves nothing by itself, CLAUDE.md section 14).
 */
data class MasteryRules(
    val exposurePoints: Double = 0.5,
    val exposureMax: Double = 12.0,
    val lessonPoints: Double = 4.0,
    val lessonMax: Double = 16.0,
    val correctPoints: Double = 15.0,
    val correctMax: Double = 75.0,
    val incorrectPenalty: Double = 12.0,
    val helpPenalty: Double = 3.0,
    val helpMax: Double = 15.0,
    val learningAt: Double = 10.0,
    val familiarAt: Double = 40.0,
    val masteredAt: Double = 75.0,
    val familiarMinCorrect: Int = 2,
    val masteredMinCorrect: Int = 4,
    val masteredMinAccuracy: Double = 0.8,
    /** Days without meeting the unit before the score starts to fall, then how much it falls per further 30 days. */
    val inactiveAfterDays: Int = 60,
    val inactivityPenaltyPer30Days: Double = 10.0,
    val inactivityMax: Double = 40.0,
    /** Asking for a unit's translation within this many days means it is not mastered right now (at most FAMILIAR). */
    val recentHelpDays: Int = 14,
    /** Each pronunciation attempt the recognizer understood adds a little; it never removes points and never gates a state. */
    val pronunciationPoints: Double = 3.0,
    val pronunciationMax: Double = 9.0,
)

enum class MasteryFactorKind { EXPOSURE, LESSON, CORRECT, INCORRECT, HELP, PRONUNCIATION, INACTIVITY }

/** One line of the explanation: what counted, and how many points it added or removed. */
data class MasteryFactor(val kind: MasteryFactorKind, val count: Int, val points: Double)

data class MasteryEvaluation(
    val score: Double,
    val state: MasteryStatus,
    val factors: List<MasteryFactor>,
    /** What is still missing for the next state, or null at MASTERED. */
    val nextStep: String?,
) {
    /** A plain-language account of why the score and state are what they are. */
    fun explain(): String = buildString {
        append("Score ${format(score)} of 100, so $state.")
        for (factor in factors.filter { it.points != 0.0 }) {
            append('\n')
            append(if (factor.points > 0) "+" else "-").append(format(kotlin.math.abs(factor.points))).append(' ').append(describe(factor))
        }
        nextStep?.let { append('\n').append(it) }
    }

    private fun describe(factor: MasteryFactor) = when (factor.kind) {
        MasteryFactorKind.EXPOSURE -> "for meeting it ${factor.count} times (counts for little)"
        MasteryFactorKind.LESSON -> "for ${factor.count} lesson encounters"
        MasteryFactorKind.CORRECT -> "for ${factor.count} correct recognitions"
        MasteryFactorKind.INCORRECT -> "for ${factor.count} incorrect recognitions"
        MasteryFactorKind.HELP -> "for ${factor.count} translation-help requests"
        MasteryFactorKind.PRONUNCIATION -> "for ${factor.count} practice attempts the speech recognizer understood"
        MasteryFactorKind.INACTIVITY -> "for ${factor.count} days without meeting it"
    }

    private fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)
}

/**
 * The first, deliberately simple mastery calculation: a fixed formula over counts, with two gates. It is deterministic
 * (the same evidence and the same time always give the same answer), has no hidden state and no learned weights, and every
 * result lists the points that make it up. It is not a CEFR level and does not estimate one.
 */
class MasteryCalculator(private val rules: MasteryRules = MasteryRules()) {

    fun evaluate(evidence: MasteryEvidence, nowMillis: Long): MasteryEvaluation {
        val exposure = minOf(evidence.exposures * rules.exposurePoints, rules.exposureMax)
        val lessons = minOf(evidence.lessonEncounters * rules.lessonPoints, rules.lessonMax)
        val correct = minOf(evidence.correctRecognitions * rules.correctPoints, rules.correctMax)
        val incorrect = -evidence.incorrectRecognitions * rules.incorrectPenalty
        val help = -minOf(evidence.helpRequests * rules.helpPenalty, rules.helpMax)
        val pronunciation = minOf(evidence.pronunciationGood * rules.pronunciationPoints, rules.pronunciationMax)
        val daysInactive = daysSince(evidence.lastSeenMillis, nowMillis)
        val inactivity = -inactivityPenalty(daysInactive)

        val factors = listOf(
            MasteryFactor(MasteryFactorKind.EXPOSURE, evidence.exposures, exposure),
            MasteryFactor(MasteryFactorKind.LESSON, evidence.lessonEncounters, lessons),
            MasteryFactor(MasteryFactorKind.CORRECT, evidence.correctRecognitions, correct),
            MasteryFactor(MasteryFactorKind.INCORRECT, evidence.incorrectRecognitions, incorrect),
            MasteryFactor(MasteryFactorKind.HELP, evidence.helpRequests, help),
            MasteryFactor(MasteryFactorKind.PRONUNCIATION, evidence.pronunciationGood, pronunciation),
            MasteryFactor(MasteryFactorKind.INACTIVITY, daysInactive, inactivity),
        )
        val score = (exposure + lessons + correct + incorrect + help + pronunciation + inactivity).coerceIn(0.0, 100.0)
        val state = stateFor(score, evidence, nowMillis)
        return MasteryEvaluation(score, state, factors, nextStep(state, score, evidence, nowMillis))
    }

    private fun stateFor(score: Double, evidence: MasteryEvidence, nowMillis: Long): MasteryStatus {
        var state = when {
            score >= rules.masteredAt -> MasteryStatus.MASTERED
            score >= rules.familiarAt -> MasteryStatus.FAMILIAR
            score >= rules.learningAt -> MasteryStatus.LEARNING
            else -> MasteryStatus.UNKNOWN
        }
        if (state == MasteryStatus.MASTERED && !(evidence.correctRecognitions >= rules.masteredMinCorrect && accuracy(evidence) >= rules.masteredMinAccuracy)) {
            state = MasteryStatus.FAMILIAR
        }
        if (state == MasteryStatus.FAMILIAR && evidence.correctRecognitions < rules.familiarMinCorrect) {
            state = MasteryStatus.LEARNING
        }
        // Asking what a word means is direct evidence that it is not fully known, whatever the counts say.
        if (state == MasteryStatus.MASTERED && askedForHelpRecently(evidence, nowMillis)) state = MasteryStatus.FAMILIAR
        return state
    }

    private fun nextStep(state: MasteryStatus, score: Double, evidence: MasteryEvidence, nowMillis: Long): String? = when (state) {
        MasteryStatus.UNKNOWN -> "Next: reach a score of ${rules.learningAt.toInt()} (a correct recognition or a few lesson encounters) to become LEARNING."
        MasteryStatus.LEARNING -> buildString {
            append("Next: FAMILIAR needs a score of ${rules.familiarAt.toInt()}")
            if (evidence.correctRecognitions < rules.familiarMinCorrect) append(" and at least ${rules.familiarMinCorrect} correct recognitions")
            append('.')
        }
        MasteryStatus.FAMILIAR -> buildString {
            if (askedForHelpRecently(evidence, nowMillis)) append("You asked for its translation recently, so it cannot count as mastered for ${rules.recentHelpDays} days after asking. ")
            append("Next: MASTERED needs a score of ${rules.masteredAt.toInt()}")
            if (evidence.correctRecognitions < rules.masteredMinCorrect) append(", at least ${rules.masteredMinCorrect} correct recognitions")
            if (accuracy(evidence) < rules.masteredMinAccuracy) append(", and an accuracy of at least ${(rules.masteredMinAccuracy * 100).toInt()}%")
            append('.')
        }
        MasteryStatus.MASTERED -> null
    }

    private fun askedForHelpRecently(evidence: MasteryEvidence, nowMillis: Long): Boolean =
        evidence.lastHelpMillis > 0 && nowMillis - evidence.lastHelpMillis < rules.recentHelpDays * DAY_MILLIS

    private fun accuracy(evidence: MasteryEvidence): Double {
        val total = evidence.correctRecognitions + evidence.incorrectRecognitions
        return if (total == 0) 0.0 else evidence.correctRecognitions.toDouble() / total
    }

    private fun daysSince(lastSeenMillis: Long, nowMillis: Long): Int =
        if (lastSeenMillis <= 0 || nowMillis <= lastSeenMillis) 0 else ((nowMillis - lastSeenMillis) / DAY_MILLIS).toInt()

    private fun inactivityPenalty(days: Int): Double {
        if (days <= rules.inactiveAfterDays) return 0.0
        val blocks = (days - rules.inactiveAfterDays) / 30 + 1
        return minOf(blocks * rules.inactivityPenaltyPer30Days, rules.inactivityMax)
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
