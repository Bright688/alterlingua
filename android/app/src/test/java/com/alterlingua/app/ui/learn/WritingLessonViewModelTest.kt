package com.alterlingua.app.ui.learn

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.curriculum.CurriculumApi
import com.alterlingua.app.learning.curriculum.CurriculumPageResult
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.writing.WritingLessonService
import com.alterlingua.app.learning.writing.WritingSessionState
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeWritingVmApi(private val words: List<String>) : CurriculumApi {
    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int) =
        CurriculumPageResult.Success(words.drop(offset).take(limit), words.size)
}

private class FakeWritingVmMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
}

class WritingLessonViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(api: CurriculumApi = FakeWritingVmApi((1..10).map { "mot$it" })): WritingLessonViewModel {
        val service = WritingLessonService(api, FakeWritingVmMeanings(), { LessonLanguages(Languages.French, Languages.English) }, randomOffset = { it.first })
        return WritingLessonViewModel(service)
    }

    @Test fun startLoadsASession() = runTest {
        val vm = viewModel()
        vm.start()
        assertTrue(vm.uiState.value is WritingSessionState.InProgress)
    }

    @Test fun nextAdvancesPosition() = runTest {
        val vm = viewModel()
        vm.start()
        vm.next()
        assertEquals(1, (vm.uiState.value as WritingSessionState.InProgress).position)
    }

    @Test fun nextPastTheLastPromptFinishes() = runTest {
        val vm = viewModel()
        vm.start()
        repeat(5) { vm.next() }
        assertTrue(vm.uiState.value is WritingSessionState.Finished)
    }
}
