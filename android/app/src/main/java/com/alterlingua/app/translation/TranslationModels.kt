package com.alterlingua.app.translation

/** What is sent to `POST /v1/translate`. The target always comes from the user's current selection. */
data class TranslationRequest(
    val text: String,
    val target: String,
    val source: String = "auto",
    val context: String = "messaging",
    val tone: String = "natural",
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslationRequest(redacted)"
}

/** A successful answer. */
data class Translation(val text: String, val sourceLanguage: String, val targetLanguage: String) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "Translation(redacted)"
}

/** Why a translation did not happen. In every case the user's own text stays exactly as it was. */
enum class TranslationFailure(val canRetry: Boolean) {
    EMPTY_TEXT(false),
    TEXT_TOO_LONG(false),
    UNSUPPORTED_EDITOR(false),
    PASSWORD_FIELD(false),
    NOT_CONFIGURED(false),
    OFFLINE(true),
    BACKEND_UNAVAILABLE(true),
    TIMEOUT(true),
    TRANSLATION_FAILED(true),
    UNSUPPORTED_LANGUAGE(false),
    UNSUPPORTED_PAIR(false),
    SOURCE_UNDETECTED(false),

    /** The text changed while translating, so the result was not applied. */
    TEXT_CHANGED(true),

    /** The translation could not be put into the text field. */
    APPLY_FAILED(true),
    UNDO_UNAVAILABLE(false),
}

sealed interface TranslationResult {
    data class Success(val translation: Translation) : TranslationResult
    data class Failure(val failure: TranslationFailure) : TranslationResult
}

/** The translation service, as seen by the keyboard. */
interface TranslationApi {
    suspend fun translate(request: TranslationRequest): TranslationResult
}
