package com.alterlingua.app.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.entitlements.EntitlementService
import com.alterlingua.app.entitlements.Feature
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.entitlements.TierPolicy
import com.alterlingua.app.learning.Language
import com.alterlingua.app.storage.UserSettingsRepository
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import com.alterlingua.app.translation.TranslationApi
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

/** What the image translation screen shows for a decoded image's text. */
data class ImageTranslationResult(
    val extractedText: String,
    val translation: String,
    val targetLanguage: Language,
    val canListen: Boolean,
    val playing: Boolean = false,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "ImageTranslationResult(redacted)"
}

sealed interface SharedImageState {
    data object Idle : SharedImageState

    enum class Step { READING, RECOGNIZING, TRANSLATING }

    data class Working(val step: Step) : SharedImageState

    data class Result(val value: ImageTranslationResult) : SharedImageState

    /** Exactly one of [share], [translation] and [noTextFound] describes the problem. */
    data class Failed(
        val share: ImageShareFailure? = null,
        val translation: TranslationFailure? = null,
        val noTextFound: Boolean = false,
        val canRetry: Boolean = false,
    ) : SharedImageState

    /** FREE: image translation is a Standard/Premium feature, with no trial (CLAUDE.md section 27). */
    data object NotEntitled : SharedImageState

    /** The user cancelled. */
    data object Closed : SharedImageState
}

/**
 * The image translation flow: a shared image's text is found entirely on the phone (ML Kit, see
 * [ImageTextRecognizer]) -- the image itself is never uploaded anywhere -- and the text found is translated by the
 * same backend service the keyboard uses. Standard and Premium only; FREE sees [SharedImageState.NotEntitled].
 *
 * The image copy is deleted as soon as it has been read for OCR (the text found afterwards is kept only in memory,
 * never logged); nothing of what the image showed is kept once this screen closes.
 */
class SharedImageViewModel(
    private val reader: SharedImageReader,
    private val recognizer: ImageTextRecognizer,
    private val translationApi: TranslationApi,
    private val settings: UserSettingsRepository,
    private val entitlements: EntitlementService,
    private val speaker: Speaker,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow<SharedImageState>(SharedImageState.Idle)
    val uiState: StateFlow<SharedImageState> = state.asStateFlow()

    private var job: Job? = null
    private var address: String? = null
    private var started = false

    /** Begins with the shared item's address. Ignored if a flow for this screen already began (rotation). */
    fun start(address: String?, force: Boolean = false) {
        if (started && !force) return
        started = true
        this.address = address
        run()
    }

    fun retry() {
        if (state.value !is SharedImageState.Failed) return
        run()
    }

    fun cancel() {
        job?.cancel()
        job = null
        speaker.stop()
        state.value = SharedImageState.Closed
    }

    fun listen() {
        val current = (state.value as? SharedImageState.Result)?.value ?: return
        if (!current.canListen) return
        if (current.playing) {
            speaker.stop()
            setResult(current.copy(playing = false))
        } else if (speaker.speak(current.translation, current.targetLanguage) { (state.value as? SharedImageState.Result)?.let { setResult(it.value.copy(playing = false)) } }) {
            setResult(current.copy(playing = true))
        }
    }

    private fun setResult(value: ImageTranslationResult) {
        state.value = SharedImageState.Result(value)
    }

    private fun run() {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                process()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = SharedImageState.Failed(translation = TranslationFailure.TRANSLATION_FAILED, canRetry = true)
            }
        }
    }

    private suspend fun process() {
        val tier = entitlements.tier()
        if (!TierPolicy.includes(tier, Feature.IMAGE_TRANSLATION)) {
            state.value = SharedImageState.NotEntitled
            return
        }

        state.value = SharedImageState.Working(SharedImageState.Step.READING)
        withContext(io) { reader.sweep() }
        val image = when (val read = withContext(io) { reader.read(address) }) {
            is ImageReadResult.Failure -> {
                state.value = SharedImageState.Failed(share = read.reason)
                return
            }
            is ImageReadResult.Success -> read.image
        }

        state.value = SharedImageState.Working(SharedImageState.Step.RECOGNIZING)
        val text = try {
            withContext(io) { recognizer.recognize(image.file) }
        } finally {
            image.file.delete() // the image itself is never needed again, whatever happens next
        }
        if (text.isNullOrBlank()) {
            state.value = SharedImageState.Failed(noTextFound = true)
            return
        }

        state.value = SharedImageState.Working(SharedImageState.Step.TRANSLATING)
        val native = settings.settings.first().nativeLanguage
        when (val result = translationApi.translate(TranslationRequest(text, native.code, source = "auto"))) {
            is TranslationResult.Success -> setResult(
                ImageTranslationResult(text, result.translation.text, native, speaker.canSpeak(native)),
            )
            is TranslationResult.Failure -> state.value = SharedImageState.Failed(translation = result.failure, canRetry = result.failure.canRetry)
        }
    }

    override fun onCleared() {
        job?.cancel()
        speaker.stop()
        speaker.shutdown()
    }
}
