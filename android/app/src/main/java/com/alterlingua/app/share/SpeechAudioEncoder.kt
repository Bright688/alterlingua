package com.alterlingua.app.share

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android's on-device text-to-speech ([Speaker.synthesizeToFile]) only ever writes plain PCM WAV. That plays back
 * fine in AlterLingua itself and through a generic Share, but WhatsApp's own voice-message bubble -- the one its
 * Share hand-off lands a short audio file into -- expects a compressed voice-note-shaped file the way WhatsApp's
 * own recordings are, and refuses to play a raw WAV with "Couldn't process audio" even though the WAV itself is
 * perfectly valid (found 2026-10-17: confirmed byte-for-byte against the WAV header and decoded cleanly outside
 * WhatsApp). This re-encodes that WAV into AAC-in-MP4 (`.m4a`), a compressed format common to real voice/audio
 * messages, entirely on-device with the platform's own [MediaCodec]/[MediaMuxer] -- no new library, nothing leaves
 * the phone a second time.
 */
object SpeechAudioEncoder {
    private const val AAC_MIME = "audio/mp4a-latm"
    private const val BIT_RATE = 64_000

    /** Re-encodes the PCM [wav] (as written by Android's TextToSpeech) into AAC-in-MP4 at [output]. True on
     * success; [wav] is left untouched either way for the caller to clean up. */
    fun wavToAac(wav: File, output: File): Boolean = runCatching {
        val pcm = readPcmWav(wav)
        encode(pcm.samples, pcm.sampleRate, pcm.channelCount, output)
        true
    }.getOrDefault(false)

    internal data class Pcm(val samples: ByteArray, val sampleRate: Int, val channelCount: Int)

    /** Reads the handful of RIFF/WAVE chunks Android's own TextToSpeech ever writes (fmt , data); anything else is
     * skipped by its declared size rather than assumed absent. Internal (not private) so a plain JVM unit test can
     * exercise the parsing directly -- [encode] itself needs a real [MediaCodec], which only a device has. */
    internal fun readPcmWav(file: File): Pcm {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE") {
                "Not a RIFF/WAVE file"
            }
            var sampleRate = 0
            var channelCount = 0
            var data: ByteArray? = null
            val chunkHeader = ByteArray(8)
            while (raf.filePointer < raf.length()) {
                val read = raf.read(chunkHeader)
                if (read < 8) break
                val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        channelCount = buf.getShort(2).toInt()
                        sampleRate = buf.getInt(4)
                    }
                    "data" -> {
                        data = ByteArray(size)
                        raf.readFully(data)
                    }
                    else -> raf.seek(raf.filePointer + size)
                }
                if (size % 2 == 1 && raf.filePointer < raf.length()) raf.seek(raf.filePointer + 1) // chunks pad to even sizes
            }
            require(sampleRate > 0 && channelCount > 0 && data != null) { "Missing fmt or data chunk" }
            return Pcm(data, sampleRate, channelCount)
        }
    }

    private fun encode(pcm: ByteArray, sampleRate: Int, channelCount: Int, output: File) {
        val format = MediaFormat.createAudioFormat(AAC_MIME, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(AAC_MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val remaining = pcm.size - offset
                        val chunk = minOf(buffer.capacity(), remaining)
                        buffer.clear()
                        if (chunk > 0) {
                            buffer.put(pcm, offset, chunk)
                            offset += chunk
                        }
                        val flags = if (offset >= pcm.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(inputIndex, 0, chunk, 0, flags)
                        if (flags != 0) inputDone = true
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outputIndex)!!
                        if (info.size > 0 && muxerStarted) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (eos) break
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }

    private const val TIMEOUT_US = 10_000L
}
