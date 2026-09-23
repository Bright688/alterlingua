package com.alterlingua.app.keyboard

import androidx.annotation.StringRes
import com.alterlingua.app.R
import com.alterlingua.app.translation.TranslationFailure

/** The words shown for each way a translation can fail. Every message says the user's text is safe where that matters. */
@StringRes
fun TranslationFailure.messageRes(canRestore: Boolean = false): Int = when (this) {
    TranslationFailure.EMPTY_TEXT -> R.string.failure_empty_text
    TranslationFailure.TEXT_TOO_LONG -> R.string.failure_text_too_long
    TranslationFailure.UNSUPPORTED_EDITOR -> R.string.failure_unsupported_editor
    TranslationFailure.PASSWORD_FIELD -> R.string.failure_password_field
    TranslationFailure.NOT_CONFIGURED -> R.string.failure_not_configured
    TranslationFailure.OFFLINE -> R.string.failure_offline
    TranslationFailure.BACKEND_UNAVAILABLE -> R.string.failure_backend_unavailable
    TranslationFailure.TIMEOUT -> R.string.failure_timeout
    TranslationFailure.TRANSLATION_FAILED -> R.string.failure_translation_failed
    TranslationFailure.UNSUPPORTED_LANGUAGE -> R.string.failure_unsupported_language
    TranslationFailure.UNSUPPORTED_PAIR -> R.string.failure_unsupported_pair
    TranslationFailure.SOURCE_UNDETECTED -> R.string.failure_source_undetected
    TranslationFailure.TEXT_CHANGED -> R.string.failure_text_changed
    TranslationFailure.APPLY_FAILED -> if (canRestore) R.string.failure_apply_failed_restore else R.string.failure_apply_failed
    TranslationFailure.UNDO_UNAVAILABLE -> R.string.failure_undo_unavailable
}
