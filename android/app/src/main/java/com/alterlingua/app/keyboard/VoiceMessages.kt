package com.alterlingua.app.keyboard

import androidx.annotation.StringRes
import com.alterlingua.app.R
import com.alterlingua.app.translation.VoiceFailure

/** The words shown for each way voice input can fail. */
@StringRes
fun VoiceFailure.messageRes(): Int = when (this) {
    VoiceFailure.NOT_AVAILABLE_HERE -> R.string.failure_voice_not_available_here
    VoiceFailure.MIC_UNAVAILABLE -> R.string.failure_voice_mic_unavailable
    VoiceFailure.RECORDING_FAILED -> R.string.failure_voice_recording_failed
    VoiceFailure.TOO_SHORT -> R.string.failure_voice_too_short
    VoiceFailure.NO_SPEECH -> R.string.failure_voice_no_speech
    VoiceFailure.UNCLEAR_SPEECH -> R.string.failure_voice_unclear_speech
    VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE -> R.string.failure_voice_unsupported_speech_language
    VoiceFailure.UNSUPPORTED_TARGET -> R.string.failure_voice_unsupported_target
    VoiceFailure.RECORDING_TOO_LONG -> R.string.failure_voice_recording_too_long
    VoiceFailure.AUDIO_REJECTED -> R.string.failure_voice_audio_rejected
    VoiceFailure.OFFLINE -> R.string.failure_voice_offline
    VoiceFailure.BACKEND_UNAVAILABLE -> R.string.failure_voice_backend_unavailable
    VoiceFailure.TIMEOUT -> R.string.failure_voice_timeout
    VoiceFailure.TRANSLATION_FAILED -> R.string.failure_voice_translation_failed
    VoiceFailure.NOT_CONFIGURED -> R.string.failure_voice_not_configured
    VoiceFailure.PARTIAL -> R.string.failure_voice_partial
    VoiceFailure.NO_VOICE_FOR_TARGET -> R.string.failure_voice_no_voice_for_target
}
