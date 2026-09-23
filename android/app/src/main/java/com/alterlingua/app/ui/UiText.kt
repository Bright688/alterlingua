package com.alterlingua.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text that will be shown to the user, held as a string resource plus its arguments instead of as finished text. Code that decides
 * what to say (which does not know the app language) returns this; the screen turns it into words in the app language when it
 * draws it. It also makes the decision testable without a phone.
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    /** The words, in the language of [context]. */
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

/** The words, in the app language. */
@Composable
fun UiText.string(): String = stringResource(id, *args.toTypedArray())
