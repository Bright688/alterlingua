package com.alterlingua.app.learning.progress

/** How many map items of one language were in each state at the end of a day. */
data class MasterySnapshot(val encountered: Int, val learning: Int, val familiar: Int, val mastered: Int)

/**
 * One language's counts for one local day. Only numbers: no message, word, transcript or audio is kept here.
 *
 * - [wordsMet]: content words met in real communication (translated, dictated, received) that day.
 * - [wordsAssisted]: those words that were not mastered when met (the ones translation still had to cover).
 * - [newWords]: words met for the first time.
 * - [helpRequests], [lessonCards], [practiceTries], [practiceGood]: what the learner did that day.
 * - [snapshot]: the map's state counts at the last change of the day, if any.
 */
data class DailyActivity(
    /** Local date as `yyyy-MM-dd`. */
    val date: String,
    val language: String,
    val wordsMet: Int = 0,
    val wordsAssisted: Int = 0,
    val newWords: Int = 0,
    val helpRequests: Int = 0,
    val lessonCards: Int = 0,
    val practiceTries: Int = 0,
    val practiceGood: Int = 0,
    val snapshot: MasterySnapshot? = null,
    /**
     * Translations the user actually received or used (see docs/pilot.md), by kind. A count of translations, never their text.
     */
    val translationsOutgoingText: Int = 0,
    val translationsOutgoingVoice: Int = 0,
    val translationsIncomingText: Int = 0,
    val translationsIncomingVoice: Int = 0,
    /** Upward steps in the Personal Language Map: a word that crossed UNKNOWN to LEARNING, LEARNING to FAMILIAR, FAMILIAR to MASTERED. */
    val toLearning: Int = 0,
    val toFamiliar: Int = 0,
    val toMastered: Int = 0,
    /** Daily lessons finished (the last card done). */
    val lessonsCompleted: Int = 0,
    /** Translations plus translation-help requests made while each assistance mode was selected. */
    val actionsFullSupport: Int = 0,
    val actionsAdaptive: Int = 0,
    val actionsOnDemand: Int = 0,
) {
    val translations: Int get() = translationsOutgoingText + translationsOutgoingVoice + translationsIncomingText + translationsIncomingVoice

    /** Anything the learner did or met that day. */
    val hasActivity: Boolean get() = wordsMet + helpRequests + lessonCards + practiceTries + translations > 0
}
