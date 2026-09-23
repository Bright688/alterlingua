package com.alterlingua.app.keyboard.handwriting

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRecognizer(var canPrepare: Boolean = true, var downloads: Boolean = false) : InkRecognizer {
    val recognized = mutableListOf<Int>() // how many strokes each recognition saw
    var answer = listOf("好", "妈", "奶")
    var preparedFor: String? = null
    var closed = false

    override suspend fun prepare(languageCode: String, onDownloading: () -> Unit): Boolean {
        if (downloads) onDownloading()
        preparedFor = languageCode
        return canPrepare
    }

    override suspend fun recognize(strokes: List<InkStroke>): List<String> {
        recognized += strokes.size
        return answer
    }

    override fun close() { closed = true }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HandwritingControllerTest {
    private val scope = TestScope()
    private val recognizer = FakeRecognizer()
    private val controller = HandwritingController(recognizer, scope, pauseMillis = 600)
    private val stroke = listOf(InkPoint(1f, 1f, 0), InkPoint(5f, 9f, 40))

    private fun ready() {
        controller.open("zh")
        scope.runCurrent()
    }

    @Test
    fun opening_preparesTheRecogniserForTheLanguage_andBecomesReady() {
        controller.open("fr")
        assertEquals(HandwritingStatus.PREPARING, controller.state.status)
        scope.runCurrent()
        assertEquals("fr", recognizer.preparedFor)
        assertEquals(HandwritingStatus.READY, controller.state.status)
    }

    @Test
    fun aFirstDownloadIsShown_andAFailureCanBeRetried() {
        recognizer.canPrepare = false
        recognizer.downloads = true
        val seen = mutableListOf<HandwritingStatus>()
        controller.onChanged = { seen += it.status }
        controller.open("ja")
        scope.runCurrent()
        assertTrue(HandwritingStatus.DOWNLOADING in seen)
        assertEquals(HandwritingStatus.FAILED, controller.state.status)
        recognizer.canPrepare = true
        controller.retry()
        scope.runCurrent()
        assertEquals(HandwritingStatus.READY, controller.state.status)
    }

    @Test
    fun recognitionFollowsAPauseAfterTheLastStroke() {
        ready()
        controller.strokeFinished(stroke)
        assertTrue(controller.state.hasInk)
        scope.advanceTimeBy(599); scope.runCurrent()
        assertTrue("not yet", recognizer.recognized.isEmpty())
        scope.advanceTimeBy(2); scope.runCurrent()
        assertEquals(listOf(1), recognizer.recognized)
        assertEquals(listOf("好", "妈", "奶"), controller.state.candidates)
    }

    @Test
    fun aNewStrokeBeforeThePauseEnds_makesOneRecognitionOfAllStrokes() {
        ready()
        controller.strokeFinished(stroke)
        scope.advanceTimeBy(300); scope.runCurrent()
        controller.strokeStarted()
        controller.strokeFinished(stroke)
        scope.advanceUntilIdle()
        assertEquals(listOf(2), recognizer.recognized)
    }

    @Test
    fun choosingACandidateGivesItsText_andClearsThePad() {
        ready()
        controller.strokeFinished(stroke)
        scope.advanceUntilIdle()
        assertEquals("妈", controller.choose(1))
        assertTrue(controller.state.candidates.isEmpty())
        assertFalse(controller.state.hasInk)
        controller.strokeFinished(stroke)
        scope.advanceUntilIdle()
        assertEquals("only the new stroke is read", 1, recognizer.recognized.last())
        assertNull(controller.choose(9))
    }

    @Test
    fun clearing_reportsWhetherThereWasAnythingToClear() {
        ready()
        assertFalse(controller.clear())
        controller.strokeFinished(stroke)
        assertTrue(controller.clear())
        scope.advanceUntilIdle()
        assertTrue("a cleared pad is not recognised", recognizer.recognized.isEmpty())
        assertFalse(controller.state.hasInk)
    }

    @Test
    fun strokesAreIgnoredUntilTheRecogniserIsReady() {
        recognizer.canPrepare = false
        ready()
        controller.strokeFinished(stroke)
        scope.advanceUntilIdle()
        assertTrue(recognizer.recognized.isEmpty())
    }

    @Test
    fun closing_releasesTheRecogniser() {
        ready()
        controller.close()
        assertTrue(recognizer.closed)
    }
}
