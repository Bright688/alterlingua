package com.alterlingua.app.keyboard.handwriting

/** One point of a pen stroke: where the finger was, and when. */
data class InkPoint(val x: Float, val y: Float, val timeMillis: Long)

/** One stroke: the points from putting the finger down to lifting it. */
typealias InkStroke = List<InkPoint>

/**
 * Turns pen strokes into written text. Kept behind an interface so the keyboard logic and its tests do not depend on the
 * recognition library. Implementations must not log or send the strokes anywhere except to the on-device recogniser.
 */
interface InkRecognizer {
    /** Makes the recogniser ready for [languageCode] (a language code such as "fr"), downloading its model the first time. Returns false if it could not. */
    suspend fun prepare(languageCode: String, onDownloading: () -> Unit): Boolean

    /** The most likely readings of [strokes], best first; empty if nothing was recognised. */
    suspend fun recognize(strokes: List<InkStroke>): List<String>

    fun close()
}
