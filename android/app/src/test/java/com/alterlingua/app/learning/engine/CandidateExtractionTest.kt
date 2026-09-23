package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extraction across the initial languages, using the real ICU dictionary segmentation for 中文 and 日本語. */
class CandidateExtractionTest {

    private val analyzer = RuleBasedAnalyzer(cjkBreaker = Icu4jWordBreaker)

    private fun extract(language: Language, text: String, native: String = "en"): List<LearningCandidate> =
        CandidateExtractor(analyzer.profile(language)).extract(analyzer.analyze(text, language), native, nowMillis = 1_000)

    private fun List<LearningCandidate>.of(type: UnitType) = filter { it.type == type }.map { it.normalized }.toSet()

    // ---- the examples from the brief ----

    @Test
    fun francais_devisAvantMidi() {
        val units = extract(Languages.French, "Je vais vous envoyer le devis avant midi.")
        assertTrue(units.of(UnitType.WORD).containsAll(setOf("envoyer", "devis")))
        assertTrue(units.of(UnitType.PHRASE).containsAll(setOf("avant midi", "je vais vous envoyer")))
        // Function words are not offered as words to learn.
        assertTrue(units.of(UnitType.WORD).none { it in setOf("je", "vais", "vous", "le", "avant") })
    }

    @Test
    fun espanol_presupuestoAntesDelMediodia() {
        val units = extract(Languages.Spanish, "Te enviaré el presupuesto antes del mediodía.")
        assertTrue("presupuesto" in units.of(UnitType.WORD))
        assertTrue(units.of(UnitType.PHRASE).containsAll(setOf("antes del mediodía", "te enviaré")))
    }

    @Test
    fun deutsch_capitalisedNounsAreWords_andFixedExpressionsAreRecognised() {
        val units = extract(Languages.German, "Ich schicke Ihnen das Angebot vor Mittag. Ich halte dich auf dem Laufenden.")
        assertTrue("angebot" in units.of(UnitType.WORD)) // German capitalises every noun: that is not a name
        assertTrue(units.of(UnitType.EXPRESSION).containsAll(setOf("auf dem laufenden", "vor mittag")))
        assertEquals(1, units.count { it.normalized == "vor mittag" }) // listed once, as an expression, not again as a phrase
    }

    @Test
    fun italiano_and_nederlands_and_english() {
        assertTrue("preventivo" in extract(Languages.Italian, "Le invierò il preventivo prima di mezzogiorno.").of(UnitType.WORD))
        assertTrue("prima di mezzogiorno" in extract(Languages.Italian, "Le invierò il preventivo prima di mezzogiorno.").of(UnitType.PHRASE))
        val dutch = extract(Languages.Dutch, "Ik stuur u de offerte vóór de middag.")
        assertTrue("offerte" in dutch.of(UnitType.WORD))
        assertTrue("vóór de middag" in dutch.of(UnitType.PHRASE))
        val english = extract(Languages.English, "I'll send you the quotation before noon.")
        assertTrue("quotation" in english.of(UnitType.WORD))
        assertTrue("before noon" in english.of(UnitType.PHRASE))
    }

    // ---- writing systems without spaces ----

    @Test
    fun chinese_isSegmentedWithADictionary_notBySpaces() {
        val text = "你明天来吗？"
        val tokens = analyzer.analyze(text, Languages.Chinese).filter { it.kind == TokenKind.WORD }
        assertFalse(text.contains(' '))
        assertTrue("no spaces, but several words: $tokens", tokens.size >= 3)

        val units = extract(Languages.Chinese, text)
        assertTrue("明天" in units.of(UnitType.WORD))
        assertTrue("来" in units.of(UnitType.WORD))
        assertTrue("你明天来" in units.of(UnitType.PHRASE))
        assertTrue(units.none { "吗" in it.normalized }) // a question particle is not something to learn
        assertTrue(units.none { it.normalized == "你" })
    }

