package com.alterlingua.app.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Calls the AlterLingua backend's `POST /v1/translate`.
 *
 * PRIVACY: the text and the translation are only ever in memory here. They are never logged, and error details from
 * the server are reduced to a [TranslationFailure]. Uses Android's built-in HTTP client, so no extra library is needed.
 * Cancelling the calling coroutine closes the connection.
 */
class HttpTranslationApi(
    baseUrl: String,
    private val isOnline: () -> Boolean = { true },
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 20_000,
    /** The server's secret API token (sent as a bearer token); empty means none (a local development server). */
    private val apiToken: String = "",
) : TranslationApi {

    private val endpoint: String? = baseUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/v1/translate" }

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        val url = endpoint ?: return TranslationResult.Failure(TranslationFailure.NOT_CONFIGURED)
        return suspendCancellableCoroutine { continuation ->
            val call = HttpCall()
            // If the caller gives up (timeout, user left the field), close the connection so we stop waiting.
            continuation.invokeOnCancellation { call.cancel() }
            thread(name = "alterlingua-translate", isDaemon = true) {
                val result = exchange(url, request, call)
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun exchange(url: String, request: TranslationRequest, call: HttpCall): TranslationResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return TranslationResult.Failure(TranslationFailure.NOT_CONFIGURED)
        } catch (_: ClassCastException) {
            return TranslationResult.Failure(TranslationFailure.NOT_CONFIGURED)
        }
        if (!call.attach(connection)) return TranslationResult.Failure(TranslationFailure.TRANSLATION_FAILED)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (apiToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${apiToken.trim()}")

            val body = JSONObject()
                .put("text", request.text)
                .put("source", request.source)
                .put("target", request.target)
                .put("context", request.context)
                .put("tone", request.tone)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val answer = stream?.use { readLimited(it) }.orEmpty()
            interpret(status, answer, request)
        } catch (_: SocketTimeoutException) {
            TranslationResult.Failure(TranslationFailure.TIMEOUT)
        } catch (_: IOException) {
            // Could not connect or the connection dropped: offline if the phone has no network, else the server is down.
            TranslationResult.Failure(if (isOnline()) TranslationFailure.BACKEND_UNAVAILABLE else TranslationFailure.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= MAX_RESPONSE_BYTES) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun interpret(status: Int, body: String, request: TranslationRequest): TranslationResult {
        if (status in 200..299) return parseSuccess(body, request)
        val code = errorCode(body)
        val failure = when (status) {
            422 -> when (code) {
                "unsupported_language" -> TranslationFailure.UNSUPPORTED_LANGUAGE
                "unsupported_language_pair", "auto_detect_unavailable" -> TranslationFailure.UNSUPPORTED_PAIR
                "source_language_undetected" -> TranslationFailure.SOURCE_UNDETECTED
                "text_too_long" -> TranslationFailure.TEXT_TOO_LONG
                else -> TranslationFailure.TRANSLATION_FAILED
            }
            502 -> TranslationFailure.TRANSLATION_FAILED
            503 -> TranslationFailure.BACKEND_UNAVAILABLE
            504 -> TranslationFailure.TIMEOUT
            in 500..599 -> TranslationFailure.BACKEND_UNAVAILABLE
            else -> TranslationFailure.TRANSLATION_FAILED
        }
        return TranslationResult.Failure(failure)
    }

    private fun parseSuccess(body: String, request: TranslationRequest): TranslationResult {
        val failed = TranslationResult.Failure(TranslationFailure.TRANSLATION_FAILED)
        return try {
            val json = JSONObject(body)
            val translation = json.optString("translation", "")
            val source = json.optString("source_language", "").lowercase(Locale.ROOT)
            val target = json.optString("target_language", "").lowercase(Locale.ROOT)
            when {
                translation.isBlank() || source.isBlank() -> failed
                // Never insert a translation that is not in the language the user chose.
                target != request.target.lowercase(Locale.ROOT) -> failed
                else -> TranslationResult.Success(Translation(translation, source, target))
            }
        } catch (_: JSONException) {
            failed
        }
    }

    private fun errorCode(body: String): String? = try {
        JSONObject(body).optJSONObject("error")?.optString("code")
    } catch (_: JSONException) {
        null
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}

/** Whether the phone has a network. Only used to word an error, never to block a request. */
class AndroidConnectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean = try {
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: SecurityException) {
        true // cannot tell, so do not claim the phone is offline
    }
}
