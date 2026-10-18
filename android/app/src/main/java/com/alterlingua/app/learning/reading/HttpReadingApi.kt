package com.alterlingua.app.learning.reading

import com.alterlingua.app.translation.HttpCall
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject

/**
 * Calls the AlterLingua backend's `GET /v1/reading/{language}`. Uses Android's built-in HTTP client, like
 * [com.alterlingua.app.learning.curriculum.HttpCurriculumApi], so no extra library is needed. Cancelling the
 * calling coroutine closes the connection.
 */
class HttpReadingApi(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000,
    /** The server's secret API token (sent as a bearer token); empty means none (a local development server). */
    private val apiToken: String = "",
) : ReadingApi {

    private val base: String? = baseUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): ReadingPageResult {
        val root = base ?: return ReadingPageResult.Unavailable
        val url = "$root/v1/reading/$languageCode?offset=$offset&limit=$limit"
        return suspendCancellableCoroutine { continuation ->
            val call = HttpCall()
            continuation.invokeOnCancellation { call.cancel() }
            thread(name = "alterlingua-reading", isDaemon = true) {
                val result = exchange(url, call)
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun exchange(url: String, call: HttpCall): ReadingPageResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return ReadingPageResult.Unavailable
        } catch (_: ClassCastException) {
            return ReadingPageResult.Unavailable
        }
        if (!call.attach(connection)) return ReadingPageResult.Unavailable
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (apiToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${apiToken.trim()}")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it) }.orEmpty()
            if (status in 200..299) parseSuccess(body) else ReadingPageResult.Unavailable
        } catch (_: SocketTimeoutException) {
            ReadingPageResult.Unavailable
        } catch (_: IOException) {
            ReadingPageResult.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= MAX_RESPONSE_BYTES) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun parseSuccess(body: String): ReadingPageResult = try {
        val json = JSONObject(body)
        val sentences = json.getJSONArray("sentences").let { array -> (0 until array.length()).map { array.getString(it) } }
        ReadingPageResult.Success(sentences, json.getInt("total"))
    } catch (_: JSONException) {
        ReadingPageResult.Unavailable
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
