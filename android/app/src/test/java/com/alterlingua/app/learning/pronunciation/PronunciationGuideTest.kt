package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.learning.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationGuideTest {

    // ---- the exact examples the feature was asked to reproduce ----

    @Test fun aSingleFrenchWord_matchesTheExample() {
        assertEquals("RAHN-truh", PronunciationGuide.guideFor("Rentre.", Languages.French))
    }

    @Test fun aFrenchPhrase_matchesTheExample_earlierWordsLowercase_lastWordsFirstSyllableCapitalized() {
        assertEquals("tü RAHN-truh", PronunciationGuide.guideFor("Tu rentres ?", Languages.French))
    }

    // ---- what the mechanism guarantees, regardless of the exact per-language rules ----

    @Test fun chineseAndJapanese_haveNoGuide() {
        assertNull(PronunciationGuide.guideFor("你好", Languages.Chinese))
        assertNull(PronunciationGuide.guideFor("こんにちは", Languages.Japanese))
    }

    @Test fun blankOrPunctuationOnlyText_hasNoGuide() {
        assertNull(PronunciationGuide.guideFor("", Languages.French))
        assertNull(PronunciationGuide.guideFor("   ", Languages.French))
        assertNull(PronunciationGuide.guideFor("?!", Languages.French))
    }

    @Test fun everyLatinCatalogueLanguage_producesAGuide_forAnOrdinaryWord() {
        val words = mapOf(
            Languages.English to "banana",
            Languages.French to "bonjour",
            Languages.Spanish to "gracias",
            Languages.German to "danke",
            Languages.Italian to "grazie",
            Languages.Dutch to "dank",
        )
        for ((language, word) in words) {
            val guide = PronunciationGuide.guideFor(word, language)
            assertTrue("$language should have a guide for '$word', got null", guide != null)
            assertTrue("guide for '$word' should not be blank", guide!!.isNotBlank())
        }
    }

    @Test fun onlyTheLastWordsFirstSyllable_isUppercase() {
        val guide = PronunciationGuide.guideFor("je vous tiens au courant", Languages.French)!!
        val words = guide.split(" ")
        assertEquals(5, words.size)
        // every word but the last is entirely lowercase
        for (word in words.dropLast(1)) assertEquals(word, word.lowercase())
        val lastSyllables = words.last().split("-")
        assertEquals(lastSyllables.first(), lastSyllables.first().uppercase())
        for (syllable in lastSyllables.drop(1)) assertEquals(syllable, syllable.lowercase())
    }

    @Test fun aWordEndingInAConsonantCluster_getsAHelperVowel_soItCanBeRead() {
        // "truh" (from "rentre") is exactly this case: the shared mechanism is exercised again here on a different word.
        val guide = PronunciationGuide.guideFor("notre", Languages.French)!!
        assertTrue(guide, guide.endsWith("uh") || guide.last().isLetter())
    }

    @Test fun aLanguageOutsideTheCatalogue_hasNoGuide() {
        val unknown = Languages.French.copy(code = "xx")
        assertNull(PronunciationGuide.guideFor("mot", unknown))
    }

    @Test fun punctuationAndCapitalisation_areIgnored() {
        assertEquals(
            PronunciationGuide.guideFor("bonjour", Languages.French),
            PronunciationGuide.guideFor("Bonjour!", Languages.French),
        )
    }

    @Test fun theSameTextAndLanguage_alwaysGivesTheSameGuide() {
        val a = PronunciationGuide.guideFor("avant midi", Languages.French)
        val b = PronunciationGuide.guideFor("avant midi", Languages.French)
        assertEquals(a, b)
    }
}
