package com.alterlingua.app.translation

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** Talks to a real HTTP server running inside the test, so the actual request and response bytes are checked. */
class HttpTranslationApiTest {

    private class Received(val contentType: String?, val accept: String?, val body: JSONObject, val authorization: String? = null)

    private val servers = mutableListOf<HttpServer>()
    private var received: Received? = null

    @After
    fun stopServers() = servers.forEach { it.stop(0) }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v1/translate") { exchange ->
            received = Received(
                exchange.requestHeaders.getFirst("Content-Type"),
                exchange.requestHeaders.getFirst("Accept"),
                JSONObject(String(exchange.requestBody.readBytes(), Charsets.UTF_8)),
                exchange.requestHeaders.getFirst("Authorization"),
            )
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
        HttpTranslationApi(server.url(), isOnline = { online }, connectTimeoutMillis = 2_000, readTimeoutMillis = readTimeout)

    private fun request(target: String, text: String = "Are you coming tomorrow?") = TranslationRequest(text = text, target = target)

    private fun translate(api: TranslationApi, request: TranslationRequest) = runBlocking { api.translate(request) }

    private fun failure(result: TranslationResult) = (result as TranslationResult.Failure).failure

    // ---- what is sent ----

    @Test
    fun sendsTheDocumentedRequest_withTheSelectedTarget() {
        val server = server { it.reply(200, """{"translation":"¿Vienes mañana?","source_language":"en","target_language":"es"}""") }
        val result = translate(api(server), request("es"))

        val sent = received!!
        assertEquals("application/json; charset=utf-8", sent.contentType)
        assertEquals("Are you coming tomorrow?", sent.body.getString("text"))
        assertEquals("auto", sent.body.getString("source"))
        assertEquals("es", sent.body.getString("target"))
        assertEquals("messaging", sent.body.getString("context"))
        assertEquals("natural", sent.body.getString("tone"))
        assertEquals(Translation("¿Vienes mañana?", "en", "es"), (result as TranslationResult.Success).translation)
    }

    @Test
    fun theTargetIsWhateverWasAskedFor_neverAFixedLanguage() {
        for ((target, answer) in mapOf("fr" to "Tu viens demain ?", "es" to "¿Vienes mañana?", "ja" to "明日来ますか？", "de" to "Kommst du morgen?", "zh" to "你明天来吗？")) {
            val server = server { it.reply(200, JSONObject(mapOf("translation" to answer, "source_language" to "en", "target_language" to target)).toString()) }
            val result = translate(api(server), request(target))
            assertEquals(target, received!!.body.getString("target"))
            assertEquals(answer, (result as TranslationResult.Success).translation.text)
        }
    }

    @Test
    fun unicodeSurvivesBothWays() {
        val text = "日本語のテキスト 🙂 café\nsecond line"
        val server = server { it.reply(200, JSONObject(mapOf("translation" to text, "source_language" to "ja", "target_language" to "zh")).toString()) }
        val result = translate(api(server), request("zh", text))
        assertEquals(text, received!!.body.getString("text"))
        assertEquals(text, (result as TranslationResult.Success).translation.text)
    }

    // ---- answers the client must not accept ----

    @Test
    fun aTranslationInTheWrongLanguageIsRefused() {
        val server = server { it.reply(200, """{"translation":"Tu viens demain ?","source_language":"en","target_language":"fr"}""") }
        assertEquals(TranslationFailure.TRANSLATION_FAILED, failure(translate(api(server), request("es"))))
    }

    @Test
    fun brokenOrIncompleteAnswersAreFailures() {
        for (body in listOf("not json", "{}", """{"translation":"","source_language":"en","target_language":"es"}""", """{"translation":"x","target_language":"es"}""")) {
            val server = server { it.reply(200, body) }
            assertEquals(body, TranslationFailure.TRANSLATION_FAILED, failure(translate(api(server), request("es"))))
        }
    }

    // ---- errors from the backend ----

