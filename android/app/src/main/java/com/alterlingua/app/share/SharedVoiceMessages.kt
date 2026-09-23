package com.alterlingua.app.share

import com.alterlingua.app.R
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.ui.UiText

/** What the voice screens say when something goes wrong, as text in the app language. Nothing here contains any of the audio's content. */
data class ProblemText(val title: UiText, val message: UiText)

object SharedVoiceMessages {
    private fun problem(title: Int, message: Int) = ProblemText(UiText(title), UiText(message))

    fun forShare(reason: ShareFailure) = when (reason) {
        ShareFailure.NO_AUDIO -> problem(R.string.pv_no_audio_title, R.string.pv_no_audio_msg)
        ShareFailure.NOT_A_CONTENT_ADDRESS -> problem(R.string.pv_not_content_title, R.string.pv_not_content_msg)
        ShareFailure.NOT_AUDIO -> problem(R.string.pv_not_audio_title, R.string.pv_not_audio_msg)
        ShareFailure.TOO_LARGE -> problem(R.string.pv_too_large_title, R.string.pv_too_large_msg)
        ShareFailure.EMPTY -> problem(R.string.pv_empty_title, R.string.pv_empty_msg)
        ShareFailure.PERMISSION_DENIED -> problem(R.string.pv_permission_title, R.string.pv_permission_msg)
        ShareFailure.UNREADABLE -> problem(R.string.pv_unreadable_title, R.string.pv_unreadable_msg)
    }

    fun forVoice(failure: VoiceFailure) = when (failure) {
        VoiceFailure.NO_SPEECH, VoiceFailure.UNCLEAR_SPEECH, VoiceFailure.TOO_SHORT -> problem(R.string.pv_unclear_title, R.string.pv_unclear_msg)
        VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE -> problem(R.string.pv_unsupported_speech_title, R.string.pv_unsupported_speech_msg)
        VoiceFailure.UNSUPPORTED_TARGET -> problem(R.string.pv_unsupported_target_title, R.string.pv_unsupported_target_msg)
        VoiceFailure.RECORDING_TOO_LONG -> problem(R.string.pv_too_large_title, R.string.pv_too_long_msg)
        VoiceFailure.AUDIO_REJECTED -> problem(R.string.pv_audio_rejected_title, R.string.pv_audio_rejected_msg)
        VoiceFailure.OFFLINE -> problem(R.string.pv_offline_title, R.string.pv_offline_msg)
        VoiceFailure.BACKEND_UNAVAILABLE, VoiceFailure.TIMEOUT -> problem(R.string.pv_unreachable_title, R.string.pv_unreachable_msg)
        VoiceFailure.NOT_CONFIGURED -> problem(R.string.pv_not_configured_title, R.string.pv_not_configured_msg)
        VoiceFailure.PARTIAL -> problem(R.string.pv_partial_title, R.string.pv_partial_msg)
        VoiceFailure.NO_VOICE_FOR_TARGET -> problem(R.string.pv_no_voice_title, R.string.pv_no_voice_msg)
        else -> problem(R.string.pv_generic_title, R.string.pv_generic_msg)
    }

    fun forState(state: SharedVoiceState.Failed): ProblemText = state.share?.let(::forShare) ?: forVoice(state.voice ?: VoiceFailure.TRANSLATION_FAILED)
}
