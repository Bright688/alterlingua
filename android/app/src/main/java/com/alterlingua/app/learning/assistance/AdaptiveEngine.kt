package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.AnalyzedToken
import com.alterlingua.app.learning.engine.CandidateExtractor
import com.alterlingua.app.learning.engine.Exclusion
import com.alterlingua.app.learning.engine.Script
import com.alterlingua.app.learning.engine.TokenKind
import com.alterlingua.app.learning.engine.TokenRole
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.map.LanguageMapItem
import com.alterlingua.app.learning.map.MasteryCalculator

/** How much help a message gets. */
enum class AdaptiveLevel {
    /** Everything is translated (the Full Support result). The safe answer whenever the engine is not sure. */
    FULL_TRANSLATION,

    /** The message stays in the learning language; only the units not yet mastered are glossed in the user's language. */
    PARTIAL,

    /** Every unit that could be judged is mastered: the message stays as it is, with no help added. */
    KEEP_ORIGINAL,
}

/** Why the engine chose full translation instead of keeping more of the language. Each is a "not sure, so help". */
enum class FallbackReason {
    /** The message is not in the language being learned, so this language's map says nothing about it. */
    NOT_THE_LEARNING_LANGUAGE,
    UNSUPPORTED_LANGUAGE,
    /** No word could be judged (only function words, names, numbers). */
    NOTHING_TO_JUDGE,
    /** Less than the required share of the units are mastered. */
    TOO_LITTLE_KNOWN,
    /** More units need help than a readable gloss can carry. */
    TOO_MANY_TO_GLOSS,
    /** A unit that needs help has no meaning saved in the user's language, so it cannot be glossed. */
    MEANING_MISSING,
    /** The unit could not be found again in the original text. */
    PLACEMENT_FAILED,
}

enum class UnitVerdict { KEEP, ASSIST }

/** The decision for one word, with the evidence in plain words. */
data class UnitDecision(
    val text: String,
    val normalized: String,
    val verdict: UnitVerdict,
    /** Mastery evaluated now (so a long absence counts); UNKNOWN when the unit is not in the map. */
    val state: MasteryStatus,
    val meaning: String?,
    val explanation: String,
)

/** A stretch of the original message; [gloss] is the user's-language meaning to show right after it, if any. */
data class GlossedSegment(val text: String, val gloss: String? = null)

data class AdaptiveDecision(
    val level: AdaptiveLevel,
    val units: List<UnitDecision>,
    /** The original message cut into segments (PARTIAL and KEEP_ORIGINAL only). Held in memory, never stored. */
    val segments: List<GlossedSegment>,
    val fallback: FallbackReason? = null,
) {
    val kept: List<UnitDecision> get() = units.filter { it.verdict == UnitVerdict.KEEP }
    val assisted: List<UnitDecision> get() = units.filter { it.verdict == UnitVerdict.ASSIST }
}

/**
 * The numbers behind the decision, all in one place.
 * @property keepAt a word may stay in the learning language only when its mastery, evaluated now, is at least this.
 *   MASTERED by default: only demonstrated knowledge removes help, never exposure alone.
 * @property minKeptShare the share of judged words that must be kept before the message is left in the language;
 *   below it the whole message is translated instead of a patchwork.
 * @property maxGlosses at most this many words are glossed; more and the message is translated whole.
 */
data class AdaptiveConfig(
    val keepAt: MasteryStatus = MasteryStatus.MASTERED,
    val minKeptShare: Double = 0.5,
    val maxGlosses: Int = 3,
)

/**
 * The deterministic Adaptive engine: decides, from the Personal Language Map of the language being learned, which words of
 * a message can safely stay in that language.
 *
 * How it decides (no model, no randomness; the same message and map always give the same answer):
 * 1. Only a message written in the learning language is considered; anything else gets full translation.
 * 2. The message is analysed with the analyzer for its language (dictionary segmentation for 中文 and 日本語), and only
 *    content words are judged. Function words, names and numbers are neutral: they neither keep nor block.
 * 3. A word is KEPT only when the map has it and its mastery, evaluated now, reaches [AdaptiveConfig.keepAt]. A word the
 *    map does not know is never assumed known.
 * 4. A fixed expression found in the message decides for its own words: mastered means they stay, otherwise they get help.
 *    (Knowing every word of "au courant" is not knowing the expression.)
 * 5. Everything mastered: keep the original. Enough mastered and the rest glossable: keep the original and gloss the rest
 *    with the meanings saved in the map. In every other case: full translation.
 *
 * It never replaces a word with a word from another language and never guesses a meaning: a gloss is only a meaning the
 * map already holds, in the user's language.
 */
