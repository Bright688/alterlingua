package com.alterlingua.app.ui.words

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Words list is the user's real Personal Language Map for the language being learned, never sample entries. */
class WordsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val map = LanguageMapService(InMemoryLanguageMapStore())

    private fun key(language: String, word: String) = UnitKey(language, word, UnitType.WORD)

    private suspend fun know(language: String, word: String, meaning: String) {
        map.recordLessonEncounter(key(language, word), word)
        map.setMeaning(key(language, word), meaning, "en")
    }

    private fun viewModel(settings: UserSettings = UserSettings(), repo: FakeUserSettingsRepository = FakeUserSettingsRepository(settings)) =
        WordsViewModel(repo, map)

    private suspend fun WordsViewModel.awaitWords(count: Int) = withTimeout(5_000) { uiState.first { it.words.size == count } }

    @Test
    fun aNewUserSeesNoWordsAndZeroTotals_notSampleEntries() {
        val state = viewModel().uiState.value
        assertTrue(state.isEmpty)
        assertEquals(0, state.summary.total)
    }

    @Test
    fun theListShowsTheRealEntriesOfTheLanguageBeingLearned() = runBlocking {
        know("fr", "devis", "quotation")
        know("es", "presupuesto", "budget")
        val state = viewModel().awaitWords(1)
        assertEquals(listOf("devis"), state.words.map { it.term })
        assertEquals("quotation", state.words.single().meaning)
        assertEquals(1, state.summary.total)
    }

    @Test
    fun changingTheLanguage_changesTheWords() = runBlocking {
        know("fr", "devis", "quotation")
        know("es", "presupuesto", "budget")
        val repo = FakeUserSettingsRepository()
        val vm = viewModel(repo = repo)
        assertEquals(listOf("devis"), vm.awaitWords(1).words.map { it.term })

        repo.update { it.copy(targetLanguage = Languages.Spanish) }

        assertEquals(listOf("presupuesto"), vm.awaitWords(1).words.map { it.term })
    }

    @Test
    fun searchMatchesTermOrMeaning_ignoringCase() = runBlocking {
        know("fr", "devis", "quotation")
        know("fr", "réunion", "meeting")
        val vm = viewModel()
        vm.awaitWords(2)
        vm.onQueryChange("QUOTATION")
        assertEquals(listOf("devis"), vm.uiState.value.visibleWords.map { it.term })
        vm.onQueryChange("réun")
        assertEquals(listOf("réunion"), vm.uiState.value.visibleWords.map { it.term })
    }

    @Test
    fun filterKeepsOnlyThatState_andASearchThatMatchesNothingIsNotTheEmptyState() = runBlocking {
        know("fr", "devis", "quotation")
        val vm = viewModel()
        vm.awaitWords(1)
        vm.onFilterSelected(MasteryStatus.MASTERED)
        assertTrue(vm.uiState.value.visibleWords.isEmpty())
        assertTrue("there are words, only none match the filter", !vm.uiState.value.isEmpty)
    }

    @Test
    fun searchWorksInNonLatinScripts() = runBlocking {
        know("zh", "报价", "quotation")
        val vm = viewModel(UserSettings(targetLanguage = Languages.Chinese))
        vm.awaitWords(1)
        vm.onQueryChange("报价")
        assertEquals(listOf("报价"), vm.uiState.value.visibleWords.map { it.term })
    }
}