    @Test
    fun chinese_compoundsAreRejoined_andFunctionWordsSkipped() {
        val units = extract(Languages.Chinese, "我会在中午前把报价单发给您。")
        assertTrue("报价单" in units.of(UnitType.WORD))
        assertTrue(units.of(UnitType.WORD).none { it in setOf("我", "会", "在", "把", "给", "您") })
    }

    @Test
    fun japanese_isSegmentedWithADictionary_notBySpaces() {
        val text = "正午までに見積書をお送りします。"
        assertFalse(text.contains(' '))
        val units = extract(Languages.Japanese, text)
        assertTrue(units.of(UnitType.WORD).containsAll(setOf("正午", "見積書")))
        assertTrue("お送りします" in units.of(UnitType.PHRASE))
        // Particles and the polite ending are not offered as words.
        assertTrue(units.of(UnitType.WORD).none { it in setOf("まで", "に", "を", "ます", "し") })
    }

    @Test
    fun japanese_verbWithItsEnding_isAPhrase() {
        val units = extract(Languages.Japanese, "明日来ますか？")
        assertTrue("明日" in units.of(UnitType.WORD))
        assertTrue(units.of(UnitType.PHRASE).containsAll(setOf("明日来ます", "来ます")))
        assertTrue(units.none { "か" == it.normalized })
    }

    @Test
    fun fixedExpressionsAreFoundInChineseAndJapanese() {
        assertTrue("谢谢" in extract(Languages.Chinese, "谢谢！").of(UnitType.EXPRESSION))
        assertTrue("ありがとうございます" in extract(Languages.Japanese, "ありがとうございます。").of(UnitType.EXPRESSION))
        assertTrue("没问题" in extract(Languages.Chinese, "明天可以，没问题。").of(UnitType.EXPRESSION))
    }

    @Test
    fun withoutADictionary_chineseAndJapaneseAreUnsupported_notSplitBadly() {
        val spaceOnly = RuleBasedAnalyzer(cjkBreaker = null)
        assertFalse(spaceOnly.supports(Languages.Chinese))
        assertFalse(spaceOnly.supports(Languages.Japanese))
        assertTrue(spaceOnly.supports(Languages.French))
        assertTrue(spaceOnly.analyze("你明天来吗？", Languages.Chinese).isEmpty())
        assertNull(AnalyzerRegistry(listOf(spaceOnly)).forLanguage(Languages.Japanese))
    }

    @Test
    fun everyInitialLanguageHasAnAnalyzer() {
        val registry = AnalyzerRegistry(listOf(analyzer))
        for (language in Languages.supported) assertTrue(language.code, registry.supports(language))
    }

    // ---- language-aware details ----

    @Test
    fun elisions_areSplit_soTheWordItselfIsFound() {
        val units = extract(Languages.French, "J'envoie l'offre d'abord.")
        assertTrue(units.of(UnitType.WORD).containsAll(setOf("envoie", "offre")))
        assertTrue(units.of(UnitType.WORD).none { "'" in it })
    }

    @Test
    fun theSameWordInDifferentCaseOrUnicodeFormHasOneNormalizedForm() {
        val plain = extract(Languages.French, "un DEVIS clair").first { it.type == UnitType.WORD && it.normalized == "devis" }
        val lower = extract(Languages.French, "un devis clair").first { it.type == UnitType.WORD && it.normalized == "devis" }
        assertEquals(plain.key, lower.key)
        val composed = extract(Languages.Spanish, "el mediod\u00EDa").first { it.type == UnitType.WORD } // í as one character
        val decomposed = extract(Languages.Spanish, "el mediodi\u0301a").first { it.type == UnitType.WORD } // i + combining accent
        assertEquals(composed.normalized, decomposed.normalized)
    }

    // ---- what a candidate carries ----

