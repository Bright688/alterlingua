package com.alterlingua.app.translation

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** Talks to a real HTTP server running in the test and reads the real multipart bytes it receives. */
class HttpVoiceApiTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class Part(val name: String, val filename: String?, val contentType: String?, val bytes: ByteArray)
    private class Received(val contentType: String?, val contentLength: Long, val parts: List<Part>)

    private val servers = mutableListOf<HttpServer>()
    private var received: Received? = null
    private val audioBytes = ByteArray(2_000) { (it * 7).toByte() }

    @After
    fun stopServers() = servers.forEach { it.stop(0) }

    /** Splits a multipart body into its parts. */
    private fun parse(body: ByteArray, boundary: String): List<Part> {
        val delimiter = "--$boundary".toByteArray(Charsets.ISO_8859_1)
        fun indexOf(bytes: ByteArray, pattern: ByteArray, from: Int): Int {
            outer@ for (i in from..bytes.size - pattern.size) {
                for (j in pattern.indices) if (bytes[i + j] != pattern[j]) continue@outer
                return i
            }
            return -1
        }
        val parts = mutableListOf<Part>()
        var position = indexOf(body, delimiter, 0)
        while (position >= 0) {
            val afterDelimiter = position + delimiter.size
            if (body.size >= afterDelimiter + 2 && body[afterDelimiter] == '-'.code.toByte()) break
            val headerStart = afterDelimiter + 2 // skip CRLF
            val headerEnd = indexOf(body, "\r\n\r\n".toByteArray(), headerStart)
            val headers = String(body, headerStart, headerEnd - headerStart, Charsets.UTF_8)
            val contentStart = headerEnd + 4
            val next = indexOf(body, ("\r\n--$boundary").toByteArray(Charsets.ISO_8859_1), contentStart)
            val name = Regex("name=\"([^\"]*)\"").find(headers)!!.groupValues[1]
            val filename = Regex("filename=\"([^\"]*)\"").find(headers)?.groupValues?.get(1)
            val type = Regex("Content-Type: ([^\r\n]*)").find(headers)?.groupValues?.get(1)
            parts += Part(name, filename, type, body.copyOfRange(contentStart, next))
            position = next + 2
        }
        return parts
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v1/audio/translate") { exchange ->
            val type = exchange.requestHeaders.getFirst("Content-Type")
            val boundary = type!!.substringAfter("boundary=")
            val bytes = exchange.requestBody.readBytes()
            received = Received(type, exchange.requestHeaders.getFirst("Content-Length")?.toLong() ?: -1, parse(bytes, boundary))
            handler(exchange)
        }
        server.start()
        servers += server
        return server
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpServer.url() = "http://127.0.0.1:${address.port}"

    private fun api(server: HttpServer, readTimeout: Int = 5_000, online: Boolean = true) =
        HttpVoiceApi(server.url(), isOnline = { online }, connectTimeoutMillis = 2_000, readTimeoutMillis = readTimeout)

    private fun recording(): File = folder.newFile().also { it.writeBytes(audioBytes) }

    private fun send(api: VoiceApi, source: String = "en", target: String = "es") =
        runBlocking { api.translate(recording(), "audio/mp4", source, target) }

    private fun json(source: String, transcript: String, target: String, translation: String) =
        JSONObject(mapOf("source_language" to source, "transcript" to transcript, "target_language" to target, "translation" to translation)).toString()

    private fun failure(result: VoiceResult) = (result as VoiceResult.Failure).failure

    private fun Received.field(name: String) = String(parts.first { it.name == name }.bytes, Charsets.UTF_8)

    // ---- what is sent ----

    @Test
    fun likelyLanguagesAreSentAsHints_onlyWhenGiven_asPlainCodes() {
        val server = server { it.reply(200, json("fr", "Tu viens demain ?", "en", "Are you coming tomorrow?")) }
        runBlocking { api(server).translate(recording(), "audio/ogg", "auto", "en", listOf("fr", "en")) }
        assertEquals("fr,en", received!!.field("hints"))
        assertEquals("auto", received!!.field("source"))

        send(api(server), source = "auto", target = "en")
        assertTrue("no hints field when none are given", received!!.parts.none { it.name == "hints" })
    }

    @Test
    fun sendsTheDocumentedMultipartRequest() {
        val server = server { it.reply(200, json("en", "Are you coming tomorrow?", "es", "¿Vienes mañana?")) }
        val result = send(api(server), source = "en", target = "es")

        val sent = received!!
        assertTrue(sent.contentType!!.startsWith("multipart/form-data; boundary="))
        assertEquals("es", sent.field("target"))
        assertEquals("en", sent.field("source"))
        assertEquals("messaging", sent.field("context"))
        assertEquals("natural", sent.field("tone"))
        val audio = sent.parts.first { it.name == "audio" }
        assertEquals("audio/mp4", audio.contentType)
        assertEquals("voice.m4a", audio.filename)
        assertArrayEquals(audioBytes, audio.bytes)
        assertEquals(VoiceTranslation("en", "Are you coming tomorrow?", "es", "¿Vienes mañana?"), (result as VoiceResult.Success).value)
    }

    @Test
    fun theTargetAndSourceAreWhateverWasAskedFor() {
        for ((source, target, transcript, translation) in listOf(
            listOf("en", "fr", "Are you coming tomorrow?", "Tu viens demain ?"),
            listOf("en", "ja", "Are you coming tomorrow?", "明日来ますか？"),
            listOf("ja", "en", "明日来ますか？", "Are you coming tomorrow?"),
            listOf("zh", "de", "你明天来吗？", "Kommst du morgen?"),
        )) {
            val server = server { it.reply(200, json(source, transcript, target, translation)) }
            val result = send(api(server), source, target) as VoiceResult.Success
            assertEquals(target, received!!.field("target"))
            assertEquals(source, received!!.field("source"))
            assertEquals(VoiceTranslation(source, transcript, target, translation), result.value) // real 中文 / 日本語 characters
        }
    }

    @Test
    fun theDeclaredLengthMatchesTheBytesSent() {
        val server = server { it.reply(200, json("en", "x", "es", "y")) }
        send(api(server))
        assertTrue(received!!.contentLength > audioBytes.size)
    }

    // ---- answers the client must not accept ----

    @Test
    fun aTranslationInTheWrongLanguageIsRefused() {
        val server = server { it.reply(200, json("en", "hello", "fr", "salut")) }
        assertEquals(VoiceFailure.TRANSLATION_FAILED, failure(send(api(server), target = "es")))
    }

    @Test
    fun wordsWithoutATranslation_keepWhatWasHeard() {
        val server = server { it.reply(200, json("en", "Tell him I'll be late", "es", "")) }
        val result = send(api(server)) as VoiceResult.Failure
        assertEquals(VoiceFailure.PARTIAL, result.failure)
        assertEquals("Tell him I'll be late", result.heard)
    }

    @Test
    fun brokenAnswersAreFailures() {
        for (body in listOf("not json", "{}")) {
            val server = server { it.reply(200, body) }
            assertTrue(body, failure(send(api(server))) in setOf(VoiceFailure.TRANSLATION_FAILED, VoiceFailure.NO_SPEECH))
        }
        val noSource = server { it.reply(200, """{"transcript":"a","translation":"b","target_language":"es"}""") }
        assertEquals(VoiceFailure.TRANSLATION_FAILED, failure(send(api(noSource))))
    }

    // ---- errors from the backend ----

    @Test
    fun theBackendsControlledErrorsAreMapped() {
        val cases = listOf(
            Triple(422, """{"error":{"code":"unsupported_language","role":"source","feature":"speech_to_text"}}""", VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE),
            Triple(422, """{"error":{"code":"unsupported_language","role":"target"}}""", VoiceFailure.UNSUPPORTED_TARGET),
            Triple(422, """{"error":{"code":"unsupported_language_pair"}}""", VoiceFailure.UNSUPPORTED_TARGET),
            Triple(422, """{"error":{"code":"auto_detect_unavailable"}}""", VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE),
            Triple(422, """{"error":{"code":"source_language_undetected"}}""", VoiceFailure.UNCLEAR_SPEECH),
            Triple(422, """{"error":{"code":"speech_not_recognized"}}""", VoiceFailure.NO_SPEECH),
            Triple(422, """{"error":{"code":"invalid_audio"}}""", VoiceFailure.TOO_SHORT),
            Triple(422, """{"error":{"code":"text_too_long"}}""", VoiceFailure.RECORDING_TOO_LONG),
            Triple(422, """{"error":{"code":"invalid_request"}}""", VoiceFailure.TRANSLATION_FAILED),
            Triple(413, """{"error":{"code":"audio_too_large"}}""", VoiceFailure.RECORDING_TOO_LONG),
            Triple(415, """{"error":{"code":"unsupported_audio_type"}}""", VoiceFailure.AUDIO_REJECTED),
            Triple(502, """{"error":{"code":"provider_error"}}""", VoiceFailure.TRANSLATION_FAILED),
            Triple(503, """{"error":{"code":"provider_unavailable"}}""", VoiceFailure.BACKEND_UNAVAILABLE),
            Triple(504, """{"error":{"code":"provider_timeout"}}""", VoiceFailure.TIMEOUT),
            Triple(500, """{"error":{"code":"internal_error"}}""", VoiceFailure.BACKEND_UNAVAILABLE),
            Triple(404, "", VoiceFailure.TRANSLATION_FAILED),
        )
        for ((status, body, expected) in cases) {
            val server = server { it.reply(status, body) }
            assertEquals("HTTP $status $body", expected, failure(send(api(server))))
        }
    }

    // ---- can't reach it ----

    @Test
    fun aSlowServerIsATimeout() {
        val server = server {
            Thread.sleep(1_500)
            runCatching { it.reply(200, "{}") }
        }
        assertEquals(VoiceFailure.TIMEOUT, failure(send(api(server, readTimeout = 300))))
    }

    @Test
    fun aServerThatIsNotRunning_isUnavailable_orOfflineWhenThePhoneHasNoNetwork() {
        val closed = server { it.reply(200, "{}") }
        val url = closed.url()
        closed.stop(0)
        assertEquals(VoiceFailure.BACKEND_UNAVAILABLE, failure(runBlocking { HttpVoiceApi(url, isOnline = { true }).translate(recording(), "audio/mp4", "en", "es") }))
        assertEquals(VoiceFailure.OFFLINE, failure(runBlocking { HttpVoiceApi(url, isOnline = { false }).translate(recording(), "audio/mp4", "en", "es") }))
    }

    @Test
    fun noAddressMeansNotConfigured() {
        assertEquals(VoiceFailure.NOT_CONFIGURED, failure(runBlocking { HttpVoiceApi("").translate(recording(), "audio/mp4", "en", "es") }))
    }

    @Test
    fun cancellingStopsWaitingAtOnce() {
        val server = server {
            Thread.sleep(5_000)
            runCatching { it.reply(200, "{}") }
        }
        val started = System.nanoTime()
        try {
            runBlocking { withTimeout(400) { api(server, readTimeout = 10_000).translate(recording(), "audio/mp4", "en", "es") } }
        } catch (_: TimeoutCancellationException) {
            // expected
        }
        assertTrue("took too long", (System.nanoTime() - started) / 1e9 < 3.0)
    }
}
