package com.alterlingua.app.learning.engine

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

/** The same dictionary word breaking Android provides (android.icu), from the ICU4J library, for JVM unit tests. */
object Icu4jWordBreaker : WordBreaker {
    override fun segment(text: String, languageTag: String): List<RawToken> {
        val iterator = BreakIterator.getWordInstance(ULocale.forLanguageTag(languageTag))
        iterator.setText(text)
        val out = mutableListOf<RawToken>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val piece = text.substring(start, end)
            if (piece.isNotBlank()) out += RawToken(piece, isWord = iterator.ruleStatus >= BreakIterator.WORD_NONE_LIMIT)
            start = end
            end = iterator.next()
        }
        return out
    }
}
