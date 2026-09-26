package com.alterlingua.app.capture

import com.alterlingua.app.share.AudioReadResult
import com.alterlingua.app.share.SharedAudioReader
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapturedAudioTest {
    @get:Rule val folder = TemporaryFolder()

    private fun captured(name: String = "note-abc123.wav", samples: ShortArray = ShortArray(1600) { (it % 100).toShort() }): File {
        val dir = File(folder.root, "captured_audio").apply { mkdirs() }
        return File(dir, name).also { WavFile.write(it, samples, 16_000) }
    }

    @Test
    fun aWavFile_hasAStandardHeader_andTheSamples() {
        val samples = ShortArray(1600) { (it * 3).toShort() }
        val file = captured(samples = samples)
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44 + samples.size * 2, bytes.capacity())
        assertEquals("RIFF", String(bytes.array(), 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.array(), 8, 4, Charsets.US_ASCII))
        assertEquals(1, bytes.getShort(20).toInt()) // PCM
        assertEquals(1, bytes.getShort(22).toInt()) // mono
        assertEquals(16_000, bytes.getInt(24))
        assertEquals(32_000, bytes.getInt(28))
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals("data", String(bytes.array(), 36, 4, Charsets.US_ASCII))
        assertEquals(samples.size * 2, bytes.getInt(40))
        assertEquals(samples[10], bytes.getShort(44 + 20))
        assertEquals(samples.last(), bytes.getShort(44 + (samples.size - 1) * 2))
    }

    @Test
    fun theSharedVoiceReader_acceptsARecording_asAudioWav_andTheSourceIsDeletedAfterwards() {
        val file = captured()
        val reader = SharedAudioReader(CapturedAudioSource(file.parentFile), File(folder.root, "shared_audio"))
        val result = reader.read(CapturedAudioSource.addressOf(file))
        assertTrue(result is AudioReadResult.Success)
        val audio = (result as AudioReadResult.Success).audio
        assertEquals("audio/wav", audio.contentType)
        assertTrue(audio.file.exists())
        assertFalse("the captured original is deleted once it has been read", file.exists())
    }

    @Test
    fun addressesThatDoNotNameAFileInsideTheFolder_areRefused() {
        val file = captured()
        File(folder.root, "secret.wav").writeBytes(file.readBytes())
        val source = CapturedAudioSource(file.parentFile)
        assertNull(source.open("content://alterlingua-capture/../secret.wav"))
        assertNull(source.open("content://alterlingua-capture/sub/note-abc123.wav"))
        assertNull(source.open("file:///etc/passwd"))
        assertNull(source.open("content://other.app/note-abc123.wav"))
        assertNull(source.open("content://alterlingua-capture/missing.wav"))
        assertNull(source.declaredType("content://alterlingua-capture/../secret.wav"))
        assertTrue(File(folder.root, "secret.wav").exists())
        assertTrue(file.exists())
    }
}
