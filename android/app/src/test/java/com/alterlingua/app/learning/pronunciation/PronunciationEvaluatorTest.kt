package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict.CLOSE
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict.GOOD
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict.NOT_HEARD
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict.TRY_AGAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PronunciationEvaluatorTest {
    private fun verdict(expected: String, heard: String?, language: com.alterlingua.app.learning.Language = Languages.French) =
        PronunciationEvaluator.evaluate(expected, heard, language).verdict

    // ---- Latin scripts ----

    @Test fun exactMatch_isGood_ignoringCapitalsPunctuationAndAccents() {
        assertEquals(GOOD, verdict("acompte", "acompte"))
        assertEquals(GOOD, verdict("acompte", "Acompte."))
        assertEquals(GOOD, verdict("réunion", "reunion"))
        assertEquals(GOOD, verdict("avant midi", "Avant midi !"))
        assertEquals(GOOD, verdict("je vous tiens au courant", "je vous tiens au courant"))
    }

    @Test fun aNearMissOnALongerWord_isClose_notGood() {
        assertEquals(CLOSE, verdict("fournisseur", "fournisseure"))
        assertEquals(CLOSE, verdict("marchandise", "marchandize"))
        // A real but different word one letter away is still only "nearly": the check cannot tell a slip from a lookalike.
        assertEquals(CLOSE, verdict("acompte", "compte"))
    }

    @Test fun anotherWord_isTryAgain() {
        assertEquals(TRY_AGAIN, verdict("acompte", "paiement"))
        assertEquals(CLOSE, verdict("devis", "de vie")) // one letter apart once spaces are ignored
        assertEquals(TRY_AGAIN, verdict("fournisseur", "client"))
    }

    @Test fun shortUnits_areNeverCloseOnlyGoodOrNot() {
        assertEquals(TRY_AGAIN, verdict("midi", "mini"))
        assertEquals(TRY_AGAIN, verdict("oui", "non"))
    }

    @Test fun nothingHeard_isNotHeard() {
        assertEquals(NOT_HEARD, verdict("acompte", null))
        assertEquals(NOT_HEARD, verdict("acompte", ""))
        assertEquals(NOT_HEARD, verdict("acompte", "  ...  "))
    }

    @Test fun feedbackCarriesWhatWasHeard_exceptWhenNothingWas() {
        assertEquals("compte", PronunciationEvaluator.evaluate("acompte", " compte ", Languages.French).heard)
        assertNull(PronunciationEvaluator.evaluate("acompte", null, Languages.French).heard)
    }

    // ---- other languages ----

    @Test fun everyLatinLanguage_matchesItsOwnWords() {
        val words = mapOf(
            Languages.Spanish to ("presupuesto" to "Presupuesto"), Languages.German to ("Angebot" to "angebot"),
            Languages.Italian to ("preventivo" to "preventivo."), Languages.Dutch to ("offerte" to "Offerte"),
            Languages.English to ("quotation" to "quotation"),
        )
        for ((language, pair) in words) assertEquals(language.code, GOOD, verdict(pair.first, pair.second, language))
    }

    @Test fun german_umlautsAreIgnored_soARecognizersSpellingIsNotHeldAgainstTheLearner() {
        assertEquals(GOOD, verdict("Übung", "Ubung", Languages.German))
    }

    @Test fun chinese_matchesByCharacters_ignoringPunctuationAndSpaces() {
        assertEquals(GOOD, verdict("报价", "报价。", Languages.Chinese))
        assertEquals(GOOD, verdict("中午之前", "中午 之前", Languages.Chinese))
        assertEquals(TRY_AGAIN, verdict("报价", "包价", Languages.Chinese))
    }

    @Test fun japanese_matchesByCharacters_andTreatsKatakanaAsHiragana() {
        assertEquals(GOOD, verdict("みつもり", "ミツモリ", Languages.Japanese))
        assertEquals(GOOD, verdict("見積もり", "見積もり。", Languages.Japanese))
    }

    @Test fun japanese_aWordWrittenInAnotherScript_isNotMatched_andTheLearnerSeesWhatWasHeard() {
        val feedback = PronunciationEvaluator.evaluate("見積もり", "みつもり", Languages.Japanese)
        assertEquals(TRY_AGAIN, feedback.verdict)
        assertEquals("みつもり", feedback.heard)
    }

    @Test fun cjkIsNeverClose() {
        assertEquals(TRY_AGAIN, verdict("こんにちは", "こんばんは", Languages.Japanese))
    }

    // ---- the comparison itself ----

    @Test fun similarityIsSymmetricAndBounded() {
        assertEquals(1.0, PronunciationEvaluator.similarity("abc", "abc"), 0.0)
        assertEquals(0.0, PronunciationEvaluator.similarity("abc", "xyz"), 0.0)
        assertEquals(PronunciationEvaluator.similarity("fournisseur", "fournisseure"), PronunciationEvaluator.similarity("fournisseure", "fournisseur"), 0.0)
    }

    @Test fun deterministic() {
        assertEquals(PronunciationEvaluator.evaluate("acompte", "compte", Languages.French), PronunciationEvaluator.evaluate("acompte", "compte", Languages.French))
    }
}