class AdaptiveEngine(
    private val analyzers: AnalyzerRegistry,
    private val calculator: MasteryCalculator = MasteryCalculator(),
    private val config: AdaptiveConfig = AdaptiveConfig(),
) {
    suspend fun decide(
        text: String,
        textLanguage: Language,
        learning: Language,
        native: Language,
        nowMillis: Long,
        lookup: suspend (UnitKey) -> LanguageMapItem?,
    ): AdaptiveDecision {
        if (textLanguage.code != learning.code) return full(FallbackReason.NOT_THE_LEARNING_LANGUAGE)
        val analyzer = analyzers.forLanguage(learning) ?: return full(FallbackReason.UNSUPPORTED_LANGUAGE)
        val profile = analyzer.profile(learning)
        val tokens = analyzer.analyze(text, learning)

        val expressionVerdicts = expressionStates(tokens, profile, native, nowMillis, lookup)

        val judged = mutableListOf<Pair<AnalyzedToken, UnitDecision>>()
        for ((index, token) in tokens.withIndex()) {
            if (token.kind != TokenKind.WORD || token.exclusion != Exclusion.NONE || token.role != TokenRole.CONTENT) continue
            val item = lookup(UnitKey(learning.code, token.normalized, UnitType.WORD))
            val own = item?.let { calculator.evaluate(it.evidence, nowMillis).state } ?: MasteryStatus.UNKNOWN
            val meaning = item?.takeIf { it.meaningLanguage == native.code }?.meaning?.takeIf { it.isNotBlank() }
            val expression = expressionVerdicts[index]
            val keep: Boolean
            val why: String
            if (expression != null) {
                keep = expression.atLeast(config.keepAt)
                why = if (keep) "part of an expression you have mastered" else "part of an expression you have not mastered yet (${expression.name.lowercase()})"
            } else {
                keep = own.atLeast(config.keepAt)
                why = when {
                    item == null -> "not in your language map yet, so not assumed known"
                    keep -> "mastered"
                    else -> "${own.name.lowercase()}, not mastered yet"
                }
            }
            judged += token to UnitDecision(token.text, token.normalized, if (keep) UnitVerdict.KEEP else UnitVerdict.ASSIST, own, meaning, why)
        }

        val units = judged.map { it.second }
        if (units.isEmpty()) return full(FallbackReason.NOTHING_TO_JUDGE)
        val assisted = judged.filter { it.second.verdict == UnitVerdict.ASSIST }
        if (assisted.isEmpty()) return AdaptiveDecision(AdaptiveLevel.KEEP_ORIGINAL, units, listOf(GlossedSegment(text)))

        val keptShare = (units.size - assisted.size).toDouble() / units.size
        if (keptShare < config.minKeptShare) return full(FallbackReason.TOO_LITTLE_KNOWN, units)
        if (assisted.size > config.maxGlosses) return full(FallbackReason.TOO_MANY_TO_GLOSS, units)
        if (assisted.any { it.second.meaning == null }) return full(FallbackReason.MEANING_MISSING, units)

        val segments = place(text, assisted.map { it.first.text to it.second.meaning!! }) ?: return full(FallbackReason.PLACEMENT_FAILED, units)
        return AdaptiveDecision(AdaptiveLevel.PARTIAL, units, segments)
    }

    /** For each token covered by a fixed expression, the mastery state of that expression (UNKNOWN if the map lacks it). */
    private suspend fun expressionStates(
        tokens: List<AnalyzedToken>,
        profile: com.alterlingua.app.learning.engine.LanguageProfile,
        native: Language,
        nowMillis: Long,
        lookup: suspend (UnitKey) -> LanguageMapItem?,
    ): Map<Int, MasteryStatus> {
        val expressions = CandidateExtractor(profile).extract(tokens, native.code, nowMillis).filter { it.type == UnitType.EXPRESSION }
        if (expressions.isEmpty()) return emptyMap()
        val out = HashMap<Int, MasteryStatus>()
        for (expression in expressions) {
            val item = lookup(expression.key)
            val state = item?.let { calculator.evaluate(it.evidence, nowMillis).state } ?: MasteryStatus.UNKNOWN
            for (start in tokens.indices) {
                for (length in 1..MAX_EXPRESSION_TOKENS) {
                    val end = start + length
                    if (end > tokens.size) break
                    val window = tokens.subList(start, end)
                    if (window.any { it.kind != TokenKind.WORD }) break
                    if (profile.join(window.map { it.normalized }) == expression.normalized) {
                        // Where expressions overlap, the least-known one decides: the safe side.
                        for (i in start until end) out.merge(i, state) { old, new -> if (new.ordinal < old.ordinal) new else old }
                    }
                }
            }
        }
        return out
    }

    /** Cuts the text after each unit that gets a gloss, in order. Null if a unit cannot be found again. */
    private fun place(text: String, glosses: List<Pair<String, String>>): List<GlossedSegment>? {
        val segments = mutableListOf<GlossedSegment>()
        var cursor = 0
        for ((unit, gloss) in glosses) {
            val at = text.indexOf(unit, cursor)
            if (at < 0) return null
            val end = at + unit.length
            segments += GlossedSegment(text.substring(cursor, end), gloss)
            cursor = end
        }
        if (cursor < text.length) segments += GlossedSegment(text.substring(cursor))
        return segments
    }

    private fun full(reason: FallbackReason, units: List<UnitDecision> = emptyList()) =
        AdaptiveDecision(AdaptiveLevel.FULL_TRANSLATION, units, emptyList(), reason)

    private fun MasteryStatus.atLeast(other: MasteryStatus) = ordinal >= other.ordinal

    private companion object {
        const val MAX_EXPRESSION_TOKENS = 6
    }
}

/** Writes a PARTIAL decision as text, the way the language is written: a gloss follows its word. */
object AdaptivePresentation {
    /** `Je vais vous envoyer le devis (quotation) avant midi.`; for 中文 and 日本語 full-width brackets without spaces. */
    fun plainText(decision: AdaptiveDecision, script: Script): String {
        val (open, close) = if (script == Script.CJK) "（" to "）" else " (" to ")"
        return decision.segments.joinToString("") { segment ->
            if (segment.gloss == null) segment.text else "${segment.text}$open${segment.gloss}$close"
        }
    }
}
