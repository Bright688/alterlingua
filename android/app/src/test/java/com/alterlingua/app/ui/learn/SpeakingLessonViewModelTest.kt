package com.alterlingua.app.ui.learn

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.reading.ReadingApi
import com.alterlingua.app.learning.reading.ReadingPageResult
import com.alterlingua.app.learning.speaking.SpeakingLessonService
import com.alterlingua.app.learning.speaking.SpeakingSessionState
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeSpeakingVmApi(private val sentences: List<String>) : ReadingApi {
    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int) =
        ReadingPageResult.Success(sentences.drop(offset).take(limit), sentences.size)
}

class SpeakingLessonViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(api: ReadingApi = FakeSpeakingVmApi((1..10).map { "Phrase $it." })): SpeakingLessonViewModel {
        val service = SpeakingLessonService(api, { LessonLanguages(Languages.French, Languages.English) }, randomOffset = { it.first })
        return SpeakingLessonViewModel(service)
    }

    @Test fun startLoadsASession() = runTest {
        val vm = viewModel()
        vm.start()
        assertTrue(vm.uiState.value is SpeakingSessionState.InProgress)
    }

    @Test fun nextAdvancesPosition() = runTest {
        val vm = viewModel()
        vm.start()
        vm.next()
        assertEquals(1, (vm.uiState.value as SpeakingSessionState.InProgress).position)
    }

    @Test fun nextPastTheLastCardFinishes() = runTest {
        val vm = viewModel()
        vm.start()
        repeat(8) { vm.next() }
        assertTrue(vm.uiState.value is SpeakingSessionState.Finished)
    }
}
