package com.alterlingua.app.share

import com.alterlingua.app.R
import com.alterlingua.app.keyboard.messageRes
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.ui.UiText

/** What the image translation screen says when something goes wrong. Nothing here names what the image showed. */
object SharedImageMessages {
    private fun problem(title: Int, message: Int) = ProblemText(UiText(title), UiText(message))

    fun forShare(reason: ImageShareFailure) = when (reason) {
        ImageShareFailure.NO_IMAGE -> problem(R.string.pi_no_image_title, R.string.pi_no_image_msg)
        ImageShareFailure.NOT_A_CONTENT_ADDRESS -> problem(R.string.pi_not_content_title, R.string.pi_not_content_msg)
        ImageShareFailure.NOT_AN_IMAGE -> problem(R.string.pi_not_image_title, R.string.pi_not_image_msg)
        ImageShareFailure.TOO_LARGE -> problem(R.string.pi_too_large_title, R.string.pi_too_large_msg)
        ImageShareFailure.EMPTY -> problem(R.string.pi_empty_title, R.string.pi_empty_msg)
        ImageShareFailure.PERMISSION_DENIED -> problem(R.string.pi_permission_title, R.string.pi_permission_msg)
        ImageShareFailure.UNREADABLE -> problem(R.string.pi_unreadable_title, R.string.pi_unreadable_msg)
    }

    fun forNoText() = problem(R.string.pi_no_text_title, R.string.pi_no_text_msg)

    /** Reuses the keyboard's own per-failure message (already translated, already specific) under one shared title. */
    fun forTranslation(failure: TranslationFailure) = problem(R.string.pi_translate_failed_title, failure.messageRes())

    fun forState(state: SharedImageState.Failed): ProblemText = when {
        state.share != null -> forShare(state.share)
        state.noTextFound -> forNoText()
        else -> forTranslation(state.translation ?: TranslationFailure.TRANSLATION_FAILED)
    }
}
