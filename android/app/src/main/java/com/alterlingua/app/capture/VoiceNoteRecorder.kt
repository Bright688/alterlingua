package com.alterlingua.app.capture

import kotlin.math.log10
import kotlin.math.sqrt

/** Where the recorder is: waiting for the voice note to start, listening to it, or finished. */
enum class RecorderPhase {
    /** Nothing has played yet (or only a short blip that was not a voice note). */
    WAITING,

    /** A voice note is playing. */
    RECORDING,

    /** The voice note ended (a pause long enough to be the end), or the length limit was reached. */
    DONE,

    /** Nothing that could be a voice note played for too long. */
    GAVE_UP,
}

/**
 * Turns the sound Android captures while a chat app plays a voice note into one short mono recording of just that voice note.
 *
 * The capture delivers a continuous stream of 44.1 kHz stereo (silence between sounds is exact zeros), so this:
 * - mixes to mono and resamples to 16 kHz (what speech recognition uses, and a fifth of the upload size);
 * - waits for real sound and starts a little before it, so the first word is not clipped;
 * - ends when a pause longer than a pause inside speech follows enough sound, and drops the pause;
 * - ignores a short blip (a notification sound, a tap) that is not followed by more sound;
 * - stops at a length limit that keeps the upload inside the backend's size limit.
 *
 * All timing is counted in samples, so it does not depend on the clock. Nothing is written anywhere here, and the sound is
 * never described in logs; the caller decides what to do with the result.
 */
