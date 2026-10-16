package com.alterlingua.app.share

import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import com.alterlingua.app.translation.DocumentApi
import com.alterlingua.app.translation.DocumentFailure
import com.alterlingua.app.translation.DocumentResult
import com.alterlingua.app.translation.TranslatedDocument
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedDocumentViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private val address = "content://com.whatsapp.provider.media/item/1"
    private val pdfBytes = "%PDF-1.4\n%%EOF\n".toByteArray() + ByteArray(50)
    private var bytes: ByteArray? = pdfBytes
    private var declared: String? = DocumentSniffer.PDF
    private var openThrows: Throwable? = null

    private val source = object : AudioSource {
        override fun declaredType(address: String) = declared
        override fun open(address: String): InputStream? {
            openThrows?.let { throw it }
            return bytes?.let { ByteArrayInputStream(it) }
        }
    }

    private class FakeDocumentApi : DocumentApi {
        data class Call(val contentType: String, val source: String, val target: String)
        val calls = mutableListOf<Call>()
        val results = ArrayDeque<DocumentResult>()
        override suspend fun translate(document: File, contentType: String, source: String, target: String): DocumentResult {
            calls += Call(contentType, source, target)
            return results.removeFirst()
        }
    }

    private val api = FakeDocumentApi()
    private val sharedCache by lazy { folder.newFolder("shared") }
    private val outputCache by lazy { folder.newFolder("output") }
    private val outputFiles by lazy { TranslatedDocumentFiles(outputCache) }

    private fun settings(tier: Tier = Tier.PREMIUM, native: Language = Languages.English) =
        FakeUserSettingsRepository(UserSettings(tier = tier, nativeLanguage = native))

    private fun viewModel(prefs: FakeUserSettingsRepository = settings()) = SharedDocumentViewModel(
        reader = SharedDocumentReader(source, sharedCache),
        api = api,
        settings = prefs,
        entitlements = EntitlementService(prefs),
        outputFiles = outputFiles,
        io = Dispatchers.Unconfined,
    )

    private fun ok(translatedBytes: ByteArray = "translated pdf bytes".toByteArray(), contentType: String = DocumentSniffer.PDF, target: String = "en") =
        DocumentResult.Success(TranslatedDocument(translatedBytes, contentType, "fr", target))

    private fun SharedDocumentViewModel.result() = (uiState.value as SharedDocumentState.Result).value
    private fun sharedTemporaryFiles() = sharedCache.listFiles()?.size ?: 0
    private fun outputTemporaryFiles() = outputCache.listFiles()?.size ?: 0

    // ---- the flow from the brief ----

    @Test fun aSharedPdf_isUploadedAndTheTranslatedFileIsWritten() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        val r = vm.result()
        assertEquals(DocumentSniffer.PDF, r.contentType)
        assertEquals(Languages.English, r.targetLanguage)
        assertTrue(r.file.exists())
        assertTrue(r.file.readBytes().contentEquals("translated pdf bytes".toByteArray()))
    }

    @Test fun theBackendIsCalledWithAutoDetectedSource_andTheUsersOwnLanguageAsTarget() {
        api.results += ok(target = "es")
        viewModel(settings(native = Languages.Spanish)).start(address)
        val call = api.calls.single()
        assertEquals("auto", call.source)
        assertEquals("es", call.target)
        assertEquals(DocumentSniffer.PDF, call.contentType)
    }

    @Test fun aSharedDocxOutputsADocxFile() {
        api.results += ok(contentType = DocumentSniffer.DOCX)
        val vm = viewModel()
        vm.start(address)
        assertEquals(DocumentSniffer.DOCX, vm.result().contentType)
        assertTrue(vm.result().file.name.endsWith(".docx"))
    }

    // ---- entitlement gate (Premium only, no trial -- unlike image translation, Standard does not get this) ----

    @Test fun aFreeUser_seesNotEntitled_andNothingIsReadOrUploaded() {
        val vm = viewModel(settings(tier = Tier.FREE))
        vm.start(address)
        assertTrue(vm.uiState.value is SharedDocumentState.NotEntitled)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aStandardUser_alsoSeesNotEntitled() {
        val vm = viewModel(settings(tier = Tier.STANDARD))
        vm.start(address)
        assertTrue(vm.uiState.value is SharedDocumentState.NotEntitled)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aPremiumUser_getsTheFeature() {
        api.results += ok()
        val vm = viewModel(settings(tier = Tier.PREMIUM))
        vm.start(address)
        assertTrue(vm.uiState.value is SharedDocumentState.Result)
    }

    // ---- temporary original document ----

    @Test fun theOriginalCopyIsDeleted_assoonAsUploadFinishes_onSuccess() {
        api.results += ok()
        viewModel().start(address)
        assertEquals(0, sharedTemporaryFiles())
    }

    @Test fun theOriginalCopyIsDeleted_evenWhenTranslationFails() {
        api.results += DocumentResult.Failure(DocumentFailure.OFFLINE)
        viewModel().start(address)
        assertEquals(0, sharedTemporaryFiles())
    }

    @Test fun aTranslationFailure_canBeRetried() {
        api.results += DocumentResult.Failure(DocumentFailure.INVALID_DOCUMENT)
        val vm = viewModel()
        vm.start(address)
        assertEquals(DocumentFailure.INVALID_DOCUMENT, (vm.uiState.value as SharedDocumentState.Failed).translation)
        assertFalse((vm.uiState.value as SharedDocumentState.Failed).canRetry) // not a retryable failure
        api.results += ok()
        vm.retry()
        assertTrue(vm.uiState.value is SharedDocumentState.Result)
    }

    @Test fun aRetryableFailure_offersRetry() {
        api.results += DocumentResult.Failure(DocumentFailure.BACKEND_UNAVAILABLE)
        val vm = viewModel()
        vm.start(address)
        assertTrue((vm.uiState.value as SharedDocumentState.Failed).canRetry)
    }

    // ---- the translated file's lifecycle ----

    @Test fun cancellingAfterAResult_deletesTheTranslatedFile() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        assertEquals(1, outputTemporaryFiles())
        vm.cancel()
        assertEquals(0, outputTemporaryFiles())
        assertTrue(vm.uiState.value is SharedDocumentState.Closed)
    }

    @Test fun keepingTheProducedFile_meansCancelDoesNotDeleteIt() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        vm.keepProducedFile()
        vm.cancel()
        assertEquals(1, outputTemporaryFiles())
    }

    @Test fun aNewShare_discardsAnUnsavedPreviousResult() {
        api.results += ok(translatedBytes = "first".toByteArray())
        api.results += ok(translatedBytes = "second".toByteArray())
        val vm = viewModel()
        vm.start(address)
        assertEquals(1, outputTemporaryFiles())
        vm.start(address, force = true)
        assertEquals(1, outputTemporaryFiles()) // the first was discarded, not left behind
        assertTrue(vm.result().file.readBytes().contentEquals("second".toByteArray()))
    }

    // ---- refusals ----

    @Test fun aFileAddress_isRefused_andNothingIsUploaded() {
        val vm = viewModel()
        vm.start("file:///data/data/com.whatsapp/x.pdf")
        assertEquals(DocumentShareFailure.NOT_A_CONTENT_ADDRESS, (vm.uiState.value as SharedDocumentState.Failed).share)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aRefusedPermission_showsAProblem() {
        openThrows = SecurityException("expired")
        val vm = viewModel()
        vm.start(address)
        assertEquals(DocumentShareFailure.PERMISSION_DENIED, (vm.uiState.value as SharedDocumentState.Failed).share)
    }

    @Test fun anUnsupportedFile_isNeverUploaded() {
        bytes = "just some text".toByteArray()
        val vm = viewModel()
        vm.start(address)
        assertEquals(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE, (vm.uiState.value as SharedDocumentState.Failed).share)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aShareWithNoItem_showsAProblem() {
        val vm = viewModel()
        vm.start(null)
        assertEquals(DocumentShareFailure.NO_DOCUMENT, (vm.uiState.value as SharedDocumentState.Failed).share)
    }

    // ---- lifecycle ----

    @Test fun aRotation_doesNotStartASecondUpload() {
        api.results += ok()
        val vm = viewModel()
        vm.start(address)
        vm.start(address)
        assertEquals(1, api.calls.size)
    }
}
