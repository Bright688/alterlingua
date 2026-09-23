package com.alterlingua.app.learning.engine

/** What kind of learning unit a candidate is. */
enum class UnitType { WORD, PHRASE, EXPRESSION }

/** Why a candidate looks useful (transparent, so the scoring can be understood and later tuned). */
enum class UsefulnessSignal { CONTENT_WORD, LONGER_WORD, PHRASE_CHUNK, PREPOSITION_PHRASE, KNOWN_EXPRESSION }

/** How useful a candidate looks, from 0 to 1, and why. A heuristic for now; later it is combined with what the user knows. */
data class Usefulness(val score: Double, val signals: List<UsefulnessSignal>)

/**
 * What identifies a unit for counting: its language, its normalized form and its type. The same word in another
 * language is a different unit (CLAUDE.md 6.13): knowing `devis` in Français says nothing about Español.
 */
data class UnitKey(val language: String, val normalized: String, val type: UnitType)

/** How often a unit was met. One event counts once; the exposure store adds them up. */
data class Exposure(val count: Int, val firstSeenMillis: Long, val lastSeenMillis: Long)

/**
 * One candidate learning unit taken from a message, with everything later steps need and nothing more:
 * - [surface]: the unit as it appeared (a word or a short fragment, never a whole message);
 * - [normalized]: the form used for counting and lookup (lower case, Unicode-normalized, elisions handled);
 * - [type], [learningLanguage] (the language the unit is in) and [meaningLanguage] (the user's own language, in which
 *   the meaning will be given);
 * - [meaning] and [lemma]: empty for now; filled by a dictionary or model later;
 * - [usefulness] and [exposure].
 */
data class LearningCandidate(
    val surface: String,
    val normalized: String,
    val type: UnitType,
    val learningLanguage: String,
    val meaningLanguage: String,
    val usefulness: Usefulness,
    val exposure: Exposure,
    val meaning: String? = null,
    val lemma: String? = null,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "LearningCandidate(redacted)"
    val key: UnitKey get() = UnitKey(learningLanguage, normalized, type)
}

/** What kind of translation interaction a learning event came from. */
enum class InteractionKind { OUTGOING_TEXT, OUTGOING_VOICE, INCOMING_MESSAGE, INCOMING_VOICE }

/**
 * The result of one translation interaction: the candidate units found in the learning language, and nothing of the
 * message around them. There is no field for the message text, on purpose.
 */
data class LearningEvent(
    val id: String,
    val atMillis: Long,
    val kind: InteractionKind,
    val learningLanguage: String,
    val meaningLanguage: String,
    val candidates: List<LearningCandidate>,
)

/**
 * Text that was just translated, handed to the learning pipeline. It exists only in memory, only while the pipeline
 * runs, and is never stored or logged. [textLanguage] is the language the [text] is in.
 */
data class TranslationInteraction(val kind: InteractionKind, val text: String, val textLanguage: String) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslationInteraction(redacted)"
}

/** Something that wants to hear about translation interactions. The default does nothing. */
fun interface LearningRecorder {
    fun record(interaction: TranslationInteraction)

    companion object {
        val None = LearningRecorder { }
    }
}
