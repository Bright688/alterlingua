package com.alterlingua.app.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.alterlingua.app.keyboard.CapturedNoteUi
import com.alterlingua.app.keyboard.capturedNoteUi
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.share.AudioSource
import com.alterlingua.app.share.SharedAudioReader
import com.alterlingua.app.share.SharedVoiceState
import com.alterlingua.app.share.SharedVoiceViewModel
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.share.VoiceNoteLearning
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import com.alterlingua.app.translation.VoiceTranslation
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapturedNotesTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private val source = object : AudioSource {
        override fun declaredType(address: String) = "audio/wav"
        override fun open(address: String): InputStream = ByteArrayInputStream("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray() + ByteArray(1_000))
    }

    private class FakeApi : VoiceApi {
        var calls = 0
        val results = ArrayDeque<VoiceResult>()
        override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult {
            calls++
            return results.removeFirst()
        }
    }

    private class QuietSpeaker : Speaker {
        var shutdown = false
        override suspend fun canSpeak(language: Language) = true
        override fun speak(text: String, language: Language, onDone: () -> Unit) = true
        override suspend fun synthesizeToFile(text: String, language: Language, file: File) = true
        override fun stop() {}
        override fun shutdown() { shutdown = true }
    }

    private val api = FakeApi()
    private val speakers = mutableListOf<QuietSpeaker>()
    private val cache by lazy { folder.newFolder("cache") }

    private fun result(unclear: Boolean = false) =
        VoiceResult.Success(VoiceTranslation("fr", "Je viens de parler au fournisseur.", "en", "I just spoke with the supplier.", unclear))

    private fun newViewModel() = SharedVoiceViewModel(
        reader = SharedAudioReader(source, cache),
        api = api,
        settings = FakeUserSettingsRepository(UserSettings(nativeLanguage = Languages.English, targetLanguage = Languages.French)),
        analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
        map = LanguageMapService(InMemoryLanguageMapStore()),
        meanings = object : MeaningProvider {
            override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = null
        },
        learning = VoiceNoteLearning { false },
        speaker = QuietSpeaker().also { speakers += it },
        io = Dispatchers.Unconfined,
    )

    private fun notes(maxKept: Int = 5, now: () -> Long = { 1_000L }) = CapturedNotes(
        create = { store: ViewModelStore ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = newViewModel() as T
            }
            ViewModelProvider(store, factory)[SharedVoiceViewModel::class.java]
        },
        clock = now,
        maxKept = maxKept,
    )

    private val address = "content://alterlingua-capture/note-one.wav"

    @Test fun aCapturedNote_isTranslatedAtOnce_andBecomesTheNoteTheKeyboardShows() {
        api.results += result()
        val notes = notes()
        val note = notes.open(address, announce = true)
        assertSame(note, notes.latest.value)
        assertEquals(1, api.calls)
        val shown = capturedNoteUi(note.viewModel.uiState.value)
        assertTrue(shown is CapturedNoteUi.Result)
        assertEquals("I just spoke with the supplier.", (shown as CapturedNoteUi.Result).translation)
        assertEquals("Français", shown.originalLanguage)
    }

    @Test fun openingTheSameNoteAgain_findsTheSameOne_andDoesNotTranslateItTwice() {
        api.results += result()
        val notes = notes()
        val first = notes.open(address, announce = true)
        val second = notes.open(address, announce = false) // what tapping the notification does
        assertSame(first, second)
        assertEquals("one request for the whole note", 1, api.calls)
    }

    @Test fun openingAnOldNoteFromANotification_doesNotReplaceTheNoteOnTheKeyboard() {
        api.results += result()
        api.results += result()
        val notes = notes()
        val current = notes.open(address, announce = true)
        notes.open("content://alterlingua-capture/note-two.wav", announce = false)
        assertSame(current, notes.latest.value)
    }

    @Test fun closingANote_stopsIt_forgetsItsText_andClearsTheKeyboard() {
        api.results += result()
        val notes = notes()
        val note = notes.open(address, announce = true)
        notes.close(address)
        assertNull(notes.latest.value)
        assertTrue("its speech engine was released", speakers.single().shutdown)
        assertEquals(SharedVoiceState.Closed, note.viewModel.uiState.value)
        assertEquals(CapturedNoteUi.Hidden, capturedNoteUi(note.viewModel.uiState.value))
    }

    @Test fun onlyTheNewestFewNotesAreKept() {
        repeat(3) { api.results += result() }
        val notes = notes(maxKept = 2)
        val addresses = listOf("a", "b", "c").map { "content://alterlingua-capture/note-$it.wav" }
        val opened = addresses.map { notes.open(it, announce = true) }
        assertEquals(SharedVoiceState.Closed, opened[0].viewModel.uiState.value)
        assertTrue(opened[1].viewModel.uiState.value is SharedVoiceState.Result)
        assertTrue(opened[2].viewModel.uiState.value is SharedVoiceState.Result)
    }

    @Test fun dismissingOnTheKeyboard_keepsTheNote_soItsNotificationStillWorks() {
        api.results += result()
        val notes = notes()
        val note = notes.open(address, announce = true)
        notes.dismissOnKeyboard(address)
        assertEquals(address, notes.dismissed.value)
        assertTrue(note.viewModel.uiState.value is SharedVoiceState.Result)
        // A new note is shown again even though the last one was dismissed.
        api.results += result()
        notes.open("content://alterlingua-capture/note-two.wav", announce = true)
        assertNull(notes.dismissed.value)
    }

    @Test fun clearingEverything_forgetsAllNotes() {
        api.results += result()
        api.results += result()
        val notes = notes()
        notes.open(address, announce = true)
        notes.open("content://alterlingua-capture/note-two.wav", announce = true)
        notes.clearAll()
        assertNull(notes.latest.value)
        assertTrue(speakers.all { it.shutdown })
    }

    // ---- what the keyboard shows ----

    @Test fun theKeyboardShowsWorkingWhileTranslating_andNothingWhenThereIsNoNote() {
        assertEquals(CapturedNoteUi.Hidden, capturedNoteUi(null))
        assertEquals(CapturedNoteUi.Working, capturedNoteUi(SharedVoiceState.Idle))
        assertEquals(CapturedNoteUi.Working, capturedNoteUi(SharedVoiceState.Working(SharedVoiceState.Step.TRANSLATING)))
    }

    @Test fun aFailure_isShownWithRetryWhenItCanBeRetried() {
        api.results += VoiceResult.Failure(VoiceFailure.OFFLINE)
        val note = notes().open(address, announce = true)
        val shown = capturedNoteUi(note.viewModel.uiState.value)
        assertTrue(shown is CapturedNoteUi.Failed)
        assertTrue((shown as CapturedNoteUi.Failed).canRetry)
    }

    @Test fun anUnclearNote_isMarkedUnclearOnTheKeyboard() {
        api.results += result(unclear = true)
        val shown = capturedNoteUi(notes().open(address, announce = true).viewModel.uiState.value) as CapturedNoteUi.Result
        assertTrue(shown.unclear)
        assertFalse(shown.sameLanguage)
    }

    @Test fun theTextOfANoteIsNeverPrinted() {
        api.results += result()
        val shown = capturedNoteUi(notes().open(address, announce = true).viewModel.uiState.value)
        assertFalse(shown.toString().contains("supplier"))
    }
}
