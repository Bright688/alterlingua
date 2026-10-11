package com.alterlingua.app.learning.curriculum

/**
 * One language's progress through the standard curriculum (see CurriculumProgressService): a large, online, shared
 * word list, separate from the Personal Language Map and the conversation-derived daily lesson. Keeps no words and no
 * translations itself; those are fetched again as needed from the backend's curriculum endpoint.
 */
data class CurriculumProgress(
    /** How many words of this language's curriculum have been shown so far; also the offset for the next page. */
    val cursor: Int = 0,
    /** How many words of today's goal have been completed since [lastActiveDate]. */
    val completedToday: Int = 0,
    /** Extra words owed today because an earlier day's goal was not fully met. */
    val backlog: Int = 0,
    /** The local date [completedToday] is counted against, as ISO-8601 (e.g. "2026-10-11"); null before first use. */
    val lastActiveDate: String? = null,
) {
    /** How many words are due today: the daily goal plus anything owed from before. */
    fun goalToday(dailyGoal: Int): Int = dailyGoal + backlog

    /** How many of today's due words are still left to learn. */
    fun remainingToday(dailyGoal: Int): Int = (goalToday(dailyGoal) - completedToday).coerceAtLeast(0)
}
