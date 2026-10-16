package com.alterlingua.app.translation

import java.io.File

/** Why a shared PDF or Word (.docx) file was not turned into a translated file. */
enum class DocumentFailure(val canRetry: Boolean) {
    /** Not a PDF or a Word (.docx) file, or its bytes do not match what it claims to be. */
    UNSUPPORTED_DOCUMENT_TYPE(false),
    DOCUMENT_TOO_LARGE(false),

    /** Empty, unreadable, password-protected, or has no translatable text (for example a scanned, image-only PDF). */
    INVALID_DOCUMENT(false),

    /** More translatable paragraphs (or, for a PDF, pages) than one request will process. */
    DOCUMENT_TOO_COMPLEX(false),
    UNSUPPORTED_SOURCE_LANGUAGE(false),
    UNSUPPORTED_TARGET(false),
    SAME_LANGUAGE(false),
    OFFLINE(true),
    BACKEND_UNAVAILABLE(true),
    TIMEOUT(true),
    TRANSLATION_FAILED(true),
    NOT_CONFIGURED(false),
}

/**
 * A translated document, exactly as the backend returned it: real file bytes of the same format as the upload
 * (a translated PDF stays a PDF, a translated DOCX stays a DOCX).
 */
class TranslatedDocument(
    val bytes: ByteArray,
    val contentType: String,
    val sourceLanguage: String,
    val targetLanguage: String,
) {
    /** Never printed: this holds the translated document's bytes, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslatedDocument(redacted)"
}

sealed interface DocumentResult {
    data class Success(val value: TranslatedDocument) : DocumentResult

    data class Failure(val failure: DocumentFailure) : DocumentResult
}

/** The document translation service, as seen by the shared-document Share flow. */
interface DocumentApi {
    /** Uploads [document] (of [contentType], either a PDF or a Word .docx file) and asks for it translated into [target]. */
    suspend fun translate(document: File, contentType: String, source: String, target: String): DocumentResult
}
