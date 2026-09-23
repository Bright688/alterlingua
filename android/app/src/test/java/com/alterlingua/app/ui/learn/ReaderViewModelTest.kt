package com.alterlingua.app.ui.learn

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.assistance.OnDemandHelp
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val map = LanguageMapService(InMemoryLanguageMapStore())
    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language) = if (unit == "acompte") "deposit" else "meaning of $unit"
    }
    // Created inside the test (after the main-dispatcher rule has started), not when the test class is built.
    private val vm by lazy {
        ReaderViewModel(OnDemandHelp(AnalyzerRegistry(listOf(RuleBasedAnalyzer())), map, meanings, { LessonLanguages(Languages.French, Languages.English) }))
    }
    private val sentence = "Le fournisseur exige un acompte de 30%."

    private fun open() {
        vm.onInputChanged(sentence)
        vm.show()
    }

    private fun word(text: String) = vm.uiState.value.segments.single { it.text == text }

    @Test fun startsEmpty_withTheLanguageNamed() {
        assertFalse(vm.uiState.value.reading)
        assertEquals(Languages.French, vm.uiState.value.language)
    }

    @Test fun showingTheText_keepsItInFrench_andAsksForNothing() = runBlocking {
        open()
        assertEquals(sentence, vm.uiState.value.segments.joinToString("") { it.text })
        assertNull(vm.uiState.value.help)
        assertTrue(map.items("fr").isEmpty())
    }

    @Test fun blankInputShowsNothing() {
        vm.onInputChanged("   ")
        vm.show()
        assertFalse(vm.uiState.value.reading)
    }

    @Test fun tappingAWord_showsItsMeaning_andRecordsOneHelpRequest() = runBlocking {
        open()
        vm.ask(word("acompte"))
        val shown = vm.uiState.value.help as WordHelpState.Shown
        assertEquals("deposit", shown.answer.meaning)
        assertEquals(1, map.item(UnitKey("fr", "acompte", UnitType.WORD))!!.helpRequests)
    }

    @Test fun tappingTheSameWordAgain_isNotCountedTwiceInOneReading() = runBlocking {
        open()
        vm.ask(word("acompte"))
        vm.ask(word("acompte"))
        assertEquals(1, map.item(UnitKey("fr", "acompte", UnitType.WORD))!!.helpRequests)
    }

    @Test fun readingTheTextAgain_countsANewRequest() = runBlocking {
        open()
        vm.ask(word("acompte"))
        vm.clear()
        open()
        vm.ask(word("acompte"))
        assertEquals(2, map.item(UnitKey("fr", "acompte", UnitType.WORD))!!.helpRequests)
    }

    @Test fun tappingSomethingThatIsNotAWord_doesNothing() = runBlocking {
        open()
        vm.ask(vm.uiState.value.segments.first { !it.askable })
        assertNull(vm.uiState.value.help)
        assertTrue(map.items("fr").isEmpty())
    }

    @Test fun clearForgetsTheText() = runBlocking {
        open()
        vm.ask(word("acompte"))
        vm.clear()
        assertEquals("", vm.uiState.value.input)
        assertTrue(vm.uiState.value.segments.isEmpty())
        assertNull(vm.uiState.value.help)
    }

    @Test fun theInputIsLimitedInLength() {
        vm.onInputChanged("a".repeat(5000))
        assertEquals(ReaderViewModel.MAX_CHARS, vm.uiState.value.input.length)
    }
}
