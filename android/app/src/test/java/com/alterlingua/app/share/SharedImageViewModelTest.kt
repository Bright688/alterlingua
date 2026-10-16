package com.alterlingua.app.share

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import com.alterlingua.app.translation.Translation
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedImageViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private val address = "content://media/external/images/media/9"
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(1_000)
    private var bytes: ByteArray? = jpegBytes
    private var openThrows: Throwable? = null

    private val source = object : AudioSource {
        override fun declaredType(address: String) = "image/jpeg"
        override fun open(address: String): InputStream? {
            openThrows?.let { throw it }
            return bytes?.let { ByteArrayInputStream(it) }
        }
    }

    private class FakeRecognizer(var text: String? = "Bonjour tout le monde") : ImageTextRecognizer {
        val recognized = mutableListOf<File>()
        override suspend fun recognize(file: File): String? {
            recognized += file
            return text
        }
    }

    private class FakeTranslationApi : TranslationApi {
        val calls = mutableListOf<TranslationRequest>()
        val results = ArrayDeque<TranslationResult>()
        override suspend fun translate(request: TranslationRequest): TranslationResult {
            calls += request
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
        override suspend fun synthesizeToFile(text: String, language: Language, file: File): Boolean = false
        override fun stop() { stopped++ }
        override fun shutdown() { shutdown = true }
    }

    private val recognizer = FakeRecognizer()
    private val api = FakeTranslationApi()
    private val speaker = FakeSpeaker()
    private val cache by lazy { folder.newFolder("cache") }

    private fun settings(tier: Tier = Tier.STANDARD, native: Language = Languages.English) =
        FakeUserSettingsRepository(UserSettings(tier = tier, nativeLanguage = native))

    private fun viewModel(prefs: FakeUserSettingsRepository = settings()) = SharedImageViewModel(
        reader = SharedImageReader(source, cache),
        recognizer = recognizer,
        translationApi = api,
        settings = prefs,
        entitlements = EntitlementService(prefs),
        speaker = speaker,
        io = Dispatchers.Unconfined,
    )

    private fun ok(translation: String = "Hello everyone", target: String = "en") =
        TranslationResult.Success(Translation(translation, "fr", target))

    private fun SharedImageViewModel.result() = (uiState.value as SharedImageState.Result).value
    private fun temporaryFiles() = cache.listFiles()?.size ?: 0

    // ---- the flow from the brief ----

    @Test fun aSharedImageWithText_isRecognisedAndTranslated() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        val r = vm.result()
        assertEquals("Bonjour tout le monde", r.extractedText)
        assertEquals("Hello everyone", r.translation)
        assertEquals(Languages.English, r.targetLanguage)
    }

    @Test fun theBackendIsCalledWithAutoDetectedSource_andTheUsersOwnLanguageAsTarget() {
        api.results += ok()
        viewModel(settings(native = Languages.Spanish)).start(address)
        val call = api.calls.single()
        assertEquals("auto", call.source)
        assertEquals("es", call.target)
        assertEquals("Bonjour tout le monde", call.text)
    }

    // ---- entitlement gate (Standard/Premium only, no FREE trial) ----

    @Test fun aFreeUser_seesNotEntitled_andNothingIsReadOrTranslated() {
        val vm = viewModel(settings(tier = Tier.FREE))
        vm.start(address)
        assertTrue(vm.uiState.value is SharedImageState.NotEntitled)
        assertTrue(api.calls.isEmpty())
        assertTrue(recognizer.recognized.isEmpty())
    }

    @Test fun aStandardOrPremiumUser_getsTheFeature() {
        for (tier in listOf(Tier.STANDARD, Tier.PREMIUM)) {
            api.results += ok()
            val vm = viewModel(settings(tier = tier))
            vm.start(address)
            assertTrue(tier.name, vm.uiState.value is SharedImageState.Result)
        }
    }

    // ---- no text found ----

    @Test fun anImageWithNoText_saysSo_withoutCallingTheBackend() {
        recognizer.text = null
        val vm = viewModel()
        vm.start(address)
        assertTrue((vm.uiState.value as SharedImageState.Failed).noTextFound)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun anImageWithOnlyBlankText_isTreatedTheSame() {
        recognizer.text = "   "
        val vm = viewModel()
        vm.start(address)
        assertTrue((vm.uiState.value as SharedImageState.Failed).noTextFound)
    }

    // ---- temporary image ----

    @Test fun theImageCopyIsDeleted_assoonAsTextHasBeenLookedFor_regardlessOfWhatHappensNext() {
        api.results += ok()
        viewModel().start(address)
        assertEquals(0, temporaryFiles())
    }

    @Test fun whenNoTextIsFound_theCopyIsStillDeleted() {
        recognizer.text = null
        viewModel().start(address)
        assertEquals(0, temporaryFiles())
    }

    @Test fun aTranslationFailure_stillDeletesTheImageCopy_butCanBeRetried() {
        api.results += TranslationResult.Failure(TranslationFailure.OFFLINE)
        val vm = viewModel()
        vm.start(address)
        assertEquals(0, temporaryFiles())
        assertTrue((vm.uiState.value as SharedImageState.Failed).canRetry)
        api.results += ok()
        vm.retry()
        assertTrue(vm.uiState.value is SharedImageState.Result)
    }

    @Test fun cancelling_stopsTheSpeakerAndCloses() {
        recognizer.text = null
        val vm = viewModel()
        vm.start(address)
        vm.cancel()
        assertTrue(vm.uiState.value is SharedImageState.Closed)
    }

    @Test fun whenTheScreenGoesAway_theSpeakerIsShutDown() {
        api.results += ok()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = viewModel() as T
        })[SharedImageViewModel::class]
        vm.start(address)
        store.clear()
        assertTrue(speaker.shutdown)
    }

    // ---- refusals ----

    @Test fun aFileAddress_isRefused_andNothingIsReadOrTranslated() {
        val vm = viewModel()
        vm.start("file:///data/data/com.whatsapp/x.jpg")
        assertEquals(ImageShareFailure.NOT_A_CONTENT_ADDRESS, (vm.uiState.value as SharedImageState.Failed).share)
        assertTrue(api.calls.isEmpty())
        assertTrue(recognizer.recognized.isEmpty())
    }

    @Test fun aRefusedPermission_showsAProblem() {
        openThrows = SecurityException("expired")
        val vm = viewModel()
        vm.start(address)
        assertEquals(ImageShareFailure.PERMISSION_DENIED, (vm.uiState.value as SharedImageState.Failed).share)
    }

    @Test fun nonImageContent_isNeverRecognisedOrUploaded() {
        bytes = "just some text".toByteArray()
        val vm = viewModel()
        vm.start(address)
        assertEquals(ImageShareFailure.NOT_AN_IMAGE, (vm.uiState.value as SharedImageState.Failed).share)
        assertTrue(recognizer.recognized.isEmpty())
    }

    @Test fun aShareWithNoItem_showsAProblem() {
        val vm = viewModel()
        vm.start(null)
        assertEquals(ImageShareFailure.NO_IMAGE, (vm.uiState.value as SharedImageState.Failed).share)
    }

    // ---- Listen ----

    @Test fun listen_readsTheTranslationAloud_inTheUsersLanguage_andTogglesOff() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertTrue(vm.result().canListen)
        vm.listen()
        assertEquals(listOf("Hello everyone" to "en"), speaker.spoken)
        assertTrue(vm.result().playing)
        vm.listen()
        assertFalse(vm.result().playing)
        assertEquals(1, speaker.stopped)
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
        api.results += ok(translation = "Good morning")
        val vm = viewModel()
        vm.start(address)
        vm.start(address, force = true)
        assertEquals(2, api.calls.size)
        assertEquals("Good morning", vm.result().translation)
    }
}
