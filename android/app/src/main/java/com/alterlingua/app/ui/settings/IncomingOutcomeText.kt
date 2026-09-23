package com.alterlingua.app.ui.settings

import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.notifications.IncomingOutcome
import com.alterlingua.app.notifications.IncomingOutcomeKind
import com.alterlingua.app.ui.UiText

/** One plain sentence about how the latest incoming message ended. It never contains any message text. */
fun incomingOutcomeText(outcome: IncomingOutcome): UiText {
    val language = outcome.sourceLanguage?.let { Languages.fromCode(it)?.displayName ?: it.uppercase() }
    return when (outcome.kind) {
        IncomingOutcomeKind.TRANSLATED ->
            if (language != null) UiText(R.string.set_last_translated_from, language) else UiText(R.string.set_last_translated)
        IncomingOutcomeKind.ALREADY_IN_YOUR_LANGUAGE -> UiText(R.string.set_last_same_language)
        IncomingOutcomeKind.HIDDEN_CONTENT -> UiText(R.string.set_last_hidden)
        IncomingOutcomeKind.NOTIFICATIONS_BLOCKED -> UiText(R.string.set_last_notifications_off)
        IncomingOutcomeKind.OFFLINE -> UiText(R.string.set_last_offline)
        IncomingOutcomeKind.BACKEND_UNAVAILABLE -> UiText(R.string.set_last_unreachable)
        IncomingOutcomeKind.TIMEOUT -> UiText(R.string.set_last_timeout)
        IncomingOutcomeKind.TRANSLATION_FAILED -> UiText(R.string.set_last_failed)
        IncomingOutcomeKind.UNSUPPORTED_LANGUAGE -> UiText(R.string.set_last_unsupported)
        IncomingOutcomeKind.LANGUAGE_UNDETECTED -> UiText(R.string.set_last_undetected)
        IncomingOutcomeKind.TOO_LONG -> UiText(R.string.set_last_too_long)
        IncomingOutcomeKind.NOT_CONFIGURED -> UiText(R.string.set_last_not_configured)
    }
}
