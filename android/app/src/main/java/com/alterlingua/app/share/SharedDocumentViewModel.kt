package com.alterlingua.app.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Feature
import com.alterlingua.app.entitlements.TierPolicy
import com.alterlingua.app.learning.Language
import com.alterlingua.app.storage.UserSettingsRepository
import com.alterlingua.app.translation.DocumentApi
import com.alterlingua.app.translation.DocumentFailure
import com.alterlingua.app.translation.DocumentResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A translated document ready to be opened or shared, exactly as the backend produced it. */
data class TranslatedDocumentResult(
    val file: File,
    val contentType: String,
    val targetLanguage: Language,
) {
    /** Never printed: this points at private translated content, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslatedDocumentResult(redacted)"
}

sealed interface SharedDocumentState {
    data object Idle : SharedDocumentState

    enum class Step { READING, UPLOADING }

    data class Working(val step: Step) : SharedDocumentState

    data class Result(val value: TranslatedDocumentResult) : SharedDocumentState

    /** Exactly one of [share] and [translation] describes the problem. */
    data class Failed(
        val share: DocumentShareFailure? = null,
        val translation: DocumentFailure? = null,
        val canRetry: Boolean = false,
    ) : SharedDocumentState

    /** FREE and Standard: document file translation is Premium only, with no trial (CLAUDE.md section 27). */
    data object NotEntitled : SharedDocumentState

    /** The user cancelled. */
    data object Closed : SharedDocumentState
}

/**
 * The document translation flow: a shared PDF or Word (.docx) file is uploaded to the backend, which returns a real
 * translated file of the same format. Premium only; FREE and Standard see [SharedDocumentState.NotEntitled].
 *
 * The uploaded original is deleted the moment the upload attempt finishes (success or failure) -- it is never
 * needed again locally. The translated file this flow produces is kept only until the user acts on it (Save/Share)
 * or leaves the screen; [cancel] deletes it, and a start-up sweep removes anything a crash left behind.
 */
class SharedDocumentViewModel(
    private val reader: SharedDocumentReader,
    private val api: DocumentApi,
    private val settings: UserSettingsRepository,
    private val entitlements: EntitlementService,
    private val outputFiles: TranslatedDocumentFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow<SharedDocumentState>(SharedDocumentState.Idle)
    val uiState: StateFlow<SharedDocumentState> = state.asStateFlow()

    private var job: Job? = null
    private var address: String? = null
    private var started = false
    private var producedFile: File? = null

    /** Begins with the shared item's address. Ignored if a flow for this screen already began (rotation). */
    fun start(address: String?, force: Boolean = false) {
        if (started && !force) return
        started = true
        this.address = address
        run()
    }

    fun retry() {
        if (state.value !is SharedDocumentState.Failed) return
        run()
    }

    fun cancel() {
        job?.cancel()
        job = null
        outputFiles.delete(producedFile)
        producedFile = null
        state.value = SharedDocumentState.Closed
    }

    /** Called once the translated file has been handed to Android's Share sheet or otherwise acted on, so a later
     * [cancel] does not also delete a file the user may now have saved a reference to outside this screen. */
    fun keepProducedFile() {
        producedFile = null
    }

    private fun run() {
        job?.cancel()
        // Discards a previous result the user never saved or shared (for example a new share replacing this one)
        // before starting again; a retry after a failure never has one to discard.
        outputFiles.delete(producedFile)
        producedFile = null
        job = viewModelScope.launch {
            try {
                process()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = SharedDocumentState.Failed(translation = DocumentFailure.TRANSLATION_FAILED, canRetry = true)
            }
        }
    }

    private suspend fun process() {
        val tier = entitlements.tier()
        if (!TierPolicy.includes(tier, Feature.DOCUMENT_TRANSLATION)) {
            state.value = SharedDocumentState.NotEntitled
            return
        }

        state.value = SharedDocumentState.Working(SharedDocumentState.Step.READING)
        withContext(io) {
            reader.sweep()
            outputFiles.sweep()
        }
        val document = when (val read = withContext(io) { reader.read(address) }) {
            is DocumentReadResult.Failure -> {
                state.value = SharedDocumentState.Failed(share = read.reason)
                return
            }
            is DocumentReadResult.Success -> read.document
        }

        state.value = SharedDocumentState.Working(SharedDocumentState.Step.UPLOADING)
        val native = settings.settings.first().nativeLanguage
        val result = try {
            withContext(io) { api.translate(document.file, document.contentType, source = "auto", target = native.code) }
        } finally {
            document.file.delete() // the original is never needed again, whatever happens next
        }

        when (result) {
            is DocumentResult.Success -> {
                val translated = result.value
                val output = withContext(io) {
                    outputFiles.newFile(TranslatedDocumentFiles.extensionFor(translated.contentType)).apply {
                        writeBytes(translated.bytes)
                    }
                }
                producedFile = output
                state.value = SharedDocumentState.Result(TranslatedDocumentResult(output, translated.contentType, native))
            }
            is DocumentResult.Failure -> {
                state.value = SharedDocumentState.Failed(translation = result.failure, canRetry = result.failure.canRetry)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
