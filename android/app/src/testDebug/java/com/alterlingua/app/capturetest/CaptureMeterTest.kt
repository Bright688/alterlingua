package com.alterlingua.app.capturetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMeterTest {

    /** A tiny "sample rate" keeps the arrays small: 100 frames per second of 2 channels = 200 samples per second. */
    private fun meter() = CaptureMeter(sampleRate = 100, channels = 2)

    private fun samples(seconds: Double, amplitude: Int) = ShortArray((200 * seconds).toInt()) { amplitude.toShort() }

    @Test
    fun aCaptureThatDeliversOnlyZeros_isSilent_evenThoughSamplesKeepArriving() {
        val m = meter()
        m.add(samples(3.0, 0), 600)
        val s = m.summary()
        assertEquals(Verdict.SILENT, s.verdict)
        assertEquals(3, s.seconds)
        assertEquals(0, s.secondsWithSound)
        assertEquals(-96.0, s.peakDb, 0.0)
    }

    @Test
    fun steadySound_isHeard_andThePeakIsInDbfs() {
        val m = meter()
        val loud = samples(3.0, 10_000)
        m.add(loud, loud.size)
        val s = m.summary()
        assertEquals(Verdict.HEARD, s.verdict)
        assertEquals(3, s.secondsWithSound)
        assertEquals(20 * Math.log10(10_000 / 32768.0), s.peakDb, 0.001)
    }

    @Test
    fun oneSecondOfSoundAmongSilence_isOnlyFaint() {
        val m = meter()
        m.add(samples(2.0, 0), 400)
        m.add(samples(1.0, 8_000), 200)
        m.add(samples(2.0, 0), 400)
        val s = m.summary()
        assertEquals(Verdict.FAINT, s.verdict)
        assertEquals(1, s.secondsWithSound)
        assertEquals(5, s.seconds)
    }

    @Test
    fun aVoiceNoteThatPlaysForSeveralSeconds_isHeardEvenWithQuietGapsBetweenWords() {
        val m = meter()
        repeat(5) { second ->
            // a word in the middle of each second, the rest of the second silent
            val block = ShortArray(200)
            for (i in 80 until 120) block[i] = 6_000
            m.add(block, block.size)
            assertTrue("second ${second + 1} counted", m.summary().secondsWithSound == second + 1)
        }
        assertEquals(Verdict.HEARD, m.summary().verdict)
    }

    @Test
    fun theLevelBoundary_isJustAboveMinus55Dbfs() {
        // 58 / 32768 is -55.04 dBFS (silence); 59 / 32768 is -54.9 dBFS (sound).
        val below = meter().also { it.add(samples(2.0, 58), 400) }.summary()
        val above = meter().also { it.add(samples(2.0, 59), 400) }.summary()
        assertEquals(0, below.secondsWithSound)
        assertEquals(2, above.secondsWithSound)
    }

    @Test
    fun aFinalPartialSecond_countsIfItHoldsSamples() {
        val m = meter()
        m.add(samples(1.5, 9_000), 300)
        val s = m.summary()
        assertEquals(2, s.seconds)
        assertEquals(2, s.secondsWithSound)
    }

    @Test
    fun nothingAdded_isSilentWithZeroSeconds() {
        val s = meter().summary()
        assertEquals(0, s.seconds)
        assertEquals(Verdict.SILENT, s.verdict)
    }

    @Test
    fun theLoudestPossibleSample_isZeroDbfs_andTheQuietestNegativeValueDoesNotBreakAnything() {
        val m = meter()
        m.add(shortArrayOf(Short.MIN_VALUE), 1)
        assertEquals(0.0, m.summary().peakDb, 0.0001)
        assertEquals(-96.0, CaptureMeter.dbfs(0), 0.0)
    }

    @Test
    fun aCountLargerThanTheBuffer_isIgnoredBeyondTheBuffer() {
        val m = meter()
        m.add(shortArrayOf(5_000, 5_000), 1_000)
        assertEquals(1, m.summary().seconds) // only the two real samples were read
    }

    @Test
    fun theLiveLevel_showsTheSecondInProgress_andResetsWhenASecondCloses() {
        val m = meter()
        m.add(samples(0.5, 12_000), 100)
        assertEquals(20 * Math.log10(12_000 / 32768.0), m.livePeakDb, 0.001)
        m.add(samples(0.5, 12_000), 100) // the second closes
        assertEquals(-96.0, m.livePeakDb, 0.0)
    }
}
