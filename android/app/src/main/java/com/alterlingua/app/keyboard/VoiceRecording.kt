package com.alterlingua.app.keyboard

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID

/** Records from the microphone into a file. Behind an interface so the voice flow can be tested without a phone. */
interface VoiceRecorder {
    /** Starts recording into [file]. False if the microphone could not be used. */
    fun start(file: File): Boolean

    /** The loudness since the last call, 0 to 32767. */
    fun amplitude(): Int

    /** Stops and finishes the file. False if there is no usable recording. */
    fun stop(): Boolean

    /** Stops and throws the recording away. */
    fun cancel()
}

/** Plays a recording back so the user can check what was captured. */
interface VoicePlayer {
    /** Starts playing; [onDone] is called when it ends by itself. False if it cannot play. */
    fun play(file: File, onDone: () -> Unit): Boolean

    fun stop()
}

/**
 * Where temporary recordings live: the app's private cache folder, which no other app (WhatsApp included) can read.
 * Every recording is deleted when the voice panel closes, and anything left behind by a crash is swept at start.
 */
class VoiceFiles(private val directory: File) {
    fun newFile(): File {
        directory.mkdirs()
        return File(directory, "voice-${UUID.randomUUID()}.m4a")
    }

    fun delete(file: File?) {
        file?.delete()
    }

    /** Deletes every leftover recording. Only called when no recording is in use. */
    fun sweep() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        fun forContext(context: Context) = VoiceFiles(File(context.cacheDir, "voice"))
    }
}

/** AAC audio in an MP4 file, which the backend accepts as `audio/mp4`. Mono at 16 kHz keeps a minute under 0.5 MB. */
class MediaRecorderVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null

    override fun start(file: File): Boolean {
        release()
        val new = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            new.setAudioSource(MediaRecorder.AudioSource.MIC)
            new.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            new.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            new.setAudioChannels(1)
            new.setAudioSamplingRate(16_000)
            new.setAudioEncodingBitRate(48_000)
            new.setOutputFile(file.absolutePath)
            new.prepare()
            new.start()
            recorder = new
            true
        } catch (_: IOException) {
            new.release()
            false
        } catch (_: RuntimeException) { // includes "the microphone is in use" and a missing permission
            new.release()
            false
        }
    }

    override fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: RuntimeException) {
        0
    }

    override fun stop(): Boolean {
        val active = recorder ?: return false
        return try {
            active.stop()
            true
        } catch (_: RuntimeException) { // stopping straight after starting has nothing to save
            false
        } finally {
            release()
        }
    }

    override fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // nothing recorded yet
        }
        release()
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }
}

class MediaPlayerVoicePlayer : VoicePlayer {
    private var player: MediaPlayer? = null

    override fun play(file: File, onDone: () -> Unit): Boolean {
        stop()
        val new = MediaPlayer()
        return try {
            new.setDataSource(file.absolutePath)
            new.setOnCompletionListener {
                stop()
                onDone()
            }
            new.prepare()
            new.start()
            player = new
            true
        } catch (_: IOException) {
            new.release()
            false
        } catch (_: RuntimeException) {
            new.release()
            false
        }
    }

    override fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // already stopped
            }
            it.release()
        }
        player = null
    }
}
