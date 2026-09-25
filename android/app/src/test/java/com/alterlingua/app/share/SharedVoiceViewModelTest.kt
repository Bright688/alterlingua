package com.alterlingua.app.share

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningContext
import com.alterlingua.app.learning.engine.LearningPipeline
import com.alterlingua.app.learning.engine.PipelineResult
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapExposureStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import com.alterlingua.app.translation.VoiceTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.reflect.KClass

class SharedVoiceViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private val address = "content://com.whatsapp.provider.media/item/9"
    private val oggBytes = "OggS".toByteArray() + ByteArray(1_000)
    private var bytes: ByteArray? = oggBytes
    private var openThrows: Throwable? = null

    private val source = object : AudioSource {
        override fun declaredType(address: String) = "audio/ogg; codecs=opus"
        override fun open(address: String): InputStream? {
            openThrows?.let { throw it }
            return bytes?.let { ByteArrayInputStream(it) }
        }
    }

    private class FakeApi : VoiceApi {
        val calls = mutableListOf<Call>()
        val results = ArrayDeque<VoiceResult>()
        data class Call(val type: String, val source: String, val target: String, val existed: Boolean, val size: Long, val hints: List<String> = emptyList())
        override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult {
            calls += Call(contentType, source, target, audio.exists(), audio.length())
            return results.removeFirst()
        }
        override suspend fun translate(audio: File, contentType: String, source: String, target: String, hints: List<String>): VoiceResult {
            calls += Call(contentType, source, target, audio.exists(), audio.length(), hints)
            return results.removeFirst()
        }
    }

    private class FakeSpeaker(var voice: Boolean = true) : Speaker {
        val spoken = mutableListOf<Pair<String, String>>()
        var stopped = 0
        var shutdown = false
        var finish: (() -> Unit)? = null
        override suspend fun canSpeak(language: Language) = voice
        override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean {
            if (!voice) return false
            spoken += text to language.code
            finish = onDone
            return true
        }
        override suspend fun synthesizeToFile(text: String, language: Language, file: java.io.File): Boolean {
            if (!voice) return false
            file.writeBytes(text.toByteArray())
            return true
        }
        override fun stop() { stopped++ }
        override fun shutdown() { shutdown = true }
    }

    private val api = FakeApi()
    private val speaker = FakeSpeaker()
    private val heard = mutableListOf<TranslationInteraction>()
    private var learned = true
    private val asked = mutableListOf<String>()
    private val meanings = object : MeaningProvider {
        override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? {
            asked += unit
            return "meaning of $unit"
        }
    }
    private val map = LanguageMapService(InMemoryLanguageMapStore())
    private val cache by lazy { folder.newFolder("cache") }

    private fun settings(native: Language = Languages.English, learning: Language = Languages.French, learn: Boolean = true) =
        FakeUserSettingsRepository(UserSettings(nativeLanguage = native, targetLanguage = learning, learningFromMessagesEnabled = learn))

    private fun viewModel(
        prefs: FakeUserSettingsRepository = settings(),
        learning: VoiceNoteLearning = VoiceNoteLearning { heard += it; learned },
    ) = SharedVoiceViewModel(
        reader = SharedAudioReader(source, cache),
        api = api, settings = prefs, analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())), map = map, meanings = meanings,
        learning = learning, speaker = speaker, io = Dispatchers.Unconfined,
    )

    private fun ok(source: String = "fr", transcript: String = transcriptFr, target: String = "en", translation: String = "I just spoke with the supplier; the goods will be available tomorrow.") =
        VoiceResult.Success(VoiceTranslation(source, transcript, target, translation))

    private val transcriptFr = "Je viens de parler au fournisseur, la marchandise sera bien disponible demain."

    private fun SharedVoiceViewModel.result() = (uiState.value as SharedVoiceState.Result).value
    private fun temporaryFiles() = cache.listFiles()?.size ?: 0

    // ---- the flow from the brief ----

    @Test fun aSharedFrenchVoiceNote_isTranslatedIntoTheUsersLanguage_andShown() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        val r = vm.result()
        assertEquals(Languages.French, r.originalLanguage)
        assertEquals(transcriptFr, r.transcript)
        assertEquals(Languages.English, r.userLanguage)
        assertEquals("I just spoke with the supplier; the goods will be available tomorrow.", r.translation)
        assertFalse(r.alreadyInYourLanguage)
    }

    @Test fun theBackendIsCalledWithAutoDetectedSource_theUsersLanguageAsTarget_andTheAudioType() {
        api.results += ok()
        viewModel().start(address)
        val call = api.calls.single()
        assertEquals("audio/ogg", call.type)
        assertEquals("auto", call.source)
        assertEquals("en", call.target)
        assertTrue("the copy existed while it was uploaded", call.existed)
        assertEquals(oggBytes.size.toLong(), call.size)
    }

    @Test fun theLikelyLanguagesAreSentAsHints_theLearningLanguageFirst_soAnUnclearNoteCanBeRetried() {
        // An unclear recording can be mistaken for a language that is not supported; the backend then tries these instead
        // of giving up. A voice note the user shares is most likely in the language they are learning.
        api.results += ok()
        viewModel(settings(native = Languages.English, learning = Languages.French)).start(address)
        assertEquals(listOf("fr", "en"), api.calls.single().hints)
    }

    @Test fun theHintsFollowTheUsersLanguages_notAlwaysFrenchAndEnglish() {
        api.results += ok(target = "es")
        viewModel(settings(native = Languages.Spanish, learning = Languages.Japanese)).start(address)
        assertEquals(listOf("ja", "es"), api.calls.single().hints)
    }

    @Test fun theTargetIsEachUsersOwnLanguage_notAlwaysEnglish() {
        for (native in Languages.supported) {
            api.calls.clear()
            api.results += ok(target = native.code)
            viewModel(settings(native = native, learning = Languages.French)).start(address)
            assertEquals(native.code, api.calls.single().target)
        }
    }

    @Test fun anySupportedSourceLanguage_isShownWithItsOwnName() {
        for (source in listOf("es", "de", "it", "nl", "zh", "ja")) {
            api.results += ok(source = source, transcript = "x", translation = "y")
            val vm = viewModel()
            vm.start(address)
            assertEquals(Languages.fromCode(source), vm.result().originalLanguage)
        }
    }

    // ---- temporary audio ----

    @Test fun afterASuccessfulTranslation_theAudioCopyIsDeleted() {
        api.results += ok()
        viewModel().start(address)
        assertEquals(0, temporaryFiles())
    }

    @Test fun aFailureThatCannotBeRetried_deletesTheAudioAtOnce() {
        api.results += VoiceResult.Failure(VoiceFailure.NO_SPEECH)
        val vm = viewModel()
        vm.start(address)
        assertEquals(0, temporaryFiles())
        assertFalse((vm.uiState.value as SharedVoiceState.Failed).canRetry)
    }

    @Test fun aRetryableFailureKeepsTheCopyForRetry_thenTheSuccessDeletesIt() {
        api.results += VoiceResult.Failure(VoiceFailure.OFFLINE)
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertTrue((vm.uiState.value as SharedVoiceState.Failed).canRetry)
        assertEquals(1, temporaryFiles())
        vm.retry()
        assertTrue(vm.uiState.value is SharedVoiceState.Result)
        assertEquals(0, temporaryFiles())
        assertEquals("the retry used the same copy, no second read", 2, api.calls.size)
        assertTrue(api.calls.all { it.existed })
    }

    @Test fun cancelling_deletesTheCopy() {
        api.results += VoiceResult.Failure(VoiceFailure.TIMEOUT)
        val vm = viewModel()
        vm.start(address)
        assertEquals(1, temporaryFiles())
        vm.cancel()
        assertEquals(0, temporaryFiles())
        assertTrue(vm.uiState.value is SharedVoiceState.Closed)
    }

    @Test fun whenTheScreenGoesAway_theCopyIsDeleted_andTheSpeakerShutDown() {
        api.results += VoiceResult.Failure(VoiceFailure.BACKEND_UNAVAILABLE)
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = viewModel() as T
        })[SharedVoiceViewModel::class]
        vm.start(address)
        assertEquals(1, temporaryFiles())
        store.clear()
        assertEquals(0, temporaryFiles())
        assertTrue(speaker.shutdown)
    }

    // ---- refusals ----

    @Test fun aFileAddress_isRefused_andTheBackendIsNeverCalled() {
        val vm = viewModel()
        vm.start("file:///data/data/com.whatsapp/x.opus")
        assertEquals(ShareFailure.NOT_A_CONTENT_ADDRESS, (vm.uiState.value as SharedVoiceState.Failed).share)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aRefusedPermission_showsAProblem_andNothingIsUploaded() {
        openThrows = SecurityException("expired")
        val vm = viewModel()
        vm.start(address)
        assertEquals(ShareFailure.PERMISSION_DENIED, (vm.uiState.value as SharedVoiceState.Failed).share)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun noneAudioContent_isNeverUploaded() {
        bytes = "just some text".toByteArray()
        val vm = viewModel()
        vm.start(address)
        assertEquals(ShareFailure.NOT_AUDIO, (vm.uiState.value as SharedVoiceState.Failed).share)
        assertTrue(api.calls.isEmpty())
        assertEquals(0, temporaryFiles())
    }

    @Test fun aShareWithNoItem_showsAProblem() {
        val vm = viewModel()
        vm.start(null)
        assertEquals(ShareFailure.NO_AUDIO, (vm.uiState.value as SharedVoiceState.Failed).share)
    }

    // ---- learning signals ----

    @Test fun theTranscriptIsFedToLearning_asAnIncomingVoiceInteraction_inItsOwnLanguage() {
        api.results += ok()
        viewModel().start(address)
        assertEquals(listOf(TranslationInteraction(InteractionKind.INCOMING_VOICE, transcriptFr, "fr")), heard)
    }

    @Test fun whenUnitsAreSaved_theScreenSaysSo_andReviewIsAvailable() {
        api.results += ok()
        learned = true
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().savedToMap)
        assertTrue(vm.result().usefulUnits.isNotEmpty())
    }

    @Test fun aVoiceNoteNotInTheLearningLanguage_isNotSaved() {
        api.results += ok()
        learned = false
        val vm = viewModel()
        vm.start(address)
        assertFalse(vm.result().savedToMap)
        assertTrue("the words are still shown", vm.result().usefulUnits.isNotEmpty())
    }

    @Test fun aVoiceNoteInTheUsersOwnLanguage_isNotTranslated_taughtNothing_andHasNoUnits() {
        api.results += ok(source = "en", transcript = "See you tomorrow at noon", translation = "See you tomorrow at noon")
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().alreadyInYourLanguage)
        assertTrue(heard.isEmpty())
        assertTrue(vm.result().usefulUnits.isEmpty())
    }

    @Test fun atMostThreeUsefulUnits_areShown() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().usefulUnits.size in 1..SharedVoiceViewModel.MAX_UNITS)
    }

    @Test fun unitsCarryTheirMeaningsInTheUsersLanguage() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().usefulUnits.all { it.meaning == "meaning of ${it.text}" })
    }

    @Test fun endToEnd_withTheRealPipeline_theWordsReachTheLanguageMapOfTheLearningLanguage() {
        api.results += ok()
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = true) },
            store = LanguageMapExposureStore(map),
        )
        val vm = viewModel(learning = VoiceNoteLearning { pipeline.record(it) is PipelineResult.Recorded })
        vm.start(address)
        runBlocking {
            val item = map.item(UnitKey("fr", "fournisseur", UnitType.WORD))
            assertNotNull(item)
            assertEquals(1, item!!.exposureCount)
            assertEquals(InteractionKind.INCOMING_VOICE, item.lastContext)
            // Every unit shown was saved with its meaning, so asking for it later needs no connection.
            for (unit in vm.result().usefulUnits) {
                assertEquals("meaning of ${unit.text}", map.item(UnitKey("fr", unit.text.lowercase(), unit.type))?.meaning)
            }
            assertTrue(map.items("es").isEmpty())
        }
        assertTrue(vm.result().savedToMap)
    }

    @Test fun endToEnd_theLearnFromMessagesSwitchStillWins() {
        api.results += ok()
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = false) },
            store = LanguageMapExposureStore(map),
        )
        val vm = viewModel(settings(learn = false), VoiceNoteLearning { pipeline.record(it) is PipelineResult.Recorded })
        vm.start(address)
        runBlocking { assertTrue(map.items("fr").isEmpty()) }
        assertFalse(vm.result().savedToMap)
        assertTrue("still translated", vm.result().translation.isNotEmpty())
    }

    @Test fun theMapHoldsNoTranscript_onlyUnits() {
        api.results += ok()
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = true) },
            store = LanguageMapExposureStore(map),
        )
        viewModel(learning = VoiceNoteLearning { pipeline.record(it) is PipelineResult.Recorded }).start(address)
        runBlocking {
            assertTrue(map.items("fr").none { it.displayForm.length >= transcriptFr.length / 2 })
        }
    }

    // ---- Listen ----

    @Test fun listen_readsTheTranslationAloud_inTheUsersLanguage_andTogglesOff() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().canListen)
        vm.listen()
        assertEquals(listOf(vm.result().translation to "en"), speaker.spoken)
        assertTrue(vm.result().playing)
        vm.listen()
        assertFalse(vm.result().playing)
        assertEquals(1, speaker.stopped)
    }

    @Test fun listen_endsWhenTheSpeechEnds() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        vm.listen()
        speaker.finish!!.invoke()
        assertFalse(vm.result().playing)
    }

    @Test fun withoutAVoiceForTheLanguage_listenIsOffered_asUnavailable() {
        speaker.voice = false
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertFalse(vm.result().canListen)
        vm.listen()
        assertTrue(speaker.spoken.isEmpty())
    }

    // ---- lifecycle ----

    @Test fun aRotation_doesNotStartASecondTranslation() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        vm.start(address)
        assertEquals(1, api.calls.size)
    }

    @Test fun aNewShare_startsAgain() {
        api.results += ok()
        api.results += ok(transcript = "Bonjour", translation = "Hello")
        val vm = viewModel()
        vm.start(address)
        vm.start(address, force = true)
        assertEquals(2, api.calls.size)
        assertEquals("Hello", vm.result().translation)
    }

    @Test fun partialResult_keepsWhatWasHeard() {
        api.results += VoiceResult.Failure(VoiceFailure.PARTIAL, heard = "Bonjour")
        val vm = viewModel()
        vm.start(address)
        assertEquals("Bonjour", (vm.uiState.value as SharedVoiceState.Failed).heard)
    }

    @Test fun everyProblemHasAMessage_andNoneMentionsAudioContent() {
        for (reason in ShareFailure.entries) assertTrue(SharedVoiceMessages.forShare(reason).message.id != 0)
        for (failure in VoiceFailure.entries) assertTrue(SharedVoiceMessages.forVoice(failure).title.id != 0)
        assertNull(SharedVoiceState.Failed(share = ShareFailure.EMPTY).heard)
    }
}
