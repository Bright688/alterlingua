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

class RandomLessonViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val map = LanguageMapService(InMemoryLanguageMapStore())
    private val repo = FakeUserSettingsRepository(UserSettings())
    private val dailyStore = InMemoryDailyLessonStore()

    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
    }

    private fun lessons() = LessonService(
        map, meanings, dailyStore,
        languages = { UserSettings().let { LessonLanguages(it.targetLanguage, it.nativeLanguage) } },
    )

    private suspend fun met(language: String, text: String, type: UnitType = UnitType.WORD, usefulness: Double = 0.7, times: Int = 1) {
        val now = System.currentTimeMillis()
        repeat(times) {
            val c = LearningCandidate(text, text, type, language, "en", Usefulness(usefulness, listOf(UsefulnessSignal.CONTENT_WORD)), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, language, "en", listOf(c)))
        }
    }

    private fun viewModel(): RandomLessonViewModel = RandomLessonViewModel(lessons(), map, repo)

    @Test
    fun beforeStart_isLoading() {
        assertTrue(viewModel().uiState.value is LearnUiState.Loading)
    }

    @Test
    fun withNothingMetYet_startShowsTheEmptyState_withTheLearningLanguage() {
        val vm = viewModel()
        vm.start()
        val state = vm.uiState.value
        assertTrue(state is LearnUiState.Empty)
        assertEquals(Languages.French, (state as LearnUiState.Empty).language)
    }

    @Test
    fun withEnoughMet_startShowsACard() = runBlocking {
        met("fr", "devis", UnitType.WORD, usefulness = 0.8)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        val vm = viewModel()
        vm.start()
        val state = vm.uiState.value
        assertTrue(state is LearnUiState.Card)
        assertEquals(0, (state as LearnUiState.Card).index)
    }

    @Test
    fun next_movesThroughTheCards_andRecordsALessonEncounter_thenCompletes() = runBlocking {
        met("fr", "devis", UnitType.WORD, usefulness = 0.8)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        val vm = viewModel()
        vm.start()
        val first = (vm.uiState.value as LearnUiState.Card).lesson.current.key
        assertEquals(0, map.item(first)!!.lessonEncounters)
        repeat(3) { vm.next() }
        assertTrue(vm.uiState.value is LearnUiState.Complete)
        assertEquals(1, map.item(first)!!.lessonEncounters)
    }

    @Test
    fun previous_goesBack_withoutRecordingAnything() = runBlocking {
        met("fr", "devis", UnitType.WORD, usefulness = 0.8)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        val vm = viewModel()
        vm.start()
        vm.next()
        val second = (vm.uiState.value as LearnUiState.Card).lesson.current.key
        vm.previous()
        val state = vm.uiState.value as LearnUiState.Card
        assertEquals(0, state.index)
        assertFalse(second in state.lesson.encountered.map { state.lesson.cards[it].key })
    }

    @Test
    fun recordRecognition_passesThroughToTheLanguageMap() = runBlocking {
        met("fr", "devis", UnitType.WORD, usefulness = 0.8)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        val vm = viewModel()
        vm.start()
        val card = (vm.uiState.value as LearnUiState.Card).lesson.current
        vm.recordRecognition(card, true)
        assertEquals(1, map.item(card.key)!!.evidence.correctRecognitions)
    }

    @Test
    fun aRandomLesson_isNeverSaved_andDoesNotTouchTheDailyLesson() = runBlocking {
        met("fr", "devis", UnitType.WORD, usefulness = 0.8)
        met("fr", "avant midi", UnitType.PHRASE, usefulness = 0.8)
        met("fr", "au courant", UnitType.EXPRESSION, usefulness = 0.9)
        val vm = viewModel()
        vm.start()
        vm.next()
        assertEquals("nothing is ever saved to the daily-lesson store", null, dailyStore.load())
    }
}
