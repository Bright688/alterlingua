package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.Icu4jWordBreaker
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapItem
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.MasteryCalculator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDemandHelpTest {

    private val day = 24L * 60 * 60 * 1000
    private var now = 10_000L * day
    private val store = InMemoryLanguageMapStore()
    private val map = LanguageMapService(store, clock = { now })
    private var learning: Language = Languages.French
    private var native: Language = Languages.English

    private class Meanings(val known: Map<String, String>) : MeaningProvider {
        var offline = false
        val asked = mutableListOf<String>()
        override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? {
            asked += unit
            return if (offline) null else known[unit]
        }
    }

    private val meanings = Meanings(mapOf("acompte" to "deposit / advance payment", "fournisseur" to "supplier", "見積もり" to "quotation"))

    private val help = OnDemandHelp(
        AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = Icu4jWordBreaker))), map, meanings,
        languages = { LessonLanguages(learning, native) }, clock = { now },
    )

    private val sentence = "Le fournisseur exige un acompte de 30%."

    private fun key(text: String, language: String = "fr") = UnitKey(language, text, UnitType.WORD)

    // ---- presentation preserves the language ----

    @Test
    fun theTextIsShownAsItIs_cutIntoWords_withEveryPieceJoiningBackToTheOriginal() = runTest {
        val segments = help.read(sentence)
        assertEquals(sentence, segments.joinToString("") { it.text })
        assertTrue(segments.any { it.text == "acompte" && it.askable })
    }

    @Test
    fun readingAloneTranslatesNothingAndRecordsNothing() = runTest {
        help.read(sentence)
        assertTrue(meanings.asked.isEmpty())
        assertTrue(map.items("fr").isEmpty())
    }

    @Test
    fun numbersAndPunctuation_cannotBeAskedAbout() = runTest {
        val segments = help.read(sentence)
        // "30", spaces, "%" and "." are never offered as words to ask about.
        assertTrue(segments.filter { it.askable }.none { seg -> seg.text.any { !it.isLetter() } })
        assertTrue(segments.none { it.askable && it.text == "30" })
        assertTrue(segments.any { !it.askable && it.text.contains("30") })
    }

    @Test
    fun everyLanguage_theSegmentsAlwaysRebuildTheOriginal() = runTest {
        val texts = mapOf(
            Languages.French to sentence, Languages.Spanish to "El proveedor exige un anticipo del 30 %.",
            Languages.German to "Der Lieferant verlangt eine Anzahlung von 30 %.", Languages.Italian to "Il fornitore richiede un acconto del 30%.",
            Languages.Dutch to "De leverancier eist een aanbetaling van 30%.", Languages.Chinese to "供应商要求支付30%的定金。",
            Languages.Japanese to "供給元は30％の手付金を要求します。",
        )
        for ((language, text) in texts) {
            learning = language
            val segments = help.read(text)
            assertEquals(language.code, text, segments.joinToString("") { it.text })
            assertTrue(language.code, segments.any { it.askable })
        }
    }

    @Test
    fun aLanguageWithoutAnalyzer_givesPlainText_withNothingAskable() = runTest {
        val bare = OnDemandHelp(AnalyzerRegistry(emptyList()), map, meanings, { LessonLanguages(learning, native) })
        assertEquals(listOf(ReaderSegment(sentence)), bare.read(sentence))
    }

    // ---- asking for help ----

    @Test
    fun askingAboutAWord_showsItsMeaning() = runTest {
        val word = help.read(sentence).single { it.text == "acompte" }
        val answer = help.request(word)
        assertEquals("acompte", answer.term)
        assertEquals("deposit / advance payment", answer.meaning)
    }

    @Test
    fun onlyTheAskedWordIsLookedUp_neverTheSentence() = runTest {
        help.request(help.read(sentence).single { it.text == "acompte" })
        assertEquals(listOf("acompte"), meanings.asked)
    }

    @Test
    fun theRequestBecomesAMasterySignal_helpCountedAndTimeRecorded() = runTest {
        now += 5 * day
        help.request(help.read(sentence).single { it.text == "acompte" })
        val item = map.item(key("acompte"))!!
        assertEquals(1, item.helpRequests)
        assertEquals(now, item.lastHelpAt)
    }

    @Test
    fun aWordAskedAboutJoinsTheLanguageMap_soItCanBecomeALessonItem() = runTest {
        help.request(help.read(sentence).single { it.text == "acompte" })
        assertEquals(listOf("acompte"), map.items("fr").map { it.normalized })
    }

    @Test
    fun aWordThatWasMastered_isNoLongerMastered_afterAskingForHelp() = runTest {
        store.update(key("acompte")) {
            LanguageMapItem("fr", "acompte", UnitType.WORD, "acompte", exposureCount = 12, lessonEncounters = 4, correctRecognitions = 5, lastSeen = now)
        }
        assertEquals(MasteryStatus.MASTERED, MasteryCalculator().evaluate(map.item(key("acompte"))!!.evidence, now).state)
        val answer = help.request(help.read(sentence).single { it.text == "acompte" })
        assertEquals(MasteryStatus.FAMILIAR, answer.state)
        assertEquals(MasteryStatus.FAMILIAR, MasteryCalculator().evaluate(map.item(key("acompte"))!!.evidence, now).state)
    }

    @Test
    fun theMeaningIsSavedInTheMap_soAskingAgainNeedsNoConnection() = runTest {
        val word = help.read(sentence).single { it.text == "acompte" }
        help.request(word)
        meanings.offline = true
        assertEquals("deposit / advance payment", help.request(word, record = false).meaning)
        assertEquals(1, meanings.asked.size)
    }

    @Test
    fun whenTheMeaningCannotBeFound_theRequestIsStillRecorded_andTheAnswerSaysNothingMade_up() = runTest {
        meanings.offline = true
        val answer = help.request(help.read(sentence).single { it.text == "acompte" })
        assertNull(answer.meaning)
        assertEquals(1, map.item(key("acompte"))!!.helpRequests)
    }

    @Test
    fun aRepeatQuestionInTheSameReading_canBeLeftUncounted() = runTest {
        val word = help.read(sentence).single { it.text == "acompte" }
        help.request(word)
        help.request(word, record = false)
        assertEquals(1, map.item(key("acompte"))!!.helpRequests)
        help.request(word) // asked again in a new reading
        assertEquals(2, map.item(key("acompte"))!!.helpRequests)
    }

    @Test
    fun aMeaningInAnotherLanguage_isNotShownToThisUser() = runTest {
        store.update(key("acompte")) { LanguageMapItem("fr", "acompte", UnitType.WORD, "acompte", meaning = "anticipo", meaningLanguage = "es") }
        assertEquals("deposit / advance payment", help.request(help.read(sentence).single { it.text == "acompte" }).meaning)
    }

    // ---- languages ----

    @Test
    fun helpIsRecordedInTheMapOfTheCurrentLearningLanguage_only() = runTest {
        learning = Languages.Japanese
        help.request(help.read("見積もりを送ります。").single { it.text == "見積もり" })
        assertEquals(1, map.item(key("見積もり", "ja"))!!.helpRequests)
        assertTrue(map.items("fr").isEmpty())
    }

    @Test
    fun theMeaningIsInTheUsersOwnLanguage_notAlwaysEnglish() = runTest {
        native = Languages.Spanish
        val spanish = OnDemandHelp(
            AnalyzerRegistry(listOf(RuleBasedAnalyzer())), map,
            object : MeaningProvider { override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "[${native.code}] $unit" },
            { LessonLanguages(learning, native) }, clock = { now },
        )
        assertEquals("[es] acompte", spanish.request(spanish.read(sentence).single { it.text == "acompte" }).meaning)
        assertEquals("es", map.item(key("acompte"))!!.meaningLanguage)
    }

    @Test
    fun theAdaptiveEngine_helpsAgainWithAWordTheUserJustAskedAbout() = runTest {
        for (w in listOf("fournisseur", "exige", "acompte")) {
            store.update(key(w)) { LanguageMapItem("fr", w, UnitType.WORD, w, "meaning of $w", "en", exposureCount = 12, lessonEncounters = 4, correctRecognitions = 5, lastSeen = now) }
        }
        val engine = AdaptiveEngine(AnalyzerRegistry(listOf(RuleBasedAnalyzer())))
        suspend fun level() = engine.decide(sentence, Languages.French, Languages.French, Languages.English, now) { map.item(it) }
        assertEquals(AdaptiveLevel.KEEP_ORIGINAL, level().level)
        help.request(help.read(sentence).single { it.text == "acompte" })
        val after = level()
        assertEquals(listOf("acompte"), after.assisted.map { it.normalized })
        assertEquals(AdaptiveLevel.PARTIAL, after.level)
    }
}
