package com.alterlingua.app.ui.learn

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.reading.ReadingApi
import com.alterlingua.app.learning.reading.ReadingLessonService
import com.alterlingua.app.learning.reading.ReadingPageResult
import com.alterlingua.app.learning.reading.ReadingSessionState
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeReadingApi(private val sentences: List<String>) : ReadingApi {
    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int) =
        ReadingPageResult.Success(sentences.drop(offset).take(limit), sentences.size)
}

private class FakeReadingMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language) = "meaning of $unit"
}

private class FakeReadingSpeaker(private val canSpeakAnything: Boolean = true) : Speaker {
    val spoken = mutableListOf<Pair<String, Language>>()
    var stopped = false

    override suspend fun canSpeak(language: Language) = canSpeakAnything
    override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean {
        spoken += text to language
        return canSpeakAnything
    }
    override suspend fun synthesizeToFile(text: String, language: Language, file: File) = false
    override fun stop() { stopped = true }
    override fun shutdown() = Unit
}

class ReadingLessonViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val tenSentences = (1..10).map { "Phrase numéro $it." }

    private fun viewModel(
        api: ReadingApi = FakeReadingApi(tenSentences),
        meanings: MeaningProvider = FakeReadingMeanings(),
        speaker: FakeReadingSpeaker = FakeReadingSpeaker(),
    ): Pair<ReadingLessonViewModel, FakeReadingSpeaker> {
        val service = ReadingLessonService(api, meanings, { LessonLanguages(Languages.French, Languages.English) }, randomOffset = { it.first })
        return ReadingLessonViewModel(service, speaker) to speaker
    }

    @Test fun startLoadsASession() = runTest {
        val (vm, _) = viewModel()
        vm.start()
        assertTrue(vm.uiState.value is ReadingSessionState.InProgress)
    }

    @Test fun revealFillsInTheCurrentSentencesMeaning() = runTest {
        val (vm, _) = viewModel()
        vm.start()
        assertNull((vm.uiState.value as ReadingSessionState.InProgress).current.translation)
        vm.reveal()
        assertEquals("meaning of Phrase numéro 1.", (vm.uiState.value as ReadingSessionState.InProgress).current.translation)
    }

    @Test fun nextAdvancesPosition() = runTest {
        val (vm, _) = viewModel()
        vm.start()
        vm.next()
        assertEquals(1, (vm.uiState.value as ReadingSessionState.InProgress).position)
    }

    @Test fun nextPastTheLastSentenceFinishes() = runTest {
        val (vm, _) = viewModel()
        vm.start()
        repeat(8) { vm.next() }
        assertTrue(vm.uiState.value is ReadingSessionState.Finished)
    }

    @Test fun listenSpeaksTheCurrentSentence() = runTest {
        val (vm, speaker) = viewModel()
        vm.start()
        vm.listen()
        assertEquals(listOf("Phrase numéro 1." to Languages.French), speaker.spoken)
        assertTrue(vm.listening.value)
    }

    @Test fun listenAgainWhileSpeakingStopsInstead() = runTest {
        val (vm, speaker) = viewModel()
        vm.start()
        vm.listen()
        vm.listen()
        assertTrue(speaker.stopped)
        assertFalse(vm.listening.value)
    }

    @Test fun nextStopsAnyOngoingSpeech() = runTest {
        val (vm, speaker) = viewModel()
        vm.start()
        vm.listen()
        vm.next()
        assertTrue(speaker.stopped)
        assertFalse(vm.listening.value)
    }
}
