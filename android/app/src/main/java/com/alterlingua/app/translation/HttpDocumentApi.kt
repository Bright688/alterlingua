package com.alterlingua.app.translation

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Calls the backend's `POST /v1/documents/translate` with a multipart upload. Unlike [HttpVoiceApi], a successful
 * answer's body is the translated file itself (its bytes, with the original's content type), not JSON; the source
 * and target languages come back as response headers instead.
 *
 * PRIVACY: the document and its translation are only ever in memory or in the caller's own temporary file here.
 * Nothing is logged, and the server's error details are reduced to a [DocumentFailure]. Cancelling the calling
 * coroutine closes the connection.
 */
class HttpDocumentApi(
    baseUrl: String,
    private val isOnline: () -> Boolean = { true },
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 90_000,
    /** The server's secret API token (sent as a bearer token); empty means none (a local development server). */
    private val apiToken: String = "",
) : DocumentApi {

    private val root: String? = baseUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }
    private val endpoint: String? = root?.let { "$it/v1/documents/translate" }

    override suspend fun translate(document: File, contentType: String, source: String, target: String): DocumentResult {
        val url = endpoint ?: return DocumentResult.Failure(DocumentFailure.NOT_CONFIGURED)
        return post(url, document, contentType, source, target)
    }

    private suspend fun post(url: String, document: File, contentType: String, source: String, target: String): DocumentResult =
        suspendCancellableCoroutine { continuation ->
            val call = HttpCall()
            continuation.invokeOnCancellation { call.cancel() }
            thread(name = "alterlingua-document", isDaemon = true) {
                val result = exchange(url, document, contentType, source, target, call)
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun exchange(url: String, document: File, contentType: String, source: String, target: String, call: HttpCall): DocumentResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return DocumentResult.Failure(DocumentFailure.NOT_CONFIGURED)
        } catch (_: ClassCastException) {
            return DocumentResult.Failure(DocumentFailure.NOT_CONFIGURED)
        }
        if (!call.attach(connection)) return DocumentResult.Failure(DocumentFailure.TRANSLATION_FAILED)
        return try {
            val boundary = "alterlingua-" + UUID.randomUUID().toString().replace("-", "")
            val filename = if (contentType == "application/pdf") "document.pdf" else "document.docx"
            val head = buildString {
                for ((name, value) in listOf("target" to target, "source" to source, "context" to "messaging", "tone" to "natural")) {
                    append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                append("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: $contentType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "*/*")
            if (apiToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${apiToken.trim()}")
            connection.setFixedLengthStreamingMode(head.size.toLong() + document.length() + tail.size)
            connection.outputStream.use { out ->
                out.write(head)
                document.inputStream().use { it.copyTo(out) }
                out.write(tail)
            }

            val status = connection.responseCode
            if (status in 200..299) {
                val bytes = connection.inputStream.use { readLimited(it, MAX_RESPONSE_BYTES) }
                val answeredType = (connection.getHeaderField("Content-Type") ?: contentType).substringBefore(';').trim()
                val sourceLanguage = connection.getHeaderField("X-Source-Language").orEmpty()
                val targetLanguage = connection.getHeaderField("X-Target-Language").orEmpty()
                when {
                    bytes.isEmpty() || targetLanguage.isBlank() -> DocumentResult.Failure(DocumentFailure.TRANSLATION_FAILED)
                    // Never accept a translation that is not in the language the user chose.
                    !targetLanguage.equals(target, ignoreCase = true) -> DocumentResult.Failure(DocumentFailure.TRANSLATION_FAILED)
                    else -> DocumentResult.Success(TranslatedDocument(bytes, answeredType, sourceLanguage, targetLanguage))
                }
            } else {
                val body = connection.errorStream?.use { readLimited(it, MAX_ERROR_BYTES) }?.toString(Charsets.UTF_8) ?: ""
                DocumentResult.Failure(failureFor(status, body))
            }
        } catch (_: SocketTimeoutException) {
            DocumentResult.Failure(DocumentFailure.TIMEOUT)
        } catch (_: IOException) {
            DocumentResult.Failure(if (isOnline()) DocumentFailure.BACKEND_UNAVAILABLE else DocumentFailure.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() <= maxBytes) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun failureFor(status: Int, body: String): DocumentFailure {
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: JSONException) {
            null
        }
        val code = error?.optString("code")
        val role = error?.optString("role")
        return when (status) {
            413 -> DocumentFailure.DOCUMENT_TOO_LARGE
            415 -> DocumentFailure.UNSUPPORTED_DOCUMENT_TYPE
            422 -> when (code) {
                "invalid_document" -> DocumentFailure.INVALID_DOCUMENT
                "document_too_complex" -> DocumentFailure.DOCUMENT_TOO_COMPLEX
                "same_language" -> DocumentFailure.SAME_LANGUAGE
                "unsupported_language" -> if (role == "source") DocumentFailure.UNSUPPORTED_SOURCE_LANGUAGE else DocumentFailure.UNSUPPORTED_TARGET
                "unsupported_language_pair", "auto_detect_unavailable", "source_language_undetected" -> DocumentFailure.UNSUPPORTED_SOURCE_LANGUAGE
                else -> DocumentFailure.TRANSLATION_FAILED
            }
            502 -> DocumentFailure.TRANSLATION_FAILED
            503 -> DocumentFailure.BACKEND_UNAVAILABLE
            504 -> DocumentFailure.TIMEOUT
            in 500..599 -> DocumentFailure.BACKEND_UNAVAILABLE
            else -> DocumentFailure.TRANSLATION_FAILED
        }
    }

    private companion object {
        // Comfortably above the backend's own default document size cap, with headroom for a slightly larger
        // regenerated file (a translation can run a little longer than its original).
        const val MAX_RESPONSE_BYTES = 25 * 1024 * 1024
        const val MAX_ERROR_BYTES = 8 * 1024
    }
}
