package com.alterlingua.app.keyboard.handwriting

import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handwriting recognition with Google ML Kit Digital Ink Recognition. Recognition runs on the phone; the strokes are not sent anywhere.
 * The language model (about 20 MB) is downloaded from Google the first time a language is used. This part is not open source
 * (see docs/privacy.md).
 */
class MlKitInkRecognizer : InkRecognizer {
    private var recognizer: DigitalInkRecognizer? = null

    /** Why the last preparation failed (the library's own message, never anything written); for diagnosis. */
    var lastError: String? = null
        private set

    private fun tagFor(languageCode: String): String? = when (languageCode) {
        "en" -> "en-US"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        "de" -> "de-DE"
        "it" -> "it-IT"
        "nl" -> "nl-NL"
        "zh" -> "zh-Hans-CN"
        "ja" -> "ja-JP"
        else -> null
    }

    override suspend fun prepare(languageCode: String, onDownloading: () -> Unit): Boolean = try {
        val tag = tagFor(languageCode) ?: return false
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) ?: return false
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        val manager = RemoteModelManager.getInstance()
        if (manager.isModelDownloaded(model).awaitOrNote() != true) {
            onDownloading()
            if (!manager.download(model, DownloadConditions.Builder().build()).succeeds()) return false
        }
        recognizer?.close()
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        true
    } catch (_: MlKitException) {
        false
    }

    /** True when the task finished without failing (a download's result is empty, so its value cannot be used to tell). */
    private suspend fun Task<*>.succeeds(): Boolean = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
        addOnFailureListener {
            lastError = "${it.javaClass.simpleName}: ${it.message}"
            if (continuation.isActive) continuation.resume(false)
        }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(false) }
    }

    private suspend fun <T> Task<T>.awaitOrNote(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener {
            lastError = "${it.javaClass.simpleName}: ${it.message}"
            if (continuation.isActive) continuation.resume(null)
        }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
    }

    override suspend fun recognize(strokes: List<InkStroke>): List<String> {
        val client = recognizer ?: return emptyList()
        val ink = Ink.builder().apply {
            strokes.forEach { stroke ->
                addStroke(
                    Ink.Stroke.builder().apply {
                        stroke.forEach { addPoint(Ink.Point.create(it.x, it.y, it.timeMillis)) }
                    }.build(),
                )
            }
        }.build()
        val result = client.recognize(ink).await() ?: return emptyList()
        return result.candidates.map { it.text }
    }

    override fun close() {
        recognizer?.close()
        recognizer = null
    }
}

/** Waits for a Google Play services task without blocking; null when it failed or was cancelled. */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
}
