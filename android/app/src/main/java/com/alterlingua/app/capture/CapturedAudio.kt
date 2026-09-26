package com.alterlingua.app.capture

import com.alterlingua.app.share.AudioSource
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a mono 16-bit recording as a plain WAV file, which the backend reads like any other audio. */
object WavFile {
    fun write(file: File, samples: ShortArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(HEADER - 8 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
        header.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        file.outputStream().buffered().use { out ->
            out.write(header.array())
            val chunk = ByteBuffer.allocate(CHUNK_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
            var index = 0
            while (index < samples.size) {
                chunk.clear()
                val end = minOf(samples.size, index + CHUNK_SAMPLES)
                while (index < end) chunk.putShort(samples[index++])
                out.write(chunk.array(), 0, chunk.position())
            }
        }
    }

    const val HEADER = 44
    private const val CHUNK_SAMPLES = 8192
}

/**
 * Hands a recording made by the capture feature to the same reading and translating flow that a shared voice note uses.
 *
 * The flow is built around addresses, so a recording gets one: `content://alterlingua-capture/<file name>`. It only ever
 * names a file directly inside [directory]; anything with a folder part is refused, so an address cannot point anywhere else.
 * The recording is deleted as soon as it has been read (the flow keeps its own private copy while a retry is possible).
 */
class CapturedAudioSource(private val directory: File) : AudioSource {

    override fun declaredType(address: String): String? = if (fileFor(address) != null) "audio/wav" else null

    override fun open(address: String): InputStream? {
        val file = fileFor(address) ?: return null
        val stream = try {
            file.inputStream()
        } catch (_: IOException) {
            return null
        }
        return object : FilterInputStream(stream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    file.delete()
                }
            }
        }
    }

    /** Deletes the recording behind [address], if it is one of ours and still there (a note closed before it was translated). */
    fun discard(address: String) {
        fileFor(address)?.delete()
    }

    private fun fileFor(address: String): File? {
        if (!address.startsWith(PREFIX)) return null
        val name = address.removePrefix(PREFIX)
        if (!NAME.matches(name)) return null
        return File(directory, name).takeIf { it.isFile }
    }

    companion object {
        const val PREFIX = "content://alterlingua-capture/"
        private val NAME = Regex("[A-Za-z0-9_-]{1,64}\\.wav")

        fun addressOf(file: File): String = PREFIX + file.name
    }
}
