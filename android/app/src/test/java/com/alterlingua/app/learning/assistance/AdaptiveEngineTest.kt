package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.Icu4jWordBreaker
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.Script
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.map.LanguageMapItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEngineTest {

    private val now = 10_000L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000
    private val engine = AdaptiveEngine(AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = Icu4jWordBreaker))))

    /** A map for the test: what the user knows, by language. Evidence is set so the calculator gives the named state. */
    private class TestMap {
        val items = HashMap<UnitKey, LanguageMapItem>()

        fun put(language: String, text: String, state: MasteryStatus, meaning: String? = null, lastSeen: Long = 0, type: UnitType = UnitType.WORD, meaningLanguage: String = "en") {
            val base = LanguageMapItem(language, text, type, text, meaning, meaning?.let { meaningLanguage }, lastSeen = lastSeen)
            items[UnitKey(language, text, type)] = when (state) {
                MasteryStatus.MASTERED -> base.copy(exposureCount = 12, lessonEncounters = 4, correctRecognitions = 5)
                MasteryStatus.FAMILIAR -> base.copy(exposureCount = 12, lessonEncounters = 4, correctRecognitions = 2)
                MasteryStatus.LEARNING -> base.copy(exposureCount = 20, lessonEncounters = 2)
                MasteryStatus.UNKNOWN -> base.copy(exposureCount = 1)
            }
        }

        suspend fun lookup(key: UnitKey) = items[key]
    }

    private suspend fun decide(
        map: TestMap, text: String, language: Language, textLanguage: Language = language,
        engine: AdaptiveEngine = this.engine,
    ) = engine.decide(text, textLanguage, language, Languages.English, now, map::lookup)

    private val frenchSentence = "Je vais vous envoyer le devis avant midi."

    private fun TestMap.french(envoyer: MasteryStatus, devis: MasteryStatus, midi: MasteryStatus, seen: Long = now) {
        put("fr", "envoyer", envoyer, "to send", seen)
        put("fr", "devis", devis, "quotation", seen)
        put("fr", "midi", midi, "noon", seen)
    }

    // ---- Français, the worked example ----

    @Test
    fun beginner_nothingKnown_getsFullTranslation_andEveryWordIsAssisted() = runTest {
        val d = decide(TestMap(), frenchSentence, Languages.French)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertEquals(listOf("envoyer", "devis", "midi"), d.units.map { it.normalized })
        assertTrue(d.units.all { it.verdict == UnitVerdict.ASSIST && it.state == MasteryStatus.UNKNOWN })
        assertEquals(FallbackReason.TOO_LITTLE_KNOWN, d.fallback)
    }

    @Test
    fun assistanceReducesStepByStep_asMoreUnitsAreMastered() = runTest {
        val levels = listOf(
            listOf(MasteryStatus.UNKNOWN, MasteryStatus.UNKNOWN, MasteryStatus.UNKNOWN),
            listOf(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.UNKNOWN),
            listOf(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED),
            listOf(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED),
        ).map { (a, b, c) ->
            val map = TestMap().also { it.french(a, b, c) }
            decide(map, frenchSentence, Languages.French).level
        }
        assertEquals(listOf(AdaptiveLevel.FULL_TRANSLATION, AdaptiveLevel.FULL_TRANSLATION, AdaptiveLevel.PARTIAL, AdaptiveLevel.KEEP_ORIGINAL), levels)
    }

    @Test
    fun partial_keepsTheFrenchAndGlossesOnlyWhatIsNotMastered() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        val d = decide(map, frenchSentence, Languages.French)
        assertEquals(AdaptiveLevel.PARTIAL, d.level)
        assertEquals(listOf("devis"), d.assisted.map { it.normalized })
        assertEquals(listOf("envoyer", "midi"), d.kept.map { it.normalized })
        assertEquals("Je vais vous envoyer le devis (quotation) avant midi.", AdaptivePresentation.plainText(d, Script.LATIN))
    }

    @Test
    fun everythingMastered_leavesTheMessageAsItIs() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED) }
        val d = decide(map, frenchSentence, Languages.French)
        assertEquals(AdaptiveLevel.KEEP_ORIGINAL, d.level)
        assertEquals(frenchSentence, AdaptivePresentation.plainText(d, Script.LATIN))
    }

    // ---- what counts as knowing ----

    @Test
    fun familiarIsNotEnough_byDefault_butACustomThresholdCanAcceptIt() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.FAMILIAR, MasteryStatus.FAMILIAR, MasteryStatus.FAMILIAR) }
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, decide(map, frenchSentence, Languages.French).level)
        val lenient = AdaptiveEngine(AnalyzerRegistry(listOf(RuleBasedAnalyzer())), config = AdaptiveConfig(keepAt = MasteryStatus.FAMILIAR))
        assertEquals(AdaptiveLevel.KEEP_ORIGINAL, decide(map, frenchSentence, Languages.French, engine = lenient).level)
    }

    @Test
    fun meetingAWordManyTimes_provesNothing() = runTest {
        val map = TestMap()
        for (w in listOf("envoyer", "devis", "midi")) {
            map.items[UnitKey("fr", w, UnitType.WORD)] = LanguageMapItem("fr", w, UnitType.WORD, w, "x", "en", exposureCount = 1000, lastSeen = now)
        }
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, decide(map, frenchSentence, Languages.French).level)
    }

    @Test
    fun aMasteredWordUnseenForLong_losesItsMastery_andGetsHelpAgain() = runTest {
        val fresh = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED) }
        assertEquals(AdaptiveLevel.KEEP_ORIGINAL, decide(fresh, frenchSentence, Languages.French).level)
        val stale = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED, seen = now - 200 * day) }
        val d = decide(stale, frenchSentence, Languages.French)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertTrue(d.units.none { it.state == MasteryStatus.MASTERED })
    }

    @Test
    fun aWordNotInTheMap_isNeverAssumedKnown() = runTest {
        val map = TestMap()
        map.put("fr", "envoyer", MasteryStatus.MASTERED, "to send", now)
        map.put("fr", "midi", MasteryStatus.MASTERED, "noon", now)
        val d = decide(map, frenchSentence, Languages.French) // devis is absent
        assertEquals(UnitVerdict.ASSIST, d.units.single { it.normalized == "devis" }.verdict)
        assertTrue(d.units.single { it.normalized == "devis" }.explanation.contains("not in your language map"))
    }

    // ---- conservative fallbacks ----

    @Test
    fun ifAHelpedWordHasNoMeaningSaved_theWholeMessageIsTranslated() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        map.put("fr", "devis", MasteryStatus.LEARNING, meaning = null, lastSeen = now)
        val d = decide(map, frenchSentence, Languages.French)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertEquals(FallbackReason.MEANING_MISSING, d.fallback)
    }

    @Test
    fun aMeaningInAnotherLanguage_isNotUsedAsAGloss() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        map.put("fr", "devis", MasteryStatus.LEARNING, "presupuesto", now, meaningLanguage = "es") // the user's language is English
        assertEquals(FallbackReason.MEANING_MISSING, decide(map, frenchSentence, Languages.French).fallback)
    }

    @Test
    fun tooManyGlosses_meansTranslateWhole() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        val strict = AdaptiveEngine(AnalyzerRegistry(listOf(RuleBasedAnalyzer())), config = AdaptiveConfig(maxGlosses = 0))
        assertEquals(FallbackReason.TOO_MANY_TO_GLOSS, decide(map, frenchSentence, Languages.French, engine = strict).fallback)
    }

    @Test
    fun aMessageWithNothingToJudge_getsFullTranslation() = runTest {
        val d = decide(TestMap(), "Je vous.", Languages.French)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertEquals(FallbackReason.NOTHING_TO_JUDGE, d.fallback)
    }

    @Test
    fun aLanguageWithNoAnalyzer_getsFullTranslation() = runTest {
        val none = AdaptiveEngine(AnalyzerRegistry(emptyList()))
        assertEquals(FallbackReason.UNSUPPORTED_LANGUAGE, decide(TestMap(), frenchSentence, Languages.French, engine = none).fallback)
    }

    // ---- one map per language ----

    @Test
    fun knowingItInFrench_saysNothingAboutSpanish() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED) }
        // The same normalized words, but the user is learning Español: the Français entries must not count.
        map.put("fr", "presupuesto", MasteryStatus.MASTERED, "quotation", now)
        val d = decide(map, "Te enviaré el presupuesto antes del mediodía.", Languages.Spanish)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertTrue(d.units.all { it.state == MasteryStatus.UNKNOWN })
    }

    @Test
    fun aMessageNotInTheLearningLanguage_isNotAdapted() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.MASTERED, MasteryStatus.MASTERED) }
        val d = decide(map, frenchSentence, learningOf(Languages.Spanish), textLanguage = Languages.French)
        assertEquals(AdaptiveLevel.FULL_TRANSLATION, d.level)
        assertEquals(FallbackReason.NOT_THE_LEARNING_LANGUAGE, d.fallback)
    }

    private fun learningOf(language: Language) = language

    // ---- every language, same engine ----

    private class Case(val language: Language, val text: String, val content: List<String>, val helped: String, val script: Script, val expected: String)

    private val cases = listOf(
        Case(Languages.French, frenchSentence, listOf("envoyer", "devis", "midi"), "devis", Script.LATIN, "Je vais vous envoyer le devis (quotation) avant midi."),
        Case(Languages.Spanish, "Te enviaré el presupuesto antes del mediodía.", listOf("enviaré", "presupuesto", "mediodía"), "presupuesto", Script.LATIN, "Te enviaré el presupuesto (quotation) antes del mediodía."),
        Case(Languages.German, "Ich schicke dir das Angebot vor Mittag.", listOf("schicke", "angebot", "mittag"), "angebot", Script.LATIN, "Ich schicke dir das Angebot (quotation) vor Mittag."),
        Case(Languages.Italian, "Ti mando il preventivo prima di mezzogiorno.", listOf("mando", "preventivo", "mezzogiorno"), "preventivo", Script.LATIN, "Ti mando il preventivo (quotation) prima di mezzogiorno."),
        Case(Languages.Dutch, "Ik stuur je de offerte voor de middag.", listOf("stuur", "offerte", "middag"), "offerte", Script.LATIN, "Ik stuur je de offerte (quotation) voor de middag."),
        Case(Languages.Chinese, "我中午之前把报价发给你。", listOf("中午", "之前", "报价", "发"), "报价", Script.CJK, "我中午之前把报价（quotation）发给你。"),
        Case(Languages.Japanese, "昼までに見積もりを送ります。", listOf("昼", "見積もり", "送り"), "見積もり", Script.CJK, "昼までに見積もり（quotation）を送ります。"),
    )

    private fun mapFor(case: Case, mastered: (String) -> Boolean) = TestMap().also { map ->
        for (word in case.content) {
            map.put(case.language.code, word, if (mastered(word)) MasteryStatus.MASTERED else MasteryStatus.LEARNING, "quotation", now)
        }
        // Deutsch has a fixed expression in its sentence ("vor Mittag"); knowing the words alone would not be enough.
        if (case.language == Languages.German) map.put("de", "vor mittag", MasteryStatus.MASTERED, type = UnitType.EXPRESSION)
    }

    @Test
    fun everyLanguage_untouchedMap_getsFullTranslation() = runTest {
        for (case in cases) {
            val d = decide(TestMap(), case.text, case.language)
            assertEquals(case.language.code, AdaptiveLevel.FULL_TRANSLATION, d.level)
            assertEquals(case.language.code, case.content, d.units.map { it.normalized })
        }
    }

    @Test
    fun everyLanguage_oneUnmasteredWord_isGlossedInPlace_andTheRestStaysInTheLanguage() = runTest {
        for (case in cases) {
            val d = decide(mapFor(case) { it != case.helped }, case.text, case.language)
            assertEquals(case.language.code, AdaptiveLevel.PARTIAL, d.level)
            assertEquals(case.language.code, listOf(case.helped), d.assisted.map { it.normalized })
            assertEquals(case.language.code, case.expected, AdaptivePresentation.plainText(d, case.script))
        }
    }

    @Test
    fun everyLanguage_allMastered_keepsTheOriginalUntouched() = runTest {
        for (case in cases) {
            val d = decide(mapFor(case) { true }, case.text, case.language)
            assertEquals(case.language.code, AdaptiveLevel.KEEP_ORIGINAL, d.level)
            assertEquals(case.language.code, case.text, AdaptivePresentation.plainText(d, case.script))
        }
    }

    @Test
    fun theOriginalTextIsNeverAltered_onlyGlossesAreAdded() = runTest {
        for (case in cases) {
            val d = decide(mapFor(case) { it != case.helped }, case.text, case.language)
            assertEquals(case.language.code, case.text, d.segments.joinToString("") { it.text })
        }
    }

    // ---- fixed expressions ----

    private val expressionSentence = "Je vous tiens au courant demain."

    @Test
    fun knowingTheWordsOfAnExpression_isNotKnowingTheExpression() = runTest {
        val map = TestMap()
        map.put("fr", "tiens", MasteryStatus.MASTERED, "hold", now)
        map.put("fr", "courant", MasteryStatus.MASTERED, "current", now)
        map.put("fr", "demain", MasteryStatus.MASTERED, "tomorrow", now)
        val d = decide(map, expressionSentence, Languages.French)
        // The expressions themselves are unknown, so the words inside them get help even though each is mastered.
        assertEquals(UnitVerdict.ASSIST, d.units.single { it.normalized == "courant" }.verdict)
        assertEquals(UnitVerdict.KEEP, d.units.single { it.normalized == "demain" }.verdict)
    }

    @Test
    fun aMasteredExpression_letsItsWordsStay() = runTest {
        val map = TestMap()
        map.put("fr", "je vous tiens au courant", MasteryStatus.MASTERED, type = UnitType.EXPRESSION)
        map.put("fr", "au courant", MasteryStatus.MASTERED, type = UnitType.EXPRESSION)
        map.put("fr", "demain", MasteryStatus.MASTERED, "tomorrow", now)
        val d = decide(map, expressionSentence, Languages.French)
        assertEquals(AdaptiveLevel.KEEP_ORIGINAL, d.level)
    }

    @Test
    fun whenExpressionsOverlap_theLeastKnownOneDecides() = runTest {
        val map = TestMap()
        map.put("fr", "je vous tiens au courant", MasteryStatus.MASTERED, type = UnitType.EXPRESSION)
        map.put("fr", "au courant", MasteryStatus.LEARNING, type = UnitType.EXPRESSION)
        map.put("fr", "courant", MasteryStatus.MASTERED, "current", now)
        map.put("fr", "demain", MasteryStatus.MASTERED, "tomorrow", now)
        val d = decide(map, expressionSentence, Languages.French)
        assertEquals(UnitVerdict.ASSIST, d.units.single { it.normalized == "courant" }.verdict)
        assertEquals(UnitVerdict.KEEP, d.units.single { it.normalized == "tiens" }.verdict)
    }

    @Test
    fun theLeastKnownExpressionDecides_whicheverOfTheTwoIsTheShorter() = runTest {
        val map = TestMap()
        map.put("fr", "je vous tiens au courant", MasteryStatus.LEARNING, type = UnitType.EXPRESSION)
        map.put("fr", "au courant", MasteryStatus.MASTERED, type = UnitType.EXPRESSION)
        map.put("fr", "tiens", MasteryStatus.MASTERED, "hold", now)
        map.put("fr", "courant", MasteryStatus.MASTERED, "current", now)
        map.put("fr", "demain", MasteryStatus.MASTERED, "tomorrow", now)
        val d = decide(map, expressionSentence, Languages.French)
        // The long expression is not mastered, so both of its content words get help; the short one being mastered cannot undo that.
        assertEquals(setOf("tiens", "courant"), d.assisted.map { it.normalized }.toSet())
    }

    // ---- explainability and determinism ----

    @Test
    fun everyUnitCarriesItsReason() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        val d = decide(map, frenchSentence, Languages.French)
        assertEquals("mastered", d.units.single { it.normalized == "midi" }.explanation)
        assertEquals("learning, not mastered yet", d.units.single { it.normalized == "devis" }.explanation)
    }

    @Test
    fun theSameMessageAndMap_alwaysGiveTheSameDecision() = runTest {
        val map = TestMap().also { it.french(MasteryStatus.MASTERED, MasteryStatus.LEARNING, MasteryStatus.MASTERED) }
        assertEquals(decide(map, frenchSentence, Languages.French), decide(map, frenchSentence, Languages.French))
    }
}
