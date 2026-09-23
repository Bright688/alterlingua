package com.alterlingua.app.translation

import java.io.File

/** Why a voice message was not turned into a translation. */
enum class VoiceFailure(val canRetry: Boolean) {
    /** Passwords and fields that only take key presses. */
    NOT_AVAILABLE_HERE(false),

    /** Another app is using the microphone, or Android refused to start recording. */
    MIC_UNAVAILABLE(false),
    RECORDING_FAILED(false),
    TOO_SHORT(false),

    /** Nothing but silence, or nothing recognised. */
    NO_SPEECH(false),

    /** Speech was heard but the language could not be told. */
    UNCLEAR_SPEECH(false),
    UNSUPPORTED_SPEECH_LANGUAGE(false),
    UNSUPPORTED_TARGET(false),
    RECORDING_TOO_LONG(false),
    AUDIO_REJECTED(false),
    OFFLINE(true),
    BACKEND_UNAVAILABLE(true),
    TIMEOUT(true),
    TRANSLATION_FAILED(true),
    NOT_CONFIGURED(false),

    /** The words were recognised but could not be translated. */
    PARTIAL(false),

    /** The translation exists but no voice for the target language is available to speak it. */
    NO_VOICE_FOR_TARGET(false),
}

/** What the backend answered for a recording. */
data class VoiceTranslation(val sourceLanguage: String, val transcript: String, val targetLanguage: String, val translation: String) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "VoiceTranslation(redacted)"
}

sealed interface VoiceResult {
    data class Success(val value: VoiceTranslation) : VoiceResult

    /** [heard] is what was recognised, when the words were understood but something later failed. */
    data class Failure(val failure: VoiceFailure, val heard: String? = null) : VoiceResult
}

/** The voice translation service, as seen by the keyboard. */
interface VoiceApi {
    /** Sends [audio] (of [contentType]) spoken in [source] and asks for it translated into [target]. */
    suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult
}
