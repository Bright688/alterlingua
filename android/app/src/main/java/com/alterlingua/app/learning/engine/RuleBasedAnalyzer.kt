package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Language
import java.text.Normalizer
import java.util.Locale

/**
 * The analyzer for the initial languages: language-aware tokenization plus a rule-based classification of function
 * words, with the profile of each language supplying the data. It does not lemmatize or tag grammar; a fuller NLP
 * component can replace it for any language behind [LinguisticAnalyzer].
 *
 * Writing systems: languages with spaces use [latinBreaker]; 中文 and 日本語 use [cjkBreaker] (dictionary segmentation).
 * If no dictionary breaker is available, CJK languages are reported as unsupported rather than split badly.
 */
class RuleBasedAnalyzer(
    profiles: List<LanguageProfile> = LanguageProfiles.all,
    private val latinBreaker: WordBreaker = LatinWordBreaker,
    private val cjkBreaker: WordBreaker? = null,
) : LinguisticAnalyzer {

    private val byCode = profiles.associateBy { it.code }

    override fun supports(language: Language): Boolean {
        val profile = byCode[language.code] ?: return false
        return profile.script == Script.LATIN || cjkBreaker != null
    }

    override fun profile(language: Language): LanguageProfile = byCode.getValue(language.code)

    override fun analyze(text: String, language: Language): List<AnalyzedToken> {
        val profile = byCode[language.code] ?: return emptyList()
        val breaker = if (profile.script == Script.LATIN) latinBreaker else cjkBreaker ?: return emptyList()
        // Links and addresses are dropped before analysis: they are never learning material.
        val cleaned = LINK.replace(text, " ")
            .let { Normalizer.normalize(it, if (profile.script == Script.CJK) Normalizer.Form.NFKC else Normalizer.Form.NFC) }
        val out = mutableListOf<AnalyzedToken>()
        var sentenceStart = true
        for (raw in breaker.segment(cleaned, profile.code)) {
            if (!raw.isWord) {
                out += AnalyzedToken(raw.text, raw.text, TokenKind.PUNCTUATION)
                if (raw.text.any { it in SENTENCE_END }) sentenceStart = true
                continue
            }
            for (token in wordTokens(raw.text, profile, sentenceStart)) {
                out += token
                sentenceStart = false
            }
        }
        return out
    }

    private fun wordTokens(rawText: String, profile: LanguageProfile, startsSentence: Boolean): List<AnalyzedToken> {
        val text = Normalizer.normalize(rawText, if (profile.script == Script.CJK) Normalizer.Form.NFKC else Normalizer.Form.NFC).replace('’', '\'')
        val lower = text.lowercase(Locale.ROOT)
        // French and Italian write "l'envoyer": split the elision off so the word itself can be found.
        val elision = profile.elisions.filter { lower.startsWith(it) && lower.length > it.length }.maxByOrNull { it.length }
        if (elision != null) {
            val head = AnalyzedToken(text.take(elision.length), elision, TokenKind.WORD, profile.roleOf(elision) ?: TokenRole.CONTENT, startsSentence = startsSentence)
            return listOf(head) + wordTokens(text.drop(elision.length), profile, startsSentence = false)
        }
        val role = classify(lower, profile)
        val exclusion = exclusionFor(text, lower, role, profile, startsSentence)
        return listOf(AnalyzedToken(text, lower, TokenKind.WORD, role, exclusion, startsSentence))
    }

    private fun classify(lower: String, profile: LanguageProfile): TokenRole {
        profile.roleOf(lower)?.let { return it }
        if (profile.shortHiraganaIsFunction && lower.length <= 2 && lower.all { it in HIRAGANA }) return TokenRole.PARTICLE
        return TokenRole.CONTENT
    }

    private fun exclusionFor(text: String, lower: String, role: TokenRole, profile: LanguageProfile, startsSentence: Boolean): Exclusion {
        if (text.any { it.isDigit() }) return Exclusion.NUMBER
        if (role != TokenRole.CONTENT) return Exclusion.NONE
        if (profile.script == Script.CJK) {
            val single = lower.length == 1
            return if (single && (lower[0] in HIRAGANA || lower[0] in KATAKANA)) Exclusion.TOO_SHORT else Exclusion.NONE
        }
        if (lower.length < 2) return Exclusion.TOO_SHORT
        // Short all-capitals words are acronyms (PDF, SMS); longer ones are just someone shouting.
        if (text.length in 2..4 && text.all { it.isUpperCase() || !it.isLetter() }) return Exclusion.ACRONYM
        // A capital first letter followed by lower case in mid-sentence is probably a name (Marie); ALL CAPS is not.
        if (profile.capitalisedIsProperNoun && !startsSentence && text.first().isUpperCase() && text.drop(1).any { it.isLowerCase() }) return Exclusion.PROPER_NOUN
        return Exclusion.NONE
    }

    private companion object {
        val LINK = Regex("""(https?://\S+|www\.\S+|\S+@\S+\.\S+)""")
        const val SENTENCE_END = ".!?。！？…\n"
        val HIRAGANA = 'ぁ'..'ゟ'
        val KATAKANA = '゠'..'ヿ'
    }
}
