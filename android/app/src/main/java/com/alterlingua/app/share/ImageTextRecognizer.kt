package com.alterlingua.app.share

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Finds the text in a shared image, if any. Never told what the image shows beyond that. */
fun interface ImageTextRecognizer {
    /** The text found, or null/blank if none was (or the image could not even be decoded). */
    suspend fun recognize(file: File): String?
}

/**
 * On-device OCR (CLAUDE.md 6.10: every writing system, not just Latin). The script is not asked for or guessed from
 * settings -- all three recognizers run on the same image and the longest non-blank result wins, which is simple,
 * correct regardless of which language the photographed text is actually in, and cheap: each recognizer is a small,
 * already-downloaded on-device model, not a network call.
 */
class MlKitImageTextRecognizer : ImageTextRecognizer {
    override suspend fun recognize(file: File): String? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val image = try {
            InputImage.fromBitmap(bitmap, 0)
        } finally {
            bitmap.recycle()
        }
        val recognizers = listOf(
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        )
        return try {
            recognizers.mapNotNull { runCatching { process(it, image) }.getOrNull()?.takeIf { text -> text.isNotBlank() } }
                .maxByOrNull { it.length }
        } finally {
            recognizers.forEach { it.close() }
        }
    }

    private suspend fun process(recognizer: TextRecognizer, image: InputImage): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume("") }
    }
}
