package com.alterlingua.app.learning.map

import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType

/**
 * One entry of a user's Personal Language Map: a word, phrase or expression in ONE language, with what is known about how
 * well the user knows it. It holds counts and a mastery result, never a message. Each language has its own entries:
 * `devis` in Français and `devis` in Español are different items (CLAUDE.md 6.13).
 */
data class LanguageMapItem(
    val language: String,
    val normalized: String,
    val type: UnitType,
    /** The unit as it was first seen (with its accents and capitals), for showing to the user. */
    val displayForm: String,
    /** What it means, in [meaningLanguage]. Empty until a dictionary or lesson supplies it. */
    val meaning: String? = null,
    val meaningLanguage: String? = null,
    val exposureCount: Int = 0,
    val helpRequests: Int = 0,
    val lessonEncounters: Int = 0,
    val correctRecognitions: Int = 0,
    val incorrectRecognitions: Int = 0,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val masteryScore: Double = 0.0,
    val masteryState: MasteryStatus = MasteryStatus.UNKNOWN,
    /** The best usefulness (0 to 1) the learning engine gave this unit; 0 if unknown (older data). Used to choose lesson items. */
    val usefulness: Double = 0.0,
    /** What kind of message it was last met in (never the message itself); null if unknown. */
    val lastContext: InteractionKind? = null,
    /** When it was last shown in a daily lesson; 0 if never. Used to space repetition. */
    val lastLessonAt: Long = 0,
    /** When the learner last asked for this unit's translation; 0 if never. A recent request means it is not mastered. */
    val lastHelpAt: Long = 0,
    /** Pronunciation practice: attempts, and the ones the speech recognizer understood. Counts only, never audio or text. */
    val pronunciationTries: Int = 0,
    val pronunciationGood: Int = 0,
) {
    val key: UnitKey get() = UnitKey(language, normalized, type)

    val evidence: MasteryEvidence
        get() = MasteryEvidence(exposureCount, lessonEncounters, correctRecognitions, incorrectRecognitions, helpRequests, lastSeen, lastHelpAt, pronunciationGood)
}
