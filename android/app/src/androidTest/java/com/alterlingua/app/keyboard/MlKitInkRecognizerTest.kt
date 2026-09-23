package com.alterlingua.app.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alterlingua.app.keyboard.handwriting.InkPoint
import com.alterlingua.app.keyboard.handwriting.InkStroke
import com.alterlingua.app.keyboard.handwriting.MlKitInkRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real ML Kit handwriting on a device or emulator. Needs the network the first time (the language model is downloaded). */
@RunWith(AndroidJUnit4::class)
class MlKitInkRecognizerTest {

    /** A straight line drawn over [steps] points. */
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, start: Long): InkStroke =
        (0..20).map { InkPoint(x0 + (x1 - x0) * it / 20f, y0 + (y1 - y0) * it / 20f, start + it * 12L) }

    @Test
    fun aDrawnCapitalT_isRecognisedAsText() = runBlocking {
        val recognizer = MlKitInkRecognizer()
        var downloading = false
        val ready = recognizer.prepare("en") { downloading = true }
        assertTrue("the English model should be available (download started: $downloading, error: ${recognizer.lastError})", ready)
        // The two strokes of a T: the bar, then the stem.
        val candidates = recognizer.recognize(listOf(line(30f, 30f, 130f, 30f, 0), line(80f, 30f, 80f, 140f, 400)))
        assertTrue("expected a T among $candidates", candidates.any { it.equals("T", ignoreCase = true) })
        recognizer.close()
    }
}
