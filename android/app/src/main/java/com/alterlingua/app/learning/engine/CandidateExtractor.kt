package com.alterlingua.app.learning.engine

/**
 * Turns analysed tokens into candidate learning units: words, phrases and expressions.
 *
 * - WORD: a content word (not a function word, number, name or acronym).
 * - PHRASE: a chunk built around content words with the function words that belong to it: in French or Español a
 *   pronoun or preposition in front (`je vais vous envoyer`, `avant midi`, `antes del mediodía`), in 日本語 the verb
 *   ending after it (`来ます`).
 * - EXPRESSION: a fixed expression from the language's list (`au courant`, `por favor`, `ありがとうございます`).
 *
 * Privacy (CLAUDE.md 25): candidates are short (at most six words), each type is capped per message, names, numbers,
 * acronyms, addresses and links never become candidates, and nothing that covers a whole message longer than three words
 * is offered. So a set of candidates is a handful of study items, not a copy of the message.
 */
class CandidateExtractor(private val profile: LanguageProfile) {

    fun extract(tokens: List<AnalyzedToken>, meaningLanguage: String, nowMillis: Long): List<LearningCandidate> {
        val wordCount = tokens.count { it.kind == TokenKind.WORD }
        val found = LinkedHashMap<UnitKey, LearningCandidate>()

        fun add(type: UnitType, used: List<AnalyzedToken>, usefulness: Usefulness) {
            val normalized = profile.join(used.map { it.normalized })
            val surface = profile.join(used.map { it.text })
            if (surface.length > MAX_SURFACE_CHARS) return
            // Never a whole message. (A fixed expression comes from a public list, so it cannot reveal anything private.)
            if (type != UnitType.EXPRESSION && wordCount > 3 && used.size >= wordCount) return
            // A fixed expression already covers the same words: do not list them again as a phrase or word.
            if (type != UnitType.EXPRESSION && found.containsKey(UnitKey(profile.code, normalized, UnitType.EXPRESSION))) return
            val candidate = LearningCandidate(
                surface = surface,
                normalized = normalized,
                type = type,
                learningLanguage = profile.code,
                meaningLanguage = meaningLanguage,
                usefulness = usefulness,
                exposure = Exposure(1, nowMillis, nowMillis),
            )
            found.putIfAbsent(candidate.key, candidate)
        }

        for (run in runs(tokens)) {
            for (expression in expressionsIn(run)) add(UnitType.EXPRESSION, expression, expressionUsefulness(expression))
            for (phrase in chunksOf(run).mapNotNull(::trim)) add(UnitType.PHRASE, phrase, phraseUsefulness(phrase))
            if (profile.functionWordsFollow) for (group in verbGroups(run)) add(UnitType.PHRASE, group, phraseUsefulness(group))
            for (token in run) if (token.role == TokenRole.CONTENT) add(UnitType.WORD, listOf(token), wordUsefulness(token))
        }
        return select(found.values.toList())
    }

