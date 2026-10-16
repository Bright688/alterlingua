package com.alterlingua.app.share

import com.alterlingua.app.R
import com.alterlingua.app.keyboard.messageRes
import com.alterlingua.app.translation.DocumentFailure
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.ui.UiText

/** Maps a [DocumentFailure] onto the keyboard's existing, already-translated [TranslationFailure] message when the
 * underlying problem is the same, so that message is not duplicated. Returns null for the document-specific
 * problems (an unsupported file type, too large, no translatable text, too complex), which have their own strings. */
private fun DocumentFailure.asTranslationFailure(): TranslationFailure? = when (this) {
    DocumentFailure.OFFLINE -> TranslationFailure.OFFLINE
    DocumentFailure.BACKEND_UNAVAILABLE -> TranslationFailure.BACKEND_UNAVAILABLE
    DocumentFailure.TIMEOUT -> TranslationFailure.TIMEOUT
    DocumentFailure.NOT_CONFIGURED -> TranslationFailure.NOT_CONFIGURED
    DocumentFailure.UNSUPPORTED_SOURCE_LANGUAGE, DocumentFailure.UNSUPPORTED_TARGET -> TranslationFailure.UNSUPPORTED_LANGUAGE
    // SAME_LANGUAGE cannot actually happen from this client (the source is always "auto"), kept only so every
    // DocumentFailure has a defined message.
    DocumentFailure.TRANSLATION_FAILED, DocumentFailure.SAME_LANGUAGE -> TranslationFailure.TRANSLATION_FAILED
    DocumentFailure.UNSUPPORTED_DOCUMENT_TYPE, DocumentFailure.DOCUMENT_TOO_LARGE,
    DocumentFailure.INVALID_DOCUMENT, DocumentFailure.DOCUMENT_TOO_COMPLEX -> null
}

/** What the document translation screen says when something goes wrong. Nothing here names the document's content. */
object SharedDocumentMessages {
    private fun problem(title: Int, message: Int) = ProblemText(UiText(title), UiText(message))

    fun forShare(reason: DocumentShareFailure) = when (reason) {
        DocumentShareFailure.NO_DOCUMENT -> problem(R.string.pd_no_document_title, R.string.pd_no_document_msg)
        DocumentShareFailure.NOT_A_CONTENT_ADDRESS -> problem(R.string.pd_not_content_title, R.string.pd_not_content_msg)
        DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE -> problem(R.string.pd_unsupported_type_title, R.string.pd_unsupported_type_msg)
        DocumentShareFailure.TOO_LARGE -> problem(R.string.pd_too_large_title, R.string.pd_too_large_msg)
        DocumentShareFailure.EMPTY -> problem(R.string.pd_empty_title, R.string.pd_empty_msg)
        DocumentShareFailure.PERMISSION_DENIED -> problem(R.string.pd_permission_title, R.string.pd_permission_msg)
        DocumentShareFailure.UNREADABLE -> problem(R.string.pd_unreadable_title, R.string.pd_unreadable_msg)
    }

    fun forTranslation(failure: DocumentFailure) = when (failure) {
        DocumentFailure.UNSUPPORTED_DOCUMENT_TYPE -> problem(R.string.pd_unsupported_type_title, R.string.pd_unsupported_type_msg)
        DocumentFailure.DOCUMENT_TOO_LARGE -> problem(R.string.pd_too_large_title, R.string.pd_too_large_msg)
        DocumentFailure.INVALID_DOCUMENT -> problem(R.string.pd_invalid_document_title, R.string.pd_invalid_document_msg)
        DocumentFailure.DOCUMENT_TOO_COMPLEX -> problem(R.string.pd_too_complex_title, R.string.pd_too_complex_msg)
        else -> problem(R.string.pd_translate_failed_title, (failure.asTranslationFailure() ?: TranslationFailure.TRANSLATION_FAILED).messageRes())
    }

    fun forState(state: SharedDocumentState.Failed): ProblemText = when {
        state.share != null -> forShare(state.share)
        else -> forTranslation(state.translation ?: DocumentFailure.TRANSLATION_FAILED)
    }
}
