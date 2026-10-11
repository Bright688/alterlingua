package com.alterlingua.app.learning.curriculum

/**
 * "The Forge": the shared curriculum, organized as Levels -> Units -> Lessons, the same for every plan (CLAUDE.md's
 * own "Final decision": one curriculum for Free/Standard/Premium; the commercial difference is assistance, not
 * content). This is a FREQUENCY-based proxy for CEFR level, not an officially validated placement: the curriculum
 * word list (CurriculumApi) is already frequency-ranked, most common first, and banding by frequency is a
 * well-established heuristic for difficulty -- but it is not the same as a linguist-reviewed, can-do-statement CEFR
 * alignment. Each language's banding can be refined independently later without changing this structure, and no
 * level is shown for a language that does not actually have that many words yet.
 */
enum class CefrLevel {
    A1, A2, B1, B2, C1, C2;

    companion object {
        /** How many words make up one unit within a level (Level -> Units -> Lessons). */
        const val WORDS_PER_UNIT = 20
    }
}

/** One CEFR-proxy level's word-index range within the shared curriculum's full word list for a language. */
data class CurriculumLevelRange(val level: CefrLevel, val startIndex: Int, val endIndex: Int) {
    val wordCount: Int get() = endIndex - startIndex
    val unitCount: Int get() = (wordCount + CefrLevel.WORDS_PER_UNIT - 1) / CefrLevel.WORDS_PER_UNIT
}

/** One unit's word-index range within its level. [unitNumber] is 1-based, in order within the level. */
data class CurriculumUnitRange(val level: CefrLevel, val unitNumber: Int, val startIndex: Int, val endIndex: Int) {
    val wordCount: Int get() = endIndex - startIndex
}

/** Derives Level/Unit ranges from a language's total curriculum word count. No backend change needed: the total is
 * already returned by the existing GET /v1/curriculum/{language} page endpoint. */
object CurriculumStructure {

    /** Every level that actually has at least one word for a curriculum of [total] words, in order. */
    fun levels(total: Int): List<CurriculumLevelRange> {
        if (total <= 0) return emptyList()
        val band = (total + CefrLevel.entries.size - 1) / CefrLevel.entries.size
        return CefrLevel.entries.mapIndexedNotNull { i, level ->
            val start = (i * band).coerceAtMost(total)
            val end = ((i + 1) * band).coerceAtMost(total)
            if (start >= end) null else CurriculumLevelRange(level, start, end)
        }
    }

    /** Every unit within [levelRange], in order. */
    fun units(levelRange: CurriculumLevelRange): List<CurriculumUnitRange> {
        val unitSize = CefrLevel.WORDS_PER_UNIT
        return (levelRange.startIndex until levelRange.endIndex step unitSize).mapIndexed { i, start ->
            CurriculumUnitRange(levelRange.level, i + 1, start, (start + unitSize).coerceAtMost(levelRange.endIndex))
        }
    }

    /** Which unit a given absolute word index (for example, the learner's current cursor) falls into, if any. */
    fun locate(total: Int, index: Int): CurriculumUnitRange? {
        val levelRange = levels(total).firstOrNull { index in it.startIndex until it.endIndex } ?: return null
        return units(levelRange).firstOrNull { index in it.startIndex until it.endIndex }
    }
}