    @Test
    fun aCandidateCarriesWhatLaterStepsNeed() {
        val devis = extract(Languages.French, "Je vais vous envoyer le devis avant midi.", native = "es").first { it.normalized == "devis" }
        assertEquals("devis", devis.surface)
        assertEquals(UnitType.WORD, devis.type)
        assertEquals("fr", devis.learningLanguage) // the language the unit is in
        assertEquals("es", devis.meaningLanguage) // the user's own language, for the meaning to come
        assertNull(devis.meaning)
        assertNull(devis.lemma)
        assertEquals(Exposure(1, 1_000, 1_000), devis.exposure)
        assertEquals(UnitKey("fr", "devis", UnitType.WORD), devis.key)
        assertTrue(devis.usefulness.score in 0.0..1.0)
        assertTrue(devis.usefulness.signals.isNotEmpty())
    }

    @Test
    fun expressionsScoreAboveAPhrase_whichScoresAboveAPlainWord() {
        val units = extract(Languages.German, "Ich halte dich auf dem Laufenden. Ich schicke Ihnen das Angebot.")
        val expression = units.first { it.type == UnitType.EXPRESSION }.usefulness.score
        val phrase = units.first { it.type == UnitType.PHRASE }.usefulness.score
        val word = units.first { it.type == UnitType.WORD }.usefulness.score
        assertTrue("$expression > $phrase > $word", expression > phrase && phrase > word)
    }

    @Test
    fun theSameWordInAnotherLanguageIsAnotherUnit() {
        val french = extract(Languages.French, "un devis clair").first { it.normalized == "devis" }
        val spanish = extract(Languages.Spanish, "un devis claro").first { it.normalized == "devis" }
        assertTrue(french.key != spanish.key)
    }

    // ---- privacy: units, not messages ----

    @Test
    fun namesNumbersAcronymsAndLinksNeverBecomeCandidates() {
        val units = extract(Languages.French, "Je pense que Marie enverra la facture XYZ-9917 à 14h30 via https://example.com/x et user@example.com demain.")
        val all = units.joinToString(" ") { it.surface.lowercase() }
        for (private in listOf("marie", "xyz", "9917", "14h30", "example", "user@", "https")) {
            assertFalse("$private leaked into: $all", private in all)
        }
        assertTrue("facture" in units.of(UnitType.WORD))
    }

    @Test
    fun aLongMessageYieldsOnlyAFewShortUnits() {
        val long = "Je vais vous envoyer le devis avant midi et je vous tiens au courant demain matin si le client accepte la proposition " +
            "de la semaine prochaine pour le projet de la nouvelle usine dans la région parisienne avec toutes les options."
        val units = extract(Languages.French, long)
        assertTrue(units.count { it.type == UnitType.WORD } <= 5)
        assertTrue(units.count { it.type == UnitType.PHRASE } <= 4)
        assertTrue(units.count { it.type == UnitType.EXPRESSION } <= 3)
        for (unit in units) {
            assertTrue(unit.surface.length <= 60)
            assertTrue(unit.surface.split(' ').size <= 6)
            assertFalse(unit.surface.equals(long, ignoreCase = true))
        }
    }

    @Test
    fun aWholeMessageIsNeverACandidate() {
        val text = "Merci beaucoup pour tout"
        val units = extract(Languages.French, text)
        assertTrue(units.none { it.surface.equals(text, ignoreCase = true) })
    }

    @Test
    fun theCandidateTypesHaveNoFieldThatCouldHoldTheMessage() {
        for (type in listOf(LearningCandidate::class.java, LearningEvent::class.java)) {
            val names = type.declaredFields.map { it.name.lowercase() }
            assertTrue("$type has a message-like field: $names", names.none { it in setOf("text", "message", "body", "content", "original", "sentence") })
        }
    }

    // ---- nothing to learn ----

    @Test
    fun emptyEmojiAndPunctuationOnlyTextGivesNothing() {
        for (text in listOf("", "   ", "!!!", "😀😀", "12:30", "...")) {
            assertTrue("'$text'", extract(Languages.French, text).isEmpty())
        }
    }
}
