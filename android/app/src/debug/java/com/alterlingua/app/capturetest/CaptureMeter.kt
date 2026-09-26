package com.alterlingua.app.capturetest

import kotlin.math.abs
import kotlin.math.log10

/** How much sound a capture heard. Numbers only: the audio itself is never kept. */
enum class Verdict { HEARD, FAINT, SILENT }

data class CaptureSummary(
    /** Whole seconds listened to (a final partial second counts if it held any samples). */
    val seconds: Int,
    val secondsWithSound: Int,
    /** The loudest sample heard, in dBFS (0 is the loudest possible, -96 is digital silence). */
    val peakDb: Double,
    val verdict: Verdict,
)

/**
 * Measures the loudness of captured 16-bit PCM audio second by second, without keeping any of it.
 *
 * A capture that Android silences (an app that does not allow capture, or a sound type that cannot be captured) still
 * delivers a steady stream of samples, all zero, so "heard something" has to be judged from the level, not from whether
 * samples arrived.
 */
class CaptureMeter(sampleRate: Int, channels: Int, private val soundThresholdDb: Double = SOUND_THRESHOLD_DB) {
    private val samplesPerSecond = sampleRate * channels
    private var inSecond = 0
    private var secondPeak = 0
    private var overallPeak = 0
    private var completeSeconds = 0
    private var soundSeconds = 0

    /** The loudest sample of the second in progress, in dBFS: for a live level display. */
    val livePeakDb: Double get() = dbfs(secondPeak)

    /** Adds the first [count] samples of [buffer] (interleaved when there is more than one channel). */
    fun add(buffer: ShortArray, count: Int) {
        for (i in 0 until minOf(count, buffer.size)) {
            val value = abs(buffer[i].toInt())
            if (value > secondPeak) secondPeak = value
            if (value > overallPeak) overallPeak = value
            inSecond++
            if (inSecond >= samplesPerSecond) closeSecond()
        }
    }

    private fun closeSecond() {
        completeSeconds++
        if (dbfs(secondPeak) > soundThresholdDb) soundSeconds++
        secondPeak = 0
        inSecond = 0
    }

    fun summary(): CaptureSummary {
        var seconds = completeSeconds
        var sound = soundSeconds
        if (inSecond > 0) {
            seconds++
            if (dbfs(secondPeak) > soundThresholdDb) sound++
        }
        val verdict = when {
            sound >= 2 -> Verdict.HEARD
            sound == 1 -> Verdict.FAINT
            else -> Verdict.SILENT
        }
        return CaptureSummary(seconds, sound, dbfs(overallPeak), verdict)
    }

    companion object {
        /** Quieter than this counts as silence. Real speech played through a phone is far above it. */
        const val SOUND_THRESHOLD_DB = -55.0

        fun dbfs(peak: Int): Double = if (peak <= 0) -96.0 else maxOf(-96.0, 20 * log10(peak / 32768.0))
    }
}