    /** Stretches of usable words, broken by punctuation and by names, numbers and other excluded tokens. */
    private fun runs(tokens: List<AnalyzedToken>): List<List<AnalyzedToken>> {
        val runs = mutableListOf<List<AnalyzedToken>>()
        var current = mutableListOf<AnalyzedToken>()
        for (token in tokens) {
            if (token.kind == TokenKind.WORD && token.exclusion == Exclusion.NONE) {
                current += token
            } else if (current.isNotEmpty()) {
                runs += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) runs += current
        return if (profile.script == Script.CJK) runs.map(::mergeSuffixes) else runs
    }

    /**
     * ICU splits some compounds (`見積` + `書`, `报价` + `单`). A single character that is a common word ending
     * is joined back to the content word before it.
     */
    private fun mergeSuffixes(run: List<AnalyzedToken>): List<AnalyzedToken> {
        val out = mutableListOf<AnalyzedToken>()
        for (token in run) {
            val previous = out.lastOrNull()
            if (previous != null && previous.role == TokenRole.CONTENT && token.role == TokenRole.CONTENT && token.text.length == 1 && token.text[0] in WORD_ENDINGS) {
                out[out.lastIndex] = previous.copy(text = previous.text + token.text, normalized = previous.normalized + token.normalized)
            } else {
                out += token
            }
        }
        return out
    }

    private fun expressionsIn(run: List<AnalyzedToken>): List<List<AnalyzedToken>> {
        val out = mutableListOf<List<AnalyzedToken>>()
        for (start in run.indices) {
            for (length in 1..MAX_TOKENS) {
                if (start + length > run.size) break
                val span = run.subList(start, start + length)
                if (profile.isExpression(profile.join(span.map { it.normalized }))) out += span.toList()
            }
        }
        return out
    }

    /** Function words in front of a content word, then the content words that follow (French, Español, 中文...). */
    private fun chunksOf(run: List<AnalyzedToken>): List<List<AnalyzedToken>> {
        if (profile.functionWordsFollow) return emptyList()
        val chunks = mutableListOf<List<AnalyzedToken>>()
        var current = mutableListOf<AnalyzedToken>()
        var hasContent = false
        for (token in run) {
            if (token.role == TokenRole.CONTENT) {
                current += token
                hasContent = true
            } else {
                if (hasContent) {
                    chunks += current
                    current = mutableListOf()
                    hasContent = false
                }
                current += token
            }
        }
        if (hasContent) chunks += current
        return chunks
    }

    /** Drops what cannot start a phrase (articles, conjunctions, auxiliaries...) and keeps 2 to 6 words. */
    private fun trim(chunk: List<AnalyzedToken>): List<AnalyzedToken>? {
        var tokens = chunk
        tokens = tokens.dropWhile { it.role !in PHRASE_STARTS }
        if (tokens.isEmpty()) return null
        if (tokens.first().role != TokenRole.PREPOSITION) {
            // An article inside starts a noun phrase ("you the quotation"): keep only what follows it.
            val article = tokens.indexOfFirst { it.role == TokenRole.ARTICLE }
            if (article >= 0) tokens = tokens.drop(article + 1).dropWhile { it.role !in PHRASE_STARTS }
        }
        return tokens.takeIf { it.size in 2..MAX_TOKENS && it.last().role == TokenRole.CONTENT }
    }

    /** 日本語: content words followed by verb endings, and the last content word with its ending (来ます). */
    private fun verbGroups(run: List<AnalyzedToken>): List<List<AnalyzedToken>> {
        val groups = mutableListOf<List<AnalyzedToken>>()
        var content = mutableListOf<AnalyzedToken>()
        var endings = mutableListOf<AnalyzedToken>()
        fun flush() {
            if (content.isNotEmpty() && endings.isNotEmpty()) {
                val whole = content + endings
                if (whole.size <= MAX_TOKENS) groups += whole.toList()
                if (content.size > 1) groups += (listOf(content.last()) + endings)
            }
            content = mutableListOf()
            endings = mutableListOf()
        }
        for (token in run) {
            when (token.role) {
                TokenRole.CONTENT -> {
                    if (endings.isNotEmpty()) flush()
                    content += token
                }
                TokenRole.AUXILIARY -> if (content.isNotEmpty()) endings += token
                else -> flush()
            }
        }
        flush()
        return groups
    }

    // ---- how useful a candidate looks ----

    private fun wordUsefulness(token: AnalyzedToken): Usefulness {
        val signals = mutableListOf(UsefulnessSignal.CONTENT_WORD)
        var score = 0.30
        val longer = if (profile.script == Script.CJK) token.text.length >= 2 else token.text.length >= 5
        if (longer) {
            score += 0.10
            signals += UsefulnessSignal.LONGER_WORD
        }
        return Usefulness(score, signals)
    }

    private fun phraseUsefulness(tokens: List<AnalyzedToken>): Usefulness {
        val signals = mutableListOf(UsefulnessSignal.PHRASE_CHUNK)
        var score = 0.50 + 0.05 * (tokens.size - 2).coerceIn(0, 3)
        if (tokens.first().role == TokenRole.PREPOSITION) {
            score += 0.10
            signals += UsefulnessSignal.PREPOSITION_PHRASE
        }
        return Usefulness(score.coerceAtMost(0.80), signals)
    }

    private fun expressionUsefulness(tokens: List<AnalyzedToken>) =
        Usefulness(if (tokens.size >= 2) 0.90 else 0.85, listOf(UsefulnessSignal.KNOWN_EXPRESSION))

    /** Keeps the best few of each type, then lists the most useful first. */
    private fun select(all: List<LearningCandidate>): List<LearningCandidate> {
        val perType = mapOf(UnitType.EXPRESSION to MAX_EXPRESSIONS, UnitType.PHRASE to MAX_PHRASES, UnitType.WORD to MAX_WORDS)
        return all.groupBy { it.type }
            .flatMap { (type, candidates) -> candidates.sortedByDescending { it.usefulness.score }.take(perType.getValue(type)) }
            .sortedByDescending { it.usefulness.score }
    }

    private companion object {
        val PHRASE_STARTS = setOf(TokenRole.PRONOUN, TokenRole.PREPOSITION, TokenRole.ADVERB, TokenRole.CONTENT)
        const val MAX_TOKENS = 6
        const val WORD_ENDINGS = "書者員的性化家部課所場会社室用費料量型系単单价费员站馆院"
        const val MAX_SURFACE_CHARS = 60
        const val MAX_WORDS = 5
        const val MAX_PHRASES = 4
        const val MAX_EXPRESSIONS = 3
    }
}
