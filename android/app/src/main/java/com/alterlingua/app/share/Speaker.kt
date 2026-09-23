package com.alterlingua.app.share

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.alterlingua.app.learning.Language
import java.io.File
import java.util.Locale

/**
 * Reads text aloud, and can write it to a playable file instead. Not every language has a voice on every phone
 * (CLAUDE.md 18), so [canSpeak] is asked before offering Listen or generating a file, and both [speak] and
 * [synthesizeToFile] can still say no.
 */
interface Speaker {
    /** True when the phone has a voice for [language] right now. */
    suspend fun canSpeak(language: Language): Boolean

    /** Starts reading [text]; [onDone] runs when it finishes or stops. False if it could not start. */
    fun speak(text: String, language: Language, onDone: () -> Unit): Boolean

    /** Writes [text] spoken in [language] into [file] as a playable, shareable audio file. False if it could not. */
    suspend fun synthesizeToFile(text: String, language: Language, file: File): Boolean

    fun stop()

    fun shutdown()
}

/** Android's own text-to-speech engine, on the device: the text is not sent anywhere. */
class AndroidSpeaker(private val context: Context) : Speaker {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: ArrayList<() -> Unit> = ArrayList()
    private var starting = false

    private fun withEngine(action: (TextToSpeech?) -> Unit) {
        val existing = engine
        if (existing != null && ready) return action(existing)
        pending += { action(engine?.takeIf { ready }) }
        if (!starting) {
            starting = true
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                val queued = pending
                pending = ArrayList()
                queued.forEach { it() }
            }
        }
    }

    override suspend fun canSpeak(language: Language): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        withEngine { tts ->
            val available = tts != null && tts.isLanguageAvailable(Locale.forLanguageTag(language.code)) >= TextToSpeech.LANG_AVAILABLE
            if (continuation.isActive) continuation.resumeWith(Result.success(available))
        }
    }

    override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean {
        val tts = engine?.takeIf { ready } ?: return false
        if (tts.setLanguage(Locale.forLanguageTag(language.code)) < TextToSpeech.LANG_AVAILABLE) return false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onDone()

            @Deprecated("Required by the abstract class")
            override fun onError(utteranceId: String?) = onDone()

            override fun onStop(utteranceId: String?, interrupted: Boolean) = onDone()
        })
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alterlingua-listen") == TextToSpeech.SUCCESS
    }

    override suspend fun synthesizeToFile(text: String, language: Language, file: File): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            withEngine { tts ->
                if (tts == null || tts.setLanguage(Locale.forLanguageTag(language.code)) < TextToSpeech.LANG_AVAILABLE) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(false))
                    return@withEngine
                }
                val utteranceId = "alterlingua-synthesize-${file.name}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(true))
                    }

                    @Deprecated("Required by the abstract class")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(false))
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(false))
                    }
                })
                val started = tts.synthesizeToFile(text, null, file, utteranceId)
                if (started != TextToSpeech.SUCCESS && continuation.isActive) continuation.resumeWith(Result.success(false))
            }
        }

    override fun stop() {
        engine?.takeIf { ready }?.stop()
    }

    override fun shutdown() {
        engine?.shutdown()
        engine = null
        ready = false
        starting = false
        pending = ArrayList()
    }
}
