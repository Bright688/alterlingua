package com.alterlingua.app.share

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [SpeechAudioEncoder.encode] needs a real device codec, so only [SpeechAudioEncoder.readPcmWav]'s plain file
 * parsing -- the part a crafted WAV can exercise without Android -- is covered here. */
class SpeechAudioEncoderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun le16(v: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun wav(sampleRate: Int, channels: Int, data: ByteArray, extraChunk: Pair<String, ByteArray>? = null): File {
        val out = ByteArrayOutputStream()
        val fmt = ByteArrayOutputStream().apply {
            write(le16(1)) // PCM
            write(le16(channels))
            write(le32(sampleRate))
            write(le32(sampleRate * channels * 2))
            write(le16(channels * 2))
            write(le16(16))
        }.toByteArray()

        val chunks = ByteArrayOutputStream()
        extraChunk?.let { (id, bytes) ->
            chunks.write(id.toByteArray(Charsets.US_ASCII))
            chunks.write(le32(bytes.size))
            chunks.write(bytes)
            if (bytes.size % 2 == 1) chunks.write(0) // odd-sized chunks pad to an even boundary
        }
        chunks.write("fmt ".toByteArray(Charsets.US_ASCII))
        chunks.write(le32(fmt.size))
        chunks.write(fmt)
        chunks.write("data".toByteArray(Charsets.US_ASCII))
        chunks.write(le32(data.size))
        chunks.write(data)
        val body = chunks.toByteArray()

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32(4 + body.size)) // "WAVE" + chunks
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write(body)

        return folder.newFile("in.wav").also { it.writeBytes(out.toByteArray()) }
    }

    @Test fun readsSampleRateChannelsAndData() {
        val samples = byteArrayOf(1, 2, 3, 4, 5, 6)
        val pcm = SpeechAudioEncoder.readPcmWav(wav(sampleRate = 24_000, channels = 1, data = samples))
        assertEquals(24_000, pcm.sampleRate)
        assertEquals(1, pcm.channelCount)
        assertArrayEquals(samples, pcm.samples)
    }

    @Test fun skipsChunksItDoesNotNeed_evenAnOddSizedOneBeforeData() {
        val samples = byteArrayOf(9, 8, 7, 6)
        val pcm = SpeechAudioEncoder.readPcmWav(wav(sampleRate = 16_000, channels = 2, data = samples, extraChunk = "LIST" to byteArrayOf(1, 2, 3)))
        assertEquals(16_000, pcm.sampleRate)
        assertEquals(2, pcm.channelCount)
        assertArrayEquals(samples, pcm.samples)
    }

    @Test(expected = Exception::class) fun rejectsSomethingThatIsNotRiffWave() {
        val notWav = folder.newFile("not.wav").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)) }
        SpeechAudioEncoder.readPcmWav(notWav)
    }
}
