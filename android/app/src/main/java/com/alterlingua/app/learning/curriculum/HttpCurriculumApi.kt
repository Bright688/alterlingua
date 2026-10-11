package com.alterlingua.app.learning.curriculum

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
 * Calls the AlterLingua backend's `GET /v1/curriculum/{language}`. Uses Android's built-in HTTP client, like
 * [com.alterlingua.app.translation.HttpTranslationApi], so no extra library is needed. Cancelling the calling
 * coroutine closes the connection.
 */
class HttpCurriculumApi(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000,
    /** The server's secret API token (sent as a bearer token); empty means none (a local development server). */
    private val apiToken: String = "",
) : CurriculumApi {

    private val base: String? = baseUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): CurriculumPageResult {
        val root = base ?: return CurriculumPageResult.Unavailable
        val url = "$root/v1/curriculum/$languageCode?offset=$offset&limit=$limit"
        return suspendCancellableCoroutine { continuation ->
            val call = HttpCall()
            continuation.invokeOnCancellation { call.cancel() }
            thread(name = "alterlingua-curriculum", isDaemon = true) {
                val result = exchange(url, call)
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun exchange(url: String, call: HttpCall): CurriculumPageResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return CurriculumPageResult.Unavailable
        } catch (_: ClassCastException) {
            return CurriculumPageResult.Unavailable
        }
        if (!call.attach(connection)) return CurriculumPageResult.Unavailable
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
            if (status in 200..299) parseSuccess(body) else CurriculumPageResult.Unavailable
        } catch (_: SocketTimeoutException) {
            CurriculumPageResult.Unavailable
        } catch (_: IOException) {
            CurriculumPageResult.Unavailable
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

    private fun parseSuccess(body: String): CurriculumPageResult = try {
        val json = JSONObject(body)
        val words = json.getJSONArray("words").let { array -> (0 until array.length()).map { array.getString(it) } }
        CurriculumPageResult.Success(words, json.getInt("total"))
    } catch (_: JSONException) {
        CurriculumPageResult.Unavailable
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
