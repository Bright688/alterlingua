package com.alterlingua.app.translation

import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional: runs the real client against the real backend. Skipped unless ALTERLINGUA_LIVE_BACKEND is set, for example
 *   ALTERLINGUA_LIVE_BACKEND=http://127.0.0.1:8000 ./gradlew testDebugUnitTest --tests '*LiveBackendTest'
 * with the backend running (see backend/README.md). The backend's development provider only knows a few phrases.
 */
class LiveBackendTest {

    private val baseUrl: String? = System.getenv("ALTERLINGUA_LIVE_BACKEND")?.takeIf { it.isNotBlank() }

    private fun translate(target: String, text: String = "Are you coming tomorrow?") = runBlocking {
        HttpTranslationApi(baseUrl!!).translate(TranslationRequest(text = text, target = target))
    }

    @Test
    fun theRealBackendAnswersForSeveralTargets() {
        assumeTrue("ALTERLINGUA_LIVE_BACKEND is not set", baseUrl != null)
        val expected = mapOf(
            "es" to "¿Vienes mañana?",
            "fr" to "Tu viens demain ?",
            "ja" to "明日来ますか？",
            "de" to "Kommst du morgen?",
            "it" to "Vieni domani?",
            "nl" to "Kom je morgen?",
            "zh" to "你明天来吗？",
        )
        for ((target, answer) in expected) {
            val result = translate(target)
            assertEquals(target, TranslationResult.Success(Translation(answer, "en", target)), result)
        }
    }

    /** A tiny file that looks like an MP4/M4A recording to the backend (the development recogniser ignores the audio). */
    private fun fakeRecording(): File = File.createTempFile("live-voice", ".m4a").also {
        it.writeBytes(byteArrayOf(0, 0, 0, 0x18) + "ftypM4A ".toByteArray() + ByteArray(80))
        it.deleteOnExit()
    }

    @Test
    fun theRealBackendTranslatesARecordingForSeveralTargets() {
        assumeTrue("ALTERLINGUA_LIVE_BACKEND is not set", baseUrl != null)
        val expected = mapOf("es" to "¿Vienes mañana?", "fr" to "Tu viens demain ?", "ja" to "明日来ますか？", "de" to "Kommst du morgen?", "zh" to "你明天来吗？")
        for ((target, answer) in expected) {
            val result = runBlocking { HttpVoiceApi(baseUrl!!).translate(fakeRecording(), "audio/mp4", "en", target) }
            assertEquals(target, VoiceResult.Success(VoiceTranslation("en", "Are you coming tomorrow?", target, answer)), result)
        }
        // speech in another language, translated to English
        val japanese = runBlocking { HttpVoiceApi(baseUrl!!).translate(fakeRecording(), "audio/mp4", "ja", "en") }
        assertEquals(VoiceResult.Success(VoiceTranslation("ja", "明日来ますか？", "en", "Are you coming tomorrow?")), japanese)
    }

    @Test
    fun theRealBackendRefusesBadRecordingsAndUnsupportedTargets() {
        assumeTrue("ALTERLINGUA_LIVE_BACKEND is not set", baseUrl != null)
        val notAudio = File.createTempFile("live-voice", ".m4a").also { it.writeText("this is not audio at all"); it.deleteOnExit() }
        assertEquals(VoiceResult.Failure(VoiceFailure.AUDIO_REJECTED), runBlocking { HttpVoiceApi(baseUrl!!).translate(notAudio, "audio/mp4", "en", "es") })
        assertEquals(VoiceResult.Failure(VoiceFailure.UNSUPPORTED_TARGET), runBlocking { HttpVoiceApi(baseUrl!!).translate(fakeRecording(), "audio/mp4", "en", "pt") })
    }

    @Test
    fun theRealBackendTranslatesAnIncomingMessageIntoEachUsersOwnLanguage() {
        assumeTrue("ALTERLINGUA_LIVE_BACKEND is not set", baseUrl != null)
        // A French WhatsApp message, for users whose own language differs: source is detected, target is theirs.
        val expected = mapOf("en" to "Are you coming tomorrow?", "es" to "¿Vienes mañana?", "de" to "Kommst du morgen?", "zh" to "你明天来吗？", "ja" to "明日来ますか？")
        for ((native, answer) in expected) {
            val result = runBlocking { HttpTranslationApi(baseUrl!!).translate(TranslationRequest(text = "Tu viens demain ?", target = native)) }
            assertEquals(native, TranslationResult.Success(Translation(answer, "fr", native)), result)
        }
        // A French speaker receiving French: the backend reports the source equals the target, so nothing is shown.
        val same = runBlocking { HttpTranslationApi(baseUrl!!).translate(TranslationRequest(text = "Tu viens demain ?", target = "fr")) }
        assertEquals(TranslationResult.Success(Translation("Tu viens demain ?", "fr", "fr")), same)
    }

    @Test
    fun theRealBackendAnswersUnsupportedLanguagesAndUnknownSources() {
        assumeTrue("ALTERLINGUA_LIVE_BACKEND is not set", baseUrl != null)
        assertEquals(TranslationResult.Failure(TranslationFailure.UNSUPPORTED_LANGUAGE), translate("pt"))
        assertEquals(TranslationResult.Failure(TranslationFailure.SOURCE_UNDETECTED), translate("fr", "Some unknown latin text"))
        assertTrue(translate("en", "明日来ますか？") is TranslationResult.Success)
    }
}
