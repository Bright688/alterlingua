package com.alterlingua.app.ui.learn

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.engine.UsefulnessSignal
import com.alterlingua.app.learning.lessons.InMemoryDailyLessonStore
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LearnViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val map = LanguageMapService(InMemoryLanguageMapStore())
    private val repo = FakeUserSettingsRepository(UserSettings())

    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
    }

    private fun lessons() = LessonService(
        map, meanings, InMemoryDailyLessonStore(),
        languages = { UserSettings().let { LessonLanguages(it.targetLanguage, it.nativeLanguage) } },
    )

    private suspend fun met(language: String, text: String, type: UnitType = UnitType.WORD, times: Int = 1) {
        val now = System.currentTimeMillis()
        repeat(times) {
            val c = LearningCandidate(text, text, type, language, "en", Usefulness(0.7, listOf(UsefulnessSignal.CONTENT_WORD)), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, language, "en", listOf(c)))
        }
    }

    private fun viewModel(): LearnViewModel = LearnViewModel(lessons(), repo)

    @Test
    fun withNothingMetYet_showsTheEmptyState() {
        assertTrue(viewModel().uiState.value is LearnUiState.Empty)
    }

    @Test
    fun withItemsMet_showsTheFirstCard_andCannotGoBack() = runBlocking {
        val language = UserSettings().targetLanguage.code
        met(language, "devis", times = 3)
        met(language, "avant midi", UnitType.PHRASE)
        val state = viewModel().uiState.value
        assertTrue(state is LearnUiState.Card)
        state as LearnUiState.Card
        assertEquals(0, state.index)
        assertFalse(state.canGoBack)
        assertEquals(2, state.total)
    }

    @Test
    fun nextWalksThroughTheCards_thenCompletes_andBackWorks() = runBlocking {
        val language = UserSettings().targetLanguage.code
        met(language, "devis", times = 3)
        met(language, "avant midi", UnitType.PHRASE)
        val vm = viewModel()
        vm.next()
        val second = vm.uiState.value as LearnUiState.Card
        assertEquals(1, second.index)
        assertTrue(second.isLast)
        vm.previous()
        assertEquals(0, (vm.uiState.value as LearnUiState.Card).index)
        vm.next()
        vm.next()
        assertTrue(vm.uiState.value is LearnUiState.Complete)
    }

    @Test
    fun finishingTheLesson_recordsALessonEncounterForEachItem() = runBlocking {
        val language = UserSettings().targetLanguage.code
        met(language, "devis", times = 3)
        met(language, "avant midi", UnitType.PHRASE)
        val vm = viewModel()
        vm.next()
        vm.next()
        val items = map.items(UserSettings().targetLanguage.code)
        assertEquals(listOf(1, 1), items.map { it.lessonEncounters })
    }

    @Test
    fun refresh_picksUpItemsMetAfterTheScreenFirstOpened() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.uiState.value is LearnUiState.Empty)
        met(UserSettings().targetLanguage.code, "devis", times = 3)
        vm.refresh()
        assertTrue(vm.uiState.value is LearnUiState.Card)
    }

    @Test
    fun emptyStateNamesTheLanguageBeingLearned() {
        val state = viewModel().uiState.value as LearnUiState.Empty
        assertEquals(UserSettings().targetLanguage, state.language)
        assertEquals(Languages.French, state.language)
    }
}
