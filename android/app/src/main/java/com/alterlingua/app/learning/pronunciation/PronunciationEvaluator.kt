package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.WritingSystem
import java.text.Normalizer
import java.util.Locale

/**
 * How the practice attempt is described to the learner. This is NOT a pronunciation score. It only says how the text a
 * speech recognizer produced compares with the word or phrase being practised.
 */
enum class PronunciationVerdict {
    /** The recognizer heard exactly the word or phrase (ignoring capitals, punctuation and accents). */
    GOOD,

    /** The recognizer heard something very close: nearly right, worth one more try. */
    CLOSE,

    /** The recognizer heard something else. */
    TRY_AGAIN,

    /** The recognizer heard nothing it could use. */
    NOT_HEARD,
}

data class PronunciationFeedback(val verdict: PronunciationVerdict, val heard: String?)

/**
 * Compares what the speech recognizer heard with what the learner was practising.
 *
 * What this can and cannot tell (be honest about the provider): a recognizer returns text, not sounds. If it writes the
 * expected word, a listener could understand the learner; if it writes another word, either the pronunciation was off or
 * the recognizer was wrong. It says nothing about accent, rhythm, pitch or individual sounds, so there are no percentages
 * and no sound-by-sound feedback.
 *
 * The comparison is deterministic and language-aware:
 * - Latin scripts: capitals, punctuation and accents are ignored (a recognizer's spelling choices are not pronunciation).
 * - 中文 and 日本語: punctuation and spaces are ignored, and katakana counts as hiragana. A word the recognizer writes in a
 *   different script (kanji instead of kana) cannot be matched, and shows as TRY_AGAIN with what was heard; that is a limit of
 *   comparing written text, not a judgement of the learner.
 * - CLOSE needs a near match on a unit long enough for that to mean something (at least [MIN_CLOSE_LENGTH] characters, and
 *   only for spaced scripts); short units and 中文 or 日本語 are either GOOD or not.
 */
object PronunciationEvaluator {
    const val MIN_CLOSE_LENGTH = 5
    const val CLOSE_SIMILARITY = 0.8

    fun evaluate(expected: String, heard: String?, language: Language): PronunciationFeedback {
        val cleanHeard = heard?.trim().orEmpty()
        val wanted = normalize(expected, language)
        val got = normalize(cleanHeard, language)
        if (got.isEmpty()) return PronunciationFeedback(PronunciationVerdict.NOT_HEARD, null)
        val verdict = when {
            got == wanted -> PronunciationVerdict.GOOD
            language.writingSystem == WritingSystem.LATIN && wanted.length >= MIN_CLOSE_LENGTH && similarity(wanted, got) >= CLOSE_SIMILARITY -> PronunciationVerdict.CLOSE
            else -> PronunciationVerdict.TRY_AGAIN
        }
        return PronunciationFeedback(verdict, cleanHeard)
    }

    /** The comparison form of [text] for [language]. */
    fun normalize(text: String, language: Language): String {
        val lower = text.lowercase(Locale.ROOT)
        return when (language.writingSystem) {
            WritingSystem.LATIN -> Normalizer.normalize(lower, Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                .filter { it.isLetterOrDigit() }
            WritingSystem.CHINESE -> Normalizer.normalize(lower, Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
            WritingSystem.JAPANESE -> Normalizer.normalize(lower, Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }.map(::katakanaToHiragana).joinToString("")
        }
    }

    private fun katakanaToHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c

    /** 1 for identical strings, 0 for nothing in common (edit distance relative to the longer one). */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return 1.0 - editDistance(a, b).toDouble() / maxOf(a.length, b.length)
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }
}
