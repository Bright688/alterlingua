package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.learning.Language

/**
 * A rough, readable-in-English respelling of a word or phrase, to help say it out loud — for example "rentre" becomes
 * "RAHN-truh". This is NOT a phonetic transcription (no IPA, no real sound data): it is a small set of common
 * spelling-to-sound substitutions per language, split into syllables, with the first syllable of the last word of the
 * phrase capitalized as a rough stress hint. It is meant to sit next to the real word, never replace it (CLAUDE.md
 * section 6.19), and it is read alongside a genuine recording (text-to-speech) via the Play button, which is the
 * trustworthy part.
 *
 * What this can and cannot do, said plainly:
 * - Latin-script languages (English, Français, Español, Deutsch, Italiano, Nederlands) get a rough guide. It covers common
 *   spelling patterns for each language, not every rule or exception, and the single "stress the first syllable of the
 *   last word" rule is a simplification — real stress placement varies by language and by word.
 * - English spelling is the least regular of the six, so its guide is the least reliable of the six.
 * - 中文 and 日本語 are not covered: a trustworthy guide needs Pinyin or furigana/romaji data this app does not have, and
 *   guessing would be worse than saying nothing. [guideFor] returns null for them; Play (text-to-speech) still works.
 */
object PronunciationGuide {
    /** Marks the end of a vowel-sound syllable nucleus inside the working string; never part of the visible result. */
    private const val MARK = '\u0001'

    /** The guide for [term] in [language], or null when none is available (中文, 日本語, or nothing to say). */
    fun guideFor(term: String, language: Language): String? {
        val engine = enginesByCode[language.code] ?: return null
        val words = term.split(WORD_SPLIT).map(::cleanWord).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.mapIndexed { index, word -> respell(word, engine, stressed = index == words.lastIndex) }.joinToString(" ")
    }

    private fun cleanWord(raw: String): String = raw.lowercase().filter { it.isLetter() || it == '\'' }

    private fun respell(word: String, engine: Engine, stressed: Boolean): String {
        val phonetic = StringBuilder()
        var i = 0
        while (i < word.length) {
            val rule = engine.ruleAt(word, i)
            if (rule != null) {
                phonetic.append(rule.replacement)
                i += rule.pattern.length
            } else {
                phonetic.append(word[i])
                i++
            }
        }
        val syllables = splitIntoSyllables(phonetic.toString())
        if (syllables.isEmpty()) return word
        return if (stressed) {
            (listOf(syllables.first().uppercase()) + syllables.drop(1)).joinToString("-")
        } else {
            syllables.joinToString("-")
        }
    }

    /** Cuts the marked phonetic string into syllables: everything up to and including a nucleus is one syllable; any
     * consonants left after the last nucleus (a word-final cluster English readers cannot sound out, like "tr") get a
     * helper "uh" so the word still ends on a sound a reader can say. */
    private fun splitIntoSyllables(phonetic: String): List<String> {
        val syllables = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in phonetic) {
            if (ch == MARK) {
                syllables += current.toString()
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) syllables += "${current}uh"
        return syllables
    }

    private val WORD_SPLIT = Regex("\\s+")

    /** One substitution: [pattern] in the original spelling becomes [replacement] in the rough phonetic string.
     * [endAnchored] rules only match when they reach the exact end of the word (used for silent letters). */
    private data class Rule(val pattern: String, val replacement: String, val endAnchored: Boolean = false)

    /** A vowel-sound replacement: ends the syllable nucleus it is part of. */
    private fun v(sound: String) = "$sound$MARK"

    /** A language's rules, longest pattern first within each group so e.g. "eau" is tried before "au". */
    private class Engine(rules: List<Rule>) {
        private val anchored = rules.filter { it.endAnchored }.sortedByDescending { it.pattern.length }
        private val normal = rules.filter { !it.endAnchored }.sortedByDescending { it.pattern.length }

        fun ruleAt(word: String, i: Int): Rule? {
            for (rule in anchored) {
                val end = i + rule.pattern.length
                if (end == word.length && word.regionMatches(i, rule.pattern, 0, rule.pattern.length)) return rule
            }
            for (rule in normal) {
                if (i + rule.pattern.length <= word.length && word.regionMatches(i, rule.pattern, 0, rule.pattern.length)) return rule
            }
            return null
        }
    }

    // ---- French ----

