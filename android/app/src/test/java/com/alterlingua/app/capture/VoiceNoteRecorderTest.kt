package com.alterlingua.app.capture

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteRecorderTest {

    private val rate = VoiceNoteRecorder.DEFAULT_INPUT_RATE

    /** [seconds] of stereo 44.1 kHz sound: a 300 Hz tone at [amplitude] (0 = exact silence), like Android's capture delivers. */
    private fun stereo(seconds: Double, amplitude: Double, startFrame: Int = 0): ShortArray {
        val frames = (seconds * rate).toInt()
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val v = (amplitude * Short.MAX_VALUE * sin(2 * PI * 300.0 * (startFrame + i) / rate)).toInt().toShort()
            out[i * 2] = v
            out[i * 2 + 1] = v
        }
        return out
    }

    /** Feeds [audio] in 200 ms chunks, as the service does. Returns the phase after the last chunk. */
    private fun feed(recorder: VoiceNoteRecorder, audio: ShortArray): RecorderPhase {
        val chunk = rate / 5 * 2
        var phase = recorder.state
        var at = 0
        while (at < audio.size) {
            val count = minOf(chunk, audio.size - at)
            phase = recorder.add(audio.copyOfRange(at, at + count), count)
            at += count
        }
        return phase
    }

    @Test
    fun silenceOnly_keepsWaiting_andGivesUpAfterTheLimit() {
        val recorder = VoiceNoteRecorder(giveUpMillis = 5_000)
        assertEquals(RecorderPhase.WAITING, feed(recorder, stereo(3.0, 0.0)))
        assertEquals(RecorderPhase.GAVE_UP, feed(recorder, stereo(3.0, 0.0)))
        assertNull(recorder.result())
    }

    @Test
    fun aVoiceNoteThenAPause_isRecordedAndEnds_withTheTrailingPauseTrimmed() {
        val recorder = VoiceNoteRecorder()
        assertEquals(RecorderPhase.WAITING, feed(recorder, stereo(2.0, 0.0)))
        assertEquals(RecorderPhase.RECORDING, feed(recorder, stereo(4.0, 0.3)))
        assertEquals(RecorderPhase.DONE, feed(recorder, stereo(3.0, 0.0)))
        val samples = recorder.result()
        assertNotNull(samples)
        val seconds = samples!!.size.toDouble() / recorder.outputRate
        // 4 s of sound plus a little before and after (about 0.3 s each), never the whole 2 s and 3 s of silence.
        assertTrue("was $seconds", seconds in 4.2..5.2)
    }

    @Test
    fun aShortPauseInsideSpeech_doesNotEndTheRecording() {
        val recorder = VoiceNoteRecorder()
        feed(recorder, stereo(2.0, 0.3))
        assertEquals(RecorderPhase.RECORDING, feed(recorder, stereo(1.5, 0.0)))
        assertEquals(RecorderPhase.RECORDING, feed(recorder, stereo(2.0, 0.3)))
        assertEquals(RecorderPhase.DONE, feed(recorder, stereo(3.0, 0.0)))
        assertTrue(recorder.result()!!.size.toDouble() / recorder.outputRate > 5.0)
    }

    @Test
    fun aShortBlip_isIgnored_andTheRecorderKeepsWaiting() {
        val recorder = VoiceNoteRecorder()
        feed(recorder, stereo(0.4, 0.4)) // a notification sound
        assertEquals(RecorderPhase.WAITING, feed(recorder, stereo(3.0, 0.0)))
        assertNull(recorder.result())
        // and a real voice note afterwards is still captured
        feed(recorder, stereo(3.0, 0.3))
        assertEquals(RecorderPhase.DONE, feed(recorder, stereo(3.0, 0.0)))
        assertNotNull(recorder.result())
    }

    @Test
    fun theUserStoppingEarly_keepsWhatWasHeard_ifItWasEnough() {
        val recorder = VoiceNoteRecorder()
        feed(recorder, stereo(3.0, 0.3))
        assertEquals(RecorderPhase.RECORDING, recorder.state)
        assertNotNull(recorder.result())

        val tooLittle = VoiceNoteRecorder()
        feed(tooLittle, stereo(0.3, 0.3))
        assertNull(tooLittle.result())
    }

    @Test
    fun aVeryLongNote_stopsAtTheLimit_soTheUploadStaysSmall() {
        val recorder = VoiceNoteRecorder(maximumMillis = 6_000)
        assertEquals(RecorderPhase.DONE, feed(recorder, stereo(9.0, 0.3)))
        val seconds = recorder.result()!!.size.toDouble() / recorder.outputRate
        assertTrue("was $seconds", seconds in 5.9..6.5)
    }

    @Test
    fun output_is16kHzMono_soItIsAFifthOfTheInputSize() {
        val recorder = VoiceNoteRecorder()
        feed(recorder, stereo(5.0, 0.3))
        feed(recorder, stereo(3.0, 0.0))
        val samples = recorder.result()!!
        // about 5 s of sound + 0.3 s before it + 0.3 s after, at 16 000 samples a second
        assertTrue("was ${samples.size}", samples.size in 80_000..90_000)
    }

    @Test
    fun resampling_keepsTheToneAudible_andContinuousAcrossChunks() {
        val recorder = VoiceNoteRecorder()
        feed(recorder, stereo(3.0, 0.5))
        feed(recorder, stereo(3.0, 0.0))
        val samples = recorder.result()!!
        val level = VoiceNoteRecorder.levelDb(samples.copyOfRange(16_000, 32_000))
        // A 0.5-amplitude sine has an RMS of about -9 dBFS.
        assertTrue("was $level", level in -11.0..-7.0)
        // Nothing jumps: neighbouring samples of a 300 Hz tone at 16 kHz differ by a small step, also where chunks joined.
        val inside = samples.copyOfRange(100, 40_000) // the tone only, not where it stops
        val biggestStep = (1 until inside.size).maxOf { kotlin.math.abs(inside[it] - inside[it - 1]) }
        assertTrue("was $biggestStep", biggestStep < 0.5 * Short.MAX_VALUE * 2 * PI * 300 / 16_000 * 1.2)
    }

    @Test
    fun levelOfSilenceIsTheFloor_andOfFullScaleIsAboutZero() {
        assertEquals(-96.0, VoiceNoteRecorder.levelDb(ShortArray(100)), 0.0)
        assertEquals(0.0, VoiceNoteRecorder.levelDb(ShortArray(100) { Short.MAX_VALUE }), 0.01)
    }
}