    @Test
    fun theBackendsControlledErrorsAreMapped() {
        val cases = mapOf(
            "unsupported_language" to TranslationFailure.UNSUPPORTED_LANGUAGE,
            "unsupported_language_pair" to TranslationFailure.UNSUPPORTED_PAIR,
            "auto_detect_unavailable" to TranslationFailure.UNSUPPORTED_PAIR,
            "source_language_undetected" to TranslationFailure.SOURCE_UNDETECTED,
            "text_too_long" to TranslationFailure.TEXT_TOO_LONG,
            "invalid_request" to TranslationFailure.TRANSLATION_FAILED,
            "same_language" to TranslationFailure.TRANSLATION_FAILED,
        )
        for ((code, expected) in cases) {
            val server = server { it.reply(422, """{"error":{"code":"$code","message":"m"}}""") }
            assertEquals(code, expected, failure(translate(api(server), request("es"))))
        }
    }

    @Test
    fun serverErrorsAreMapped() {
        val cases = mapOf(
            502 to TranslationFailure.TRANSLATION_FAILED,
            503 to TranslationFailure.BACKEND_UNAVAILABLE,
            504 to TranslationFailure.TIMEOUT,
            500 to TranslationFailure.BACKEND_UNAVAILABLE,
            404 to TranslationFailure.TRANSLATION_FAILED,
        )
        for ((status, expected) in cases) {
            val server = server { it.reply(status, """{"error":{"code":"x","message":"m"}}""") }
            assertEquals("HTTP $status", expected, failure(translate(api(server), request("es"))))
        }
    }

    // ---- can't reach it ----

    @Test
    fun aSlowServerIsATimeout() {
        val server = server {
            Thread.sleep(1_500)
            runCatching { it.reply(200, "{}") }
        }
        assertEquals(TranslationFailure.TIMEOUT, failure(translate(api(server, readTimeout = 300), request("es"))))
    }

    @Test
    fun aServerThatIsNotRunning_isUnavailable_orOfflineWhenThePhoneHasNoNetwork() {
        val closed = server { it.reply(200, "{}") }
        val url = closed.url()
        closed.stop(0)
        assertEquals(TranslationFailure.BACKEND_UNAVAILABLE, failure(translate(HttpTranslationApi(url, isOnline = { true }), request("es"))))
        assertEquals(TranslationFailure.OFFLINE, failure(translate(HttpTranslationApi(url, isOnline = { false }), request("es"))))
    }

    @Test
    fun noAddressMeansNotConfigured_andNothingIsSent() {
        assertEquals(TranslationFailure.NOT_CONFIGURED, failure(translate(HttpTranslationApi(""), request("es"))))
        assertEquals(TranslationFailure.NOT_CONFIGURED, failure(translate(HttpTranslationApi("   "), request("es"))))
    }

    @Test
    fun cancellingStopsWaitingAtOnce() {
        val server = server {
            Thread.sleep(5_000)
            runCatching { it.reply(200, "{}") }
        }
        val started = System.nanoTime()
        try {
            runBlocking { withTimeout(400) { api(server, readTimeout = 10_000).translate(request("es")) } }
        } catch (_: TimeoutCancellationException) {
            // expected
        }
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("took $seconds s", seconds < 3.0)
    }

    @Test
    fun aTrailingSlashOnTheAddressIsFine() {
        val server = server { it.reply(200, """{"translation":"x","source_language":"en","target_language":"es"}""") }
        val result = translate(HttpTranslationApi(server.url() + "/"), request("es"))
        assertTrue(result is TranslationResult.Success)
    }

    @Test
    fun theApiTokenIsSentAsABearerHeader_onlyWhenOneIsConfigured() = runBlocking {
        val server = server { it.reply(200, """{"translation":"Hola","source_language":"en","target_language":"es"}""") }
        val request = TranslationRequest("Hello", "auto", "es", "messaging", "natural")
        HttpTranslationApi(server.url(), apiToken = " secret-token ").translate(request)
        assertEquals("Bearer secret-token", received?.authorization)
        received = null
        HttpTranslationApi(server.url()).translate(request)
        assertEquals(null, received?.authorization)
    }
}
