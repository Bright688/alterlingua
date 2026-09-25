package com.alterlingua.app.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.CandidateExtractor
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.MeaningProvider
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.MasteryCalculator
import com.alterlingua.app.storage.UserSettingsRepository
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Hands the transcript to the learning pipeline; true when units were saved into the Personal Language Map. */
fun interface VoiceNoteLearning {
    suspend fun learn(interaction: TranslationInteraction): Boolean
}

/** A word or phrase from the voice note worth learning. */
data class UsefulUnit(val text: String, val type: UnitType, val meaning: String?, val state: MasteryStatus)

/** What the Voice Translation screen shows for a finished voice note. */
data class VoiceNoteResult(
    /** The language the voice note was spoken in; null when the backend named one AlterLingua does not list. */
    val originalLanguage: Language?,
    val originalCode: String,
    val transcript: String,
    val userLanguage: Language,
    val translation: String,
    /** The voice note is already in the user's own language, so the translation is the same text. */
    val alreadyInYourLanguage: Boolean,
    val usefulUnits: List<UsefulUnit>,
    /** True when those units were saved into the Personal Language Map. */
    val savedToMap: Boolean,
    val canListen: Boolean,
    val playing: Boolean = false,
    /** The recording was unclear, so some words of the transcript may be wrong (shown to the user as a notice). */
    val unclear: Boolean = false,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "VoiceNoteResult(redacted)"
}

sealed interface SharedVoiceState {
    data object Idle : SharedVoiceState

    enum class Step { READING, TRANSLATING, FINDING_LANGUAGE }

    data class Working(val step: Step) : SharedVoiceState

    data class Result(val value: VoiceNoteResult) : SharedVoiceState

    /** Exactly one of [share] and [voice] is set. [heard] is the transcript when words were understood but not translated. */
    data class Failed(val share: ShareFailure? = null, val voice: VoiceFailure? = null, val canRetry: Boolean = false, val heard: String? = null) : SharedVoiceState

    /** The user cancelled. */
    data object Closed : SharedVoiceState
}

/**
 * The incoming voice-note flow (CLAUDE.md 22): a shared audio item is read (`content://` only), sent to the backend to be
 * transcribed and translated into the user's own language, and shown for reading and listening. Useful words are offered
 * for review and, when the voice note is in the language being learned, saved into that language's Personal Language Map.
 *
 * The audio copy is deleted as soon as the backend has answered (or the user cancels, or this screen goes away); it is
 * kept only while a Retry is possible. Transcript and translation are held in memory only and are never logged.
 */
