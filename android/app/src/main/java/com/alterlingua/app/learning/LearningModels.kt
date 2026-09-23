package com.alterlingua.app.learning

/** Personal Language Map learning states (CLAUDE.md section 14). The UI calls UNKNOWN "New". */
enum class MasteryStatus { UNKNOWN, LEARNING, FAMILIAR, MASTERED }

/** How much translation help the user wants (CLAUDE.md section 13). Behaviour arrives in later milestones. */
enum class AssistanceMode { FULL_SUPPORT, ADAPTIVE, ON_DEMAND }

/** Counts per learning state. Placeholder values only in this milestone. */
data class LanguageMapSummary(
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int,
) {
    val total: Int get() = newCount + learningCount + familiarCount + masteredCount

    fun countOf(status: MasteryStatus): Int = when (status) {
        MasteryStatus.UNKNOWN -> newCount
        MasteryStatus.LEARNING -> learningCount
        MasteryStatus.FAMILIAR -> familiarCount
        MasteryStatus.MASTERED -> masteredCount
    }
}

/** One word or phrase in the Personal Language Map. */
data class WordEntry(
    val term: String,
    val phonetic: String,
    val meaning: String,
    val status: MasteryStatus,
    val encounters: Int,
)

/** One item of the daily micro-lesson. */
data class LessonItem(
    val term: String,
    val phonetic: String,
    val kind: String,
    val meaning: String,
    val status: MasteryStatus,
    val exampleTarget: String,
    val exampleNative: String,
    val note: String,
)
