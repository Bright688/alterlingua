package com.alterlingua.app.ui.learn

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.curriculum.CurriculumApi
import com.alterlingua.app.learning.curriculum.CurriculumLessonService
import com.alterlingua.app.learning.curriculum.CurriculumPageResult
import com.alterlingua.app.learning.curriculum.CurriculumProgressService
import com.alterlingua.app.learning.curriculum.CurriculumSessionState
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeApi(private val words: List<String>) : CurriculumApi {
    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int) =
        CurriculumPageResult.Success(words.drop(offset).take(limit), words.size)
}

private class FakeMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
}

class CurriculumLessonViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeUserSettingsRepository(UserSettings(curriculumDailyGoal = 2))
    private val map = LanguageMapService(InMemoryLanguageMapStore())

    private fun viewModel(words: List<String> = listOf("un", "deux", "trois")) = CurriculumLessonViewModel(
        CurriculumLessonService(
            api = FakeApi(words),
            meanings = FakeMeanings(),
            progress = CurriculumProgressService(settings),
            entitlements = EntitlementService(settings),
            map = map,
            languages = { LessonLanguages(Languages.French, Languages.English) },
        ),
    )

    @Test
    fun start_loadsTodaysSession() = runTest {
        val vm = viewModel()
        assertEquals(CurriculumSessionState.Loading, vm.uiState.value)
        vm.start()
        val state = vm.uiState.value as CurriculumSessionState.InProgress
        assertEquals(listOf("un", "deux"), state.cards.map { it.term })
    }

    @Test
    fun next_advancesThroughTheSession_thenFinishes() = runTest {
        val vm = viewModel()
        vm.start()
        vm.next()
        assertTrue((vm.uiState.value as CurriculumSessionState.InProgress).position == 1)
        vm.next()
        assertTrue(vm.uiState.value is CurriculumSessionState.DoneForToday)
    }

    @Test
    fun next_beforeStart_doesNothing() = runTest {
        val vm = viewModel()
        vm.next()
        assertEquals(CurriculumSessionState.Loading, vm.uiState.value)
    }
}