    private val french = Engine(
        listOf(
            // Nasal vowels (a simplification: French has exceptions where these letters are not nasal; not modelled).
            Rule("an", v("ahn")), Rule("am", v("ahn")), Rule("en", v("ahn")), Rule("em", v("ahn")),
            Rule("ain", v("ang")), Rule("ein", v("ang")), Rule("in", v("ang")), Rule("im", v("ang")), Rule("yn", v("ang")),
            Rule("on", v("ohn")), Rule("om", v("ohn")),
            Rule("un", v("uhn")), Rule("um", v("uhn")),
            // Vowel digraphs.
            Rule("eau", v("oh")), Rule("au", v("oh")), Rule("ou", v("oo")), Rule("oi", v("wah")),
            Rule("eu", v("uh")), Rule("ai", v("eh")), Rule("ei", v("eh")), Rule("ill", v("eey")),
            Rule("é", v("ay")), Rule("è", v("eh")), Rule("ê", v("eh")), Rule("ë", v("eh")), Rule("ô", v("oh")),
            // Consonants that are not read as in English.
            Rule("ch", "sh"), Rule("qu", "k"), Rule("gn", "ny"), Rule("j", "zh"), Rule("ge", v("zheh")), Rule("gi", v("zhee")),
            // Silent letters at the end of a word.
            Rule("es", "", endAnchored = true), Rule("e", "", endAnchored = true), Rule("s", "", endAnchored = true),
            Rule("t", "", endAnchored = true), Rule("d", "", endAnchored = true), Rule("x", "", endAnchored = true),
            Rule("z", "", endAnchored = true), Rule("p", "", endAnchored = true),
            // Bare vowels (tried last: anything a more specific rule above did not already consume).
            Rule("a", v("a")), Rule("e", v("uh")), Rule("i", v("ee")), Rule("o", v("o")), Rule("u", v("ü")), Rule("y", v("ee")),
        ),
    )

    // ---- Spanish ----

    private val spanish = Engine(
        listOf(
            Rule("ll", "y"), Rule("ñ", "ny"), Rule("rr", "rr"), Rule("qu", "k"), Rule("gue", v("geh")), Rule("gui", v("gee")),
            Rule("ce", v("seh")), Rule("ci", v("see")), Rule("j", "h"), Rule("h", ""), Rule("z", "s"), Rule("v", "b"),
            Rule("a", v("ah")), Rule("e", v("eh")), Rule("i", v("ee")), Rule("o", v("oh")), Rule("u", v("oo")),
        ),
    )

    // ---- Italian ----

    private val italian = Engine(
        listOf(
            Rule("gli", "ly"), Rule("gn", "ny"), Rule("sce", v("sheh")), Rule("sci", v("shee")),
            Rule("che", v("keh")), Rule("chi", v("kee")), Rule("ce", v("cheh")), Rule("ci", v("chee")),
            Rule("ge", v("jeh")), Rule("gi", v("jee")), Rule("qu", "k"), Rule("z", "ts"),
            Rule("a", v("ah")), Rule("e", v("eh")), Rule("i", v("ee")), Rule("o", v("oh")), Rule("u", v("oo")),
        ),
    )

    // ---- German ----

    private val german = Engine(
        listOf(
            Rule("sch", "sh"), Rule("tsch", "ch"), Rule("ch", "kh"), Rule("ck", "k"),
            Rule("ei", v("igh")), Rule("ie", v("ee")), Rule("eu", v("oy")), Rule("äu", v("oy")),
            Rule("ä", v("eh")), Rule("ö", v("ur")), Rule("ü", v("ü")), Rule("ß", "s"),
            Rule("v", "f"), Rule("w", "v"), Rule("z", "ts"), Rule("j", "y"),
            Rule("a", v("ah")), Rule("e", v("eh")), Rule("i", v("ih")), Rule("o", v("oh")), Rule("u", v("oo")),
        ),
    )

    // ---- Dutch ----

    private val dutch = Engine(
        listOf(
            Rule("sch", "skh"), Rule("ch", "kh"), Rule("ng", "ng"),
            Rule("ij", v("ey")), Rule("ui", v("ow")), Rule("oe", v("oo")), Rule("eu", v("uh")), Rule("oo", v("oh")), Rule("ee", v("ay")),
            Rule("g", "kh"), Rule("j", "y"), Rule("w", "v"),
            Rule("a", v("ah")), Rule("e", v("eh")), Rule("i", v("ih")), Rule("o", v("oh")), Rule("u", v("uh")),
        ),
    )

    // ---- English (the least regular of the six; see the class note) ----

    private val english = Engine(
        listOf(
            Rule("tion", v("shuhn")), Rule("sion", v("zhuhn")),
            Rule("th", "th"), Rule("sh", "sh"), Rule("ch", "ch"), Rule("ph", "f"), Rule("wh", "w"),
            Rule("oo", v("oo")), Rule("ee", v("ee")), Rule("ea", v("ee")), Rule("ou", v("ow")), Rule("ow", v("ow")),
            Rule("ay", v("ay")), Rule("ai", v("ay")), Rule("oy", v("oy")), Rule("oi", v("oy")),
            Rule("e", "", endAnchored = true), // a common (far from universal) silent final e
            Rule("a", v("a")), Rule("e", v("eh")), Rule("i", v("ih")), Rule("o", v("ah")), Rule("u", v("uh")),
        ),
    )

    // No entries for "zh" or "ja" (see the class note): guideFor returns null for them, whatever their WritingSystem.
    private val enginesByCode: Map<String, Engine> = mapOf(
        "en" to english, "fr" to french, "es" to spanish, "de" to german, "it" to italian, "nl" to dutch,
    )
}
