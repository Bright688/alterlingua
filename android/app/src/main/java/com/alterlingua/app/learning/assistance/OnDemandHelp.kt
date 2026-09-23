package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.Exclusion
import com.alterlingua.app.learning.engine.TokenKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.MasteryCalculator

/**
 * A stretch of text on an AlterLingua screen. [normalized] is set when the user may ask about it (a word); the pieces
 * always join back into exactly the original text.
 */
data class ReaderSegment(val text: String, val normalized: String? = null) {
    val askable: Boolean get() = normalized != null
}

/** What the user is shown after asking about a word. */
data class HelpAnswer(
    val term: String,
    /** The meaning in the user's language, or null if it could not be found right now. */
    val meaning: String?,
    /** The word's state in the Personal Language Map after this request. */
    val state: MasteryStatus,
)

/**
 * On-demand assistance on screens AlterLingua owns: the text stays in the language being learned, and a meaning is given
 * only when the user asks for one word.
 *
 * Asking is itself learning evidence (CLAUDE.md 13): it is recorded in the Personal Language Map as a translation-help
 * request, which lowers the word's mastery (and holds a mastered word back from staying untranslated in Adaptive mode), and
 * puts the word among the candidates for a daily lesson. The word is looked up in the user's own language map only.
 *
 * Only what the user selects is looked up (one word, never the text around it), and nothing of the text is stored.
 */
class OnDemandHelp(
    private val analyzers: AnalyzerRegistry,
    private val map: LanguageMapService,
    private val meanings: MeaningProvider,
    private val languages: suspend () -> LessonLanguages,
    private val calculator: MasteryCalculator = MasteryCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The language whose map is used, for saying it on screen. */
    suspend fun learningLanguage(): Language = languages().learning

    /** Cuts [text] into pieces, marking the words the user can ask about. An unsupported language gives no askable words. */
    suspend fun read(text: String): List<ReaderSegment> {
        val learning = languages().learning
        val analyzer = analyzers.forLanguage(learning) ?: return listOf(ReaderSegment(text))
        val segments = mutableListOf<ReaderSegment>()
        var cursor = 0
        for (token in analyzer.analyze(text, learning)) {
            if (token.kind != TokenKind.WORD || token.exclusion == Exclusion.NUMBER) continue
            val at = text.indexOf(token.text, cursor)
            if (at < 0) continue // the analyzer changed the spelling (for example width forms): leave it as plain text
            if (at > cursor) segments += ReaderSegment(text.substring(cursor, at))
            segments += ReaderSegment(token.text, token.normalized)
            cursor = at + token.text.length
        }
        if (cursor < text.length) segments += ReaderSegment(text.substring(cursor))
        return segments
    }

    /**
     * The user asked what [segment] means. Records the request, then answers with the meaning the map already holds or,
     * if it has none, one fetched for this word alone (and kept, so asking again needs no connection).
     * [record] is false for a repeat question about the same word in the same text, so one reading is one signal.
     */
    suspend fun request(segment: ReaderSegment, record: Boolean = true): HelpAnswer {
        val normalized = requireNotNull(segment.normalized) { "not an askable word" }
        val langs = languages()
        val key = UnitKey(langs.learning.code, normalized, UnitType.WORD)
        if (record) map.recordHelpRequest(key, segment.text, clock())

        var item = map.item(key)
        var meaning = item?.takeIf { it.meaningLanguage == langs.native.code }?.meaning?.takeIf { it.isNotBlank() }
        if (meaning == null) {
            meaning = meanings.meaningOf(segment.text, langs.learning, langs.native)
            if (meaning != null) item = map.setMeaning(key, meaning, langs.native.code) ?: item
        }
        val state = item?.let { calculator.evaluate(it.evidence, clock()).state } ?: MasteryStatus.UNKNOWN
        return HelpAnswer(segment.text, meaning, state)
    }
}