class SharedVoiceViewModel(
    private val reader: SharedAudioReader,
    private val api: VoiceApi,
    private val settings: UserSettingsRepository,
    private val analyzers: AnalyzerRegistry,
    private val map: LanguageMapService,
    private val meanings: MeaningProvider,
    private val learning: VoiceNoteLearning,
    private val speaker: Speaker,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestTimeoutMillis: Long = 90_000,
    private val meaningTimeoutMillis: Long = 8_000,
    private val calculator: MasteryCalculator = MasteryCalculator(),
) : ViewModel() {

    private val state = MutableStateFlow<SharedVoiceState>(SharedVoiceState.Idle)
    val uiState: StateFlow<SharedVoiceState> = state.asStateFlow()

    private var job: Job? = null
    private var address: String? = null
    private var kept: SharedAudio? = null
    private var started = false

    /** Begins with the shared item's address. Ignored if a flow for this screen already began (rotation). */
    fun start(address: String?, force: Boolean = false) {
        if (started && !force) return
        started = true
        cancelWork()
        deleteKept()
        this.address = address
        run(fromStart = true)
    }

    fun retry() {
        if (state.value !is SharedVoiceState.Failed) return
        run(fromStart = kept == null)
    }

    /** Cancel and close buttons: stops everything and deletes the audio copy. */
    fun cancel() {
        cancelWork()
        speaker.stop()
        deleteKept()
        state.value = SharedVoiceState.Closed
    }

    fun listen() {
        val current = (state.value as? SharedVoiceState.Result)?.value ?: return
        if (!current.canListen) return
        if (current.playing) {
            speaker.stop()
            setResult(current.copy(playing = false))
        } else if (speaker.speak(current.translation, current.userLanguage) { (state.value as? SharedVoiceState.Result)?.let { setResult(it.value.copy(playing = false)) } }) {
            setResult(current.copy(playing = true))
        }
    }

    private fun setResult(value: VoiceNoteResult) {
        state.value = SharedVoiceState.Result(value)
    }

    private fun run(fromStart: Boolean) {
        cancelWork()
        job = viewModelScope.launch {
            try {
                process(fromStart)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                deleteKept()
                state.value = SharedVoiceState.Failed(voice = VoiceFailure.TRANSLATION_FAILED, canRetry = false)
            }
        }
    }

    private suspend fun process(fromStart: Boolean) {
        val prefs = settings.settings.first()
        val native = prefs.nativeLanguage

        var audio = kept
        if (audio == null || fromStart) {
            state.value = SharedVoiceState.Working(SharedVoiceState.Step.READING)
            withContext(io) { reader.sweep() }
            when (val read = withContext(io) { reader.read(address) }) {
                is AudioReadResult.Failure -> {
                    state.value = SharedVoiceState.Failed(share = read.reason)
                    return
                }
                is AudioReadResult.Success -> audio = read.audio
            }
            kept = audio
        }

        state.value = SharedVoiceState.Working(SharedVoiceState.Step.TRANSLATING)
        val answer = try {
            // The spoken language is detected automatically. If that fails on an unclear recording, the service tries again in
            // the languages the user works with: the one they are learning first (a voice note they share is most likely in
            // it), then their own.
            val hints = listOf(prefs.targetLanguage.code, native.code).distinct()
            withTimeout(requestTimeoutMillis) { api.translate(audio.file, audio.contentType, source = "auto", target = native.code, hints = hints) }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            VoiceResult.Failure(VoiceFailure.TIMEOUT)
        }
        when (answer) {
            is VoiceResult.Failure -> {
                // The copy stays only while Retry can use it.
                if (!answer.failure.canRetry) deleteKept()
                state.value = SharedVoiceState.Failed(voice = answer.failure, canRetry = answer.failure.canRetry, heard = answer.heard)
            }
            is VoiceResult.Success -> {
                deleteKept() // processed: the audio is no longer needed
                state.value = SharedVoiceState.Working(SharedVoiceState.Step.FINDING_LANGUAGE)
                setResult(buildResult(answer.value.sourceLanguage, answer.value.transcript, answer.value.translation, native, prefs.learningFromMessagesEnabled, answer.value.unclear))
            }
        }
    }

    private suspend fun buildResult(sourceCode: String, transcript: String, translation: String, native: Language, learningEnabled: Boolean, unclear: Boolean): VoiceNoteResult {
        val original = Languages.fromCode(sourceCode)
        val same = sourceCode == native.code
        var units = emptyList<LearningCandidate>()
        var saved = false
        if (!same && original != null) {
            units = usefulCandidates(transcript, original, native)
            // Only a voice note in the language being learned teaches that language; the pipeline decides (and honours the
            // user's "learn from messages" choice).
            saved = learning.learn(TranslationInteraction(InteractionKind.INCOMING_VOICE, transcript, sourceCode))
        }
        val shown = coroutineScope {
            units.map { candidate ->
                async {
                    val known = map.item(candidate.key)
                    var meaning = known?.takeIf { it.meaningLanguage == native.code }?.meaning?.takeIf { it.isNotBlank() }
                    if (meaning == null && original != null) {
                        meaning = withTimeoutOrNull(meaningTimeoutMillis) { meanings.meaningOf(candidate.surface, original, native) }
                        if (meaning != null && saved) map.setMeaning(candidate.key, meaning, native.code)
                    }
                    val item = map.item(candidate.key)
                    val state = item?.let { calculator.evaluate(it.evidence, clock()).state } ?: MasteryStatus.UNKNOWN
                    UsefulUnit(candidate.surface, candidate.type, meaning, state)
                }
            }.awaitAll()
        }
        val canListen = speaker.canSpeak(native)
        return VoiceNoteResult(original, sourceCode, transcript, native, translation, same, shown, saved && learningEnabled, canListen, unclear = unclear)
    }

    /** The few most useful words or phrases in the voice note, without overlaps. */
    private fun usefulCandidates(transcript: String, language: Language, native: Language): List<LearningCandidate> {
        val analyzer = analyzers.forLanguage(language) ?: return emptyList()
        val candidates = CandidateExtractor(analyzer.profile(language)).extract(analyzer.analyze(transcript, language), native.code, clock())
        val chosen = mutableListOf<LearningCandidate>()
        for (candidate in candidates.sortedByDescending { it.usefulness.score }) {
            if (chosen.size == MAX_UNITS) break
            if (chosen.none { it.normalized.contains(candidate.normalized) || candidate.normalized.contains(it.normalized) }) chosen += candidate
        }
        return chosen
    }

    private fun cancelWork() {
        job?.cancel()
        job = null
    }

    private fun deleteKept() {
        kept?.file?.delete()
        kept = null
    }

    override fun onCleared() {
        cancelWork()
        deleteKept()
        speaker.stop()
        speaker.shutdown()
    }

    companion object {
        const val MAX_UNITS = 3
    }
}
