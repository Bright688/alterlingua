package com.alterlingua.app.ui.home

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
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
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.progress.DailyActivity
import com.alterlingua.app.learning.progress.DependenceStatus
import com.alterlingua.app.learning.progress.InMemoryProgressStore
import com.alterlingua.app.learning.progress.ProgressLog
import com.alterlingua.app.learning.progress.ProgressService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val store = InMemoryProgressStore()
    private val now = LocalDate.of(2026, 9, 20).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val map = LanguageMapService(InMemoryLanguageMapStore(), clock = { now })
    private val progress = ProgressService(map, ProgressLog(store, { now }, { ZoneOffset.UTC }), { now }, { ZoneOffset.UTC })

    private var learning: Language = Languages.French

    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = "meaning of $unit"
    }

    private fun lessonService() = LessonService(
        map = map, meanings = meanings, store = InMemoryDailyLessonStore(),
        languages = { LessonLanguages(learning, Languages.English) }, clock = { now }, zone = ZoneOffset.UTC,
    )

    private suspend fun met(language: String, text: String, times: Int = 1) {
        repeat(times) {
            val candidate = LearningCandidate(text, text, UnitType.WORD, language, "en", Usefulness(0.5, listOf(UsefulnessSignal.CONTENT_WORD)), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, InteractionKind.OUTGOING_TEXT, language, "en", listOf(candidate)))
        }
    }

    private fun viewModel(settings: UserSettings = UserSettings()): HomeViewModel {
        learning = settings.targetLanguage
        return HomeViewModel(FakeUserSettingsRepository(settings), progress, lessonService())
    }

    @Test
    fun aNewUserSeesTheLanguageBeingLearned_andNoInventedFigures() {
        val state = viewModel(UserSettings(targetLanguage = Languages.German)).uiState.value
        assertEquals("Deutsch", state.targetLanguage)
        assertTrue("no lesson until something has been met", state.lessonItems.isEmpty())
        assertEquals(0, state.translationsThisWeek)
        assertEquals(0, state.languageMap.total)
    }

    @Test
    fun theLessonIsTheRealOneBuiltFromWhatWasMet() = runBlocking {
        met("fr", "devis", times = 3)
        met("es", "presupuesto", times = 3)
        val vm = viewModel()
        vm.refresh()
        assertEquals(listOf("devis"), vm.uiState.value.lessonItems.map { it.term })
        assertEquals("meaning of devis", vm.uiState.value.lessonItems.single().meaning)
        assertEquals(1, vm.uiState.value.languageMap.total)
    }

    @Test
    fun withNoHistory_translationDependenceIsNotInvented() {
        assertEquals(DependenceStatus.NoData, viewModel().uiState.value.dependence)
    }

    @Test
    fun withRealHistory_translationDependenceIsTheRealFigure() = runBlocking {
        val monday = LocalDate.of(2026, 9, 14)
        store.update(monday.minusWeeks(1).toString(), "fr") { DailyActivity(it.date, "fr", wordsMet = 50, wordsAssisted = 47) }
        store.update(monday.toString(), "fr") { DailyActivity(it.date, "fr", wordsMet = 50, wordsAssisted = 30) }
        val vm = viewModel()
        vm.refresh()
        val status = vm.uiState.value.dependence as DependenceStatus.Ready
        assertEquals(60, status.current)
        assertEquals(94, status.first)
        assertTrue(status.points.size == 2)
    }

    @Test
    fun anotherLanguagesHistory_isNotUsed() = runBlocking {
        store.update("2026-09-14", "es") { DailyActivity(it.date, "es", wordsMet = 50, wordsAssisted = 47) }
        val vm = viewModel() // learning Français
        vm.refresh()
        assertEquals(DependenceStatus.NoData, vm.uiState.value.dependence)
    }
}
