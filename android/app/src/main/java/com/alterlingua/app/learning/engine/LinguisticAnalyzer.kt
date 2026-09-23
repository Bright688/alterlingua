package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Language

/** The part a word plays, at the level this engine needs (not full grammar). */
enum class TokenRole { CONTENT, ARTICLE, PRONOUN, PREPOSITION, CONJUNCTION, AUXILIARY, PARTICLE, NEGATION, ADVERB }

/** What kind of piece of text a token is. */
enum class TokenKind { WORD, PUNCTUATION }

/** Why a word token is not offered as a learning unit. */
enum class Exclusion { NONE, NUMBER, PROPER_NOUN, ACRONYM, TOO_SHORT }

/**
 * One piece of analysed text. [normalized] is the form used for lookup. Text and offsets are not kept beyond the
 * analysis: tokens exist only while a message is being processed.
 */
data class AnalyzedToken(
    val text: String,
    val normalized: String,
    val kind: TokenKind,
    val role: TokenRole = TokenRole.CONTENT,
    val exclusion: Exclusion = Exclusion.NONE,
    val startsSentence: Boolean = false,
)

/**
 * The linguistic-analysis abstraction. An implementation says which languages it can analyse and turns text into
 * analysed tokens using the right strategy for that language (spaces are not assumed: 中文 and 日本語 need dictionary
 * segmentation). A different implementation, for example a full NLP library, can replace the rule-based one for any
 * language without touching the rest of the engine.
 */
interface LinguisticAnalyzer {
    fun supports(language: Language): Boolean

    /** The unit-extraction behaviour for [language] (function words, expressions and chunk direction). */
    fun profile(language: Language): LanguageProfile

    fun analyze(text: String, language: Language): List<AnalyzedToken>
}

/** The analyzers available, so a language without one is reported as unsupported instead of analysed wrongly. */
class AnalyzerRegistry(private val analyzers: List<LinguisticAnalyzer>) {
    fun forLanguage(language: Language): LinguisticAnalyzer? = analyzers.firstOrNull { it.supports(language) }

    fun supports(language: Language): Boolean = forLanguage(language) != null
}