class VoiceNoteRecorder(
    private val inputRate: Int = DEFAULT_INPUT_RATE,
    private val channels: Int = DEFAULT_CHANNELS,
    val outputRate: Int = OUTPUT_RATE,
    private val soundThresholdDb: Double = SOUND_DB,
    private val endSilenceMillis: Int = END_SILENCE_MILLIS,
    private val minimumSoundMillis: Int = MINIMUM_SOUND_MILLIS,
    private val maximumMillis: Int = MAXIMUM_MILLIS,
    private val giveUpMillis: Int = GIVE_UP_MILLIS,
) {
    init {
        require(inputRate > 0 && outputRate > 0 && channels > 0)
    }

    private val ratio = inputRate.toDouble() / outputRate
    private val windowSamples = outputRate * WINDOW_MILLIS / 1000

    // Resampler state: the last mono sample of the previous chunk, and where the next output sample falls.
    private var carry = 0f
    private var position = 0.0

    // The current 100 ms window of output samples.
    private val window = ShortArray(windowSamples)
    private var windowFill = 0

    private var recording = ShortArray(outputRate * 8)
    private var length = 0
    private var soundWindows = 0
    private var trailingSilentWindows = 0
    private var waitedWindows = 0

    var state: RecorderPhase = RecorderPhase.WAITING
        private set

    /** Feeds interleaved 16-bit samples ([count] values in all, a whole number of frames). Returns the phase afterwards. */
    fun add(samples: ShortArray, count: Int): RecorderPhase {
        if (state == RecorderPhase.DONE || state == RecorderPhase.GAVE_UP) return state
        val frames = count / channels
        if (frames < 1) return state
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels) sum += samples[frame * channels + channel].toInt()
            mono[frame] = sum.toFloat() / channels
        }
        // Linear interpolation; index -1 is the carried sample from the previous chunk.
        while (position < frames - 1) {
            val i = Math.floor(position).toInt()
            val a = if (i < 0) carry else mono[i]
            val b = mono[i + 1]
            val value = a + (b - a) * (position - i).toFloat()
            push(value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            position += ratio
            if (state == RecorderPhase.DONE || state == RecorderPhase.GAVE_UP) return state
        }
        carry = mono[frames - 1]
        position -= frames
        return state
    }

    /**
     * The recording so far, trimmed to the voice note, or null when what was heard was too little to be one. Used when the
     * phase is DONE, and by the user's own Stop.
     */
    fun result(): ShortArray? {
        if (soundWindows * WINDOW_MILLIS < minimumSoundMillis) return null
        val tail = (trailingSilentWindows - KEEP_WINDOWS).coerceAtLeast(0) * windowSamples
        val end = (length - tail).coerceAtLeast(0)
        return if (end == 0) null else recording.copyOf(end)
    }

    private fun push(sample: Short) {
        window[windowFill++] = sample
        if (windowFill == windowSamples) {
            windowFill = 0
            endOfWindow()
        }
    }

    private fun endOfWindow() {
        val loud = levelDb(window) >= soundThresholdDb
        when (state) {
            RecorderPhase.WAITING -> {
                if (loud) {
                    state = RecorderPhase.RECORDING
                    soundWindows = 1
                    trailingSilentWindows = 0
                    // Start a little before the sound: keep the last few silent windows seen while waiting.
                    length = 0
                    val preroll = minOf(quietTail, PREROLL_WINDOWS) * windowSamples
                    if (preroll > 0) append(pre, pre.size - preroll, preroll)
                    append(window, 0, windowSamples)
                } else {
                    remember(window)
                    waitedWindows++
                    if (waitedWindows * WINDOW_MILLIS >= giveUpMillis) state = RecorderPhase.GAVE_UP
                }
            }
            RecorderPhase.RECORDING -> {
                append(window, 0, windowSamples)
                if (loud) {
                    soundWindows++
                    trailingSilentWindows = 0
                } else {
                    trailingSilentWindows++
                }
                if (trailingSilentWindows * WINDOW_MILLIS >= endSilenceMillis) {
                    if (soundWindows * WINDOW_MILLIS >= minimumSoundMillis) {
                        state = RecorderPhase.DONE
                    } else {
                        // Only a blip: forget it and wait again.
                        state = RecorderPhase.WAITING
                        length = 0
                        soundWindows = 0
                        trailingSilentWindows = 0
                        quietTail = 0
                    }
                } else if (length * 1000L / outputRate >= maximumMillis) {
                    state = RecorderPhase.DONE
                }
            }
            else -> Unit
        }
    }

    // The last few quiet windows, kept only so the recording can begin a little before the first sound.
    private val pre = ShortArray(PREROLL_WINDOWS * windowSamples)
    private var quietTail = 0

    private fun remember(source: ShortArray) {
        System.arraycopy(pre, windowSamples, pre, 0, pre.size - windowSamples)
        System.arraycopy(source, 0, pre, pre.size - windowSamples, windowSamples)
        quietTail = minOf(quietTail + 1, PREROLL_WINDOWS)
    }

    private fun append(source: ShortArray, from: Int, count: Int) {
        if (length + count > recording.size) recording = recording.copyOf(maxOf(recording.size * 2, length + count))
        System.arraycopy(source, from, recording, length, count)
        length += count
    }

    companion object {
        const val DEFAULT_INPUT_RATE = 44_100
        const val DEFAULT_CHANNELS = 2
        const val OUTPUT_RATE = 16_000
        const val WINDOW_MILLIS = 100

        /** Louder than this (dBFS, over 100 ms) counts as sound. Captured silence is exactly zero, so this is generous. */
        const val SOUND_DB = -50.0

        /** A pause this long after enough sound is the end of the voice note; pauses inside speech are shorter. */
        const val END_SILENCE_MILLIS = 2_500
        const val MINIMUM_SOUND_MILLIS = 1_000

        /** 4 minutes of 16 kHz mono is about 7.7 MB: inside the backend's 10 MB limit. */
        const val MAXIMUM_MILLIS = 4 * 60 * 1000
        const val GIVE_UP_MILLIS = 90_000
        private const val PREROLL_WINDOWS = 3
        private const val KEEP_WINDOWS = 3

        fun levelDb(samples: ShortArray): Double {
            if (samples.isEmpty()) return -96.0
            var sum = 0.0
            for (sample in samples) sum += sample.toDouble() * sample
            val rms = sqrt(sum / samples.size) / Short.MAX_VALUE
            return if (rms <= 0.0) -96.0 else maxOf(-96.0, 20 * log10(rms))
        }
    }
}
