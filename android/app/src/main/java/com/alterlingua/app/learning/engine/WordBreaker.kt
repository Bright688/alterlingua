package com.alterlingua.app.learning.engine

/** A piece of text found by a word breaker: a word, or the punctuation or space between words. */
data class RawToken(val text: String, val isWord: Boolean)

/**
 * Splits text into words for one language. Behind an interface because the right method depends on the writing
 * system: Latin-script languages can use letters and punctuation, but 中文 and 日本語 have no spaces between words and
 * need a dictionary (ICU), which Android provides and JVM tests get from the ICU4J library.
 */
interface WordBreaker {
    fun segment(text: String, languageTag: String): List<RawToken>
}

/**
 * Word breaking for languages that use spaces: a word is a run of letters and digits, which may contain an
 * apostrophe or hyphen between letters (`l'envoyer`, `peut-être`, `don't`). Everything else is punctuation.
 * Spaces are dropped. Does not use any platform class, so it behaves the same everywhere.
 */
object LatinWordBreaker : WordBreaker {
    override fun segment(text: String, languageTag: String): List<RawToken> {
        val out = mutableListOf<RawToken>()
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c.isLetterOrDigit() -> {
                    val start = index
                    index++
                    while (index < text.length) {
                        val d = text[index]
                        val joiner = (d == '\'' || d == '’' || d == '-') &&
                            index + 1 < text.length && text[index + 1].isLetter() && text[index - 1].isLetter()
                        val combiningMark = Character.getType(d).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }
                        if (d.isLetterOrDigit() || combiningMark || joiner) index++ else break
                    }
                    // A joiner apostrophe stays inside the word; the analyzer decides whether it is an elision.
                    out += RawToken(text.substring(start, index), isWord = true)
                }
                c.isWhitespace() -> index++
                else -> {
                    out += RawToken(c.toString(), isWord = false)
                    index++
                }
            }
        }
        return out
    }
}

/**
 * Dictionary-based word breaking with Android's built-in ICU (API 24+), the component that segments 中文 and 日本語.
 * The word iterator marks whitespace, punctuation and symbols with a rule status below `WORD_NONE_LIMIT`.
 */
object AndroidIcuWordBreaker : WordBreaker {
    override fun segment(text: String, languageTag: String): List<RawToken> {
        val iterator = android.icu.text.BreakIterator.getWordInstance(android.icu.util.ULocale.forLanguageTag(languageTag))
        iterator.setText(text)
        val out = mutableListOf<RawToken>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            val piece = text.substring(start, end)
            val status = iterator.ruleStatus
            if (piece.isNotBlank()) out += RawToken(piece, isWord = status >= android.icu.text.BreakIterator.WORD_NONE_LIMIT)
            start = end
            end = iterator.next()
        }
        return out
    }
}
