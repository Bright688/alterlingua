package com.alterlingua.app.translation

import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Calls the backend's `POST /v1/audio/translate` with a multipart upload.
 *
 * PRIVACY: the recording, transcript and translation are only ever in memory or in the caller's temporary file here.
 * Nothing is logged, and the server's error details are reduced to a [VoiceFailure]. Cancelling the calling coroutine
 * closes the connection.
 */
class HttpVoiceApi(
    baseUrl: String,
    private val isOnline: () -> Boolean = { true },
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 60_000,
    /** The server's secret API token (sent as a bearer token); empty means none (a local development server). */
    private val apiToken: String = "",
) : VoiceApi {

    private val root: String? = baseUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }
    private val endpoint: String? = root?.let { "$it/v1/audio/translate" }

    override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult =
        translate(audio, contentType, source, target, emptyList())

    override suspend fun translate(audio: File, contentType: String, source: String, target: String, hints: List<String>): VoiceResult {
        val url = endpoint ?: return VoiceResult.Failure(VoiceFailure.NOT_CONFIGURED)
        return post(url, audio, contentType, source, target, hints, { VoiceResult.Failure(it) }) { status, body -> interpret(status, body, target) }
    }

    private suspend fun <R> post(url: String, audio: File, contentType: String, source: String, target: String, hints: List<String>, fail: (VoiceFailure) -> R, interpret: (Int, String) -> R): R =
        suspendCancellableCoroutine { continuation ->
            val call = HttpCall()
            continuation.invokeOnCancellation { call.cancel() }
            thread(name = "alterlingua-voice", isDaemon = true) {
                val result = exchange(url, audio, contentType, source, target, hints, call, fail, interpret)
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun <R> exchange(url: String, audio: File, contentType: String, source: String, target: String, hints: List<String>, call: HttpCall, fail: (VoiceFailure) -> R, interpret: (Int, String) -> R): R {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return fail(VoiceFailure.NOT_CONFIGURED)
        } catch (_: ClassCastException) {
            return fail(VoiceFailure.NOT_CONFIGURED)
        }
        if (!call.attach(connection)) return fail(VoiceFailure.TRANSLATION_FAILED)
        return try {
            val boundary = "alterlingua-" + UUID.randomUUID().toString().replace("-", "")
            val head = buildString {
                val fields = mutableListOf("target" to target, "source" to source, "context" to "messaging", "tone" to "natural")
                // Language codes only (never any content): the languages the speaker is likely to use, for a second try.
                if (hints.isNotEmpty()) fields += "hints" to hints.joinToString(",")
                for ((name, value) in fields) {
                    append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                append("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"voice.m4a\"\r\nContent-Type: $contentType\r\n\r\n")
            }.toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")
            if (apiToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${apiToken.trim()}")
            connection.setFixedLengthStreamingMode(head.size.toLong() + audio.length() + tail.size)
            connection.outputStream.use { out ->
                out.write(head)
                audio.inputStream().use { it.copyTo(out) }
                out.write(tail)
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val answer = stream?.use { readLimited(it) }.orEmpty()
            interpret(status, answer)
        } catch (_: SocketTimeoutException) {
            fail(VoiceFailure.TIMEOUT)
        } catch (_: IOException) {
            fail(if (isOnline()) VoiceFailure.BACKEND_UNAVAILABLE else VoiceFailure.OFFLINE)
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

    private fun interpret(status: Int, body: String, target: String): VoiceResult {
        if (status in 200..299) return parseSuccess(body, target)
        return VoiceResult.Failure(failureFor(status, body))
    }

    private fun failureFor(status: Int, body: String): VoiceFailure {
        val error = try {
            JSONObject(body).optJSONObject("error")
        } catch (_: JSONException) {
            null
        }
        val code = error?.optString("code")
        val role = error?.optString("role")
        return when (status) {
            413 -> VoiceFailure.RECORDING_TOO_LONG
            415 -> VoiceFailure.AUDIO_REJECTED
            422 -> when (code) {
                "unsupported_language" -> when {
                    role == "source" -> VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE
                    error?.optString("feature") == "text_to_speech" -> VoiceFailure.NO_VOICE_FOR_TARGET
                    else -> VoiceFailure.UNSUPPORTED_TARGET
                }
                "auto_detect_unavailable" -> VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE
                "unsupported_language_pair" -> VoiceFailure.UNSUPPORTED_TARGET
                "source_language_undetected" -> VoiceFailure.UNCLEAR_SPEECH
                "speech_not_recognized" -> VoiceFailure.NO_SPEECH
                "invalid_audio" -> VoiceFailure.TOO_SHORT
                "text_too_long" -> VoiceFailure.RECORDING_TOO_LONG
                else -> VoiceFailure.TRANSLATION_FAILED
            }
            502 -> VoiceFailure.TRANSLATION_FAILED
            503 -> VoiceFailure.BACKEND_UNAVAILABLE
            504 -> VoiceFailure.TIMEOUT
            in 500..599 -> VoiceFailure.BACKEND_UNAVAILABLE
            else -> VoiceFailure.TRANSLATION_FAILED
        }
    }

    private fun parseSuccess(body: String, target: String): VoiceResult {
        val failed = VoiceResult.Failure(VoiceFailure.TRANSLATION_FAILED)
        return try {
            val json = JSONObject(body)
            val transcript = json.optString("transcript", "")
            val translation = json.optString("translation", "")
            val source = json.optString("source_language", "").lowercase(Locale.ROOT)
            val answeredTarget = json.optString("target_language", "").lowercase(Locale.ROOT)
            when {
                transcript.isBlank() -> VoiceResult.Failure(VoiceFailure.NO_SPEECH)
                // Never accept a translation that is not in the language the user chose.
                answeredTarget != target.lowercase(Locale.ROOT) || source.isBlank() -> failed
                // Words were understood but no translation came back: keep what was heard.
                translation.isBlank() -> VoiceResult.Failure(VoiceFailure.PARTIAL, heard = transcript)
                else -> VoiceResult.Success(VoiceTranslation(source, transcript, answeredTarget, translation))
            }
        } catch (_: JSONException) {
            failed
        }
    }

    private companion object {
        // Room for a base64-encoded spoken clip as well as the text.
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }
}
