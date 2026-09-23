package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Languages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on a device or emulator: the same analysis as the unit tests, but with the REAL Android ICU dictionary that the
 * app uses for 中文 and 日本語 (the unit tests use the ICU4J copy of the same algorithm).
 */
class AndroidIcuSegmentationTest {

    private val analyzer = RuleBasedAnalyzer(cjkBreaker = AndroidIcuWordBreaker)

    private fun units(language: com.alterlingua.app.learning.Language, text: String) =
        CandidateExtractor(analyzer.profile(language)).extract(analyzer.analyze(text, language), "en", 0)

    @Test
    fun chineseWithoutSpaces_isSplitIntoWords() {
        val words = analyzer.analyze("你明天来吗？", Languages.Chinese).filter { it.kind == TokenKind.WORD }
        assertTrue("expected several words, got ${words.map { it.text }}", words.size >= 3)
        val found = units(Languages.Chinese, "你明天来吗？")
        assertTrue(found.any { it.normalized == "明天" && it.type == UnitType.WORD })
        assertTrue(found.none { "吗" in it.normalized })
    }

    @Test
    fun japaneseWithoutSpaces_isSplitIntoWords_andParticlesAreSkipped() {
        val found = units(Languages.Japanese, "正午までに見積書をお送りします。")
        assertTrue(found.any { it.normalized == "正午" && it.type == UnitType.WORD })
        assertTrue(found.any { it.normalized == "見積書" && it.type == UnitType.WORD })
        assertFalse(found.any { it.type == UnitType.WORD && it.normalized in setOf("まで", "に", "を") })
    }

    @Test
    fun frenchAndSpanishStillWork_onTheDevice() {
        assertTrue(units(Languages.French, "Je vais vous envoyer le devis avant midi.").any { it.normalized == "avant midi" })
        assertTrue(units(Languages.Spanish, "Te enviaré el presupuesto antes del mediodía.").any { it.normalized == "antes del mediodía" })
    }
}
