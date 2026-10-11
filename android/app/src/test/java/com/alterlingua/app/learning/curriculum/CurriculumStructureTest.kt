package com.alterlingua.app.learning.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumStructureTest {

    @Test
    fun levels_bandA3000WordListIntoSixRoughlyEqualLevels() {
        val levels = CurriculumStructure.levels(3000)
        assertEquals(CefrLevel.entries, levels.map { it.level })
        assertTrue(levels.all { it.wordCount == 500 })
        assertEquals(0, levels.first().startIndex)
        assertEquals(3000, levels.last().endIndex)
        // Consecutive, no gaps and no overlap.
        levels.zipWithNext().forEach { (a, b) -> assertEquals(a.endIndex, b.startIndex) }
    }

    @Test
    fun levels_withNoWords_isEmpty() {
        assertEquals(emptyList<CurriculumLevelRange>(), CurriculumStructure.levels(0))
    }

    @Test
    fun levels_withFewerWordsThanLevels_onlyShowsLevelsThatActuallyHaveWords() {
        val levels = CurriculumStructure.levels(3)
        assertEquals(3, levels.size)
        assertTrue(levels.all { it.wordCount == 1 })
        assertEquals(listOf(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1), levels.map { it.level })
    }

    @Test
    fun levels_unevenTotal_lastLevelTakesTheRemainder() {
        // 2503 / 6 = ceil to 418 per band; 5 full bands of 418 = 2090, remainder 413 in the last.
        val levels = CurriculumStructure.levels(2503)
        assertEquals(2503, levels.sumOf { it.wordCount })
        assertEquals(2503, levels.last().endIndex)
    }

    @Test
    fun units_splitALevelIntoFixedSizeChunks_lastOneMayBeShorter() {
        val level = CurriculumLevelRange(CefrLevel.A1, 0, 45)
        val units = CurriculumStructure.units(level)
        assertEquals(listOf(1, 2, 3), units.map { it.unitNumber })
        assertEquals(listOf(20, 20, 5), units.map { it.wordCount })
        assertEquals(0, units.first().startIndex)
        assertEquals(45, units.last().endIndex)
    }

    @Test
    fun unitCount_matchesTheNumberOfUnitsActuallyProduced() {
        val level = CurriculumLevelRange(CefrLevel.B2, 100, 170)
        assertEquals(CurriculumStructure.units(level).size, level.unitCount)
    }

    @Test
    fun locate_findsTheUnitContainingAGivenCursorPosition() {
        val found = CurriculumStructure.locate(3000, 510)
        assertEquals(CefrLevel.A2, found?.level)
        assertEquals(1, found?.unitNumber) // first unit of A2 (index 500-519)
        assertEquals(500, found?.startIndex)
    }

    @Test
    fun locate_atTheVeryEnd_isNull_onceEverythingHasBeenPassed() {
        assertNull(CurriculumStructure.locate(3000, 3000))
    }

    @Test
    fun locate_withNoWords_isNull() {
        assertNull(CurriculumStructure.locate(0, 0))
    }
}
