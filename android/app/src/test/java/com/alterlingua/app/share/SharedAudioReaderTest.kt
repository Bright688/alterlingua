package com.alterlingua.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private class FakeSource(
    var declared: String? = "audio/ogg",
    var bytes: ByteArray? = ByteArray(0),
    var declaredThrows: Boolean = false,
    var openThrows: Throwable? = null,
    var streamFails: Boolean = false,
) : AudioSource {
    val opened = mutableListOf<String>()
    override fun declaredType(address: String): String? {
        if (declaredThrows) throw SecurityException("no permission")
        return declared
    }

    override fun open(address: String): InputStream? {
        opened += address
        openThrows?.let { throw it }
        val data = bytes ?: return null
        return if (streamFails) object : InputStream() { override fun read(): Int = throw IOException("broken") } else ByteArrayInputStream(data)
    }
}

class SharedAudioReaderTest {
    @get:Rule val folder = TemporaryFolder()

    private val ogg = "OggS".toByteArray() + ByteArray(2_000) { 7 }
    private val address = "content://com.whatsapp.provider.media/item/1"

    private fun reader(source: AudioSource, max: Long = SharedAudioReader.DEFAULT_MAX_BYTES, now: () -> Long = System::currentTimeMillis) =
        SharedAudioReader(source, folder.root.resolve("cache"), max, now)

    private fun leftovers(r: SharedAudioReader): Int = folder.root.resolve("cache").listFiles()?.size ?: 0

    private fun failure(r: AudioReadResult) = (r as AudioReadResult.Failure).reason
    private fun success(r: AudioReadResult) = (r as AudioReadResult.Success).audio

    // ---- what is accepted ----

    @Test fun aWhatsAppVoiceNote_isCopiedIntoAPrivateTemporaryFile() {
        val r = reader(FakeSource(declared = "audio/ogg; codecs=opus", bytes = ogg))
        val audio = success(r.read(address))
        assertEquals("audio/ogg", audio.contentType)
        assertEquals(ogg.size.toLong(), audio.bytes)
        assertTrue(audio.file.readBytes().contentEquals(ogg))
        assertTrue(audio.file.absolutePath.startsWith(folder.root.absolutePath))
    }

    @Test fun eachSupportedFormat_isRecognisedFromItsBytes() {
        val samples = mapOf(
            "audio/wav" to "RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.ISO_8859_1),
            "audio/flac" to "fLaC\u0000\u0000".toByteArray(Charsets.ISO_8859_1),
            "audio/amr" to "#!AMR\n".toByteArray(),
            "audio/mp4" to byteArrayOf(0, 0, 0, 0x18) + "ftypM4A ".toByteArray(),
            "audio/mpeg" to "ID3\u0003\u0000".toByteArray(Charsets.ISO_8859_1),
            "audio/webm" to byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2),
            "audio/aac" to byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()),
            "audio/ogg" to "OggS\u0000".toByteArray(Charsets.ISO_8859_1),
        )
        for ((type, bytes) in samples) {
            val r = reader(FakeSource(declared = "audio/*", bytes = bytes + ByteArray(50)))
            assertEquals(type, success(r.read(address)).contentType)
        }
    }

    @Test fun aMissingOrOctetStreamDeclaredType_isFine_ifTheBytesAreAudio() {
        assertEquals("audio/ogg", success(reader(FakeSource(declared = null, bytes = ogg)).read(address)).contentType)
        assertEquals("audio/ogg", success(reader(FakeSource(declared = "application/octet-stream", bytes = ogg)).read(address)).contentType)
        assertEquals("audio/ogg", success(reader(FakeSource(declared = "application/ogg", bytes = ogg)).read(address)).contentType)
    }

    // ---- what is refused ----

    @Test fun noAddress_isRefused_withoutOpeningAnything() {
        val source = FakeSource(bytes = ogg)
        val r = reader(source)
        assertEquals(ShareFailure.NO_AUDIO, failure(r.read(null)))
        assertEquals(ShareFailure.NO_AUDIO, failure(r.read("  ")))
        assertTrue(source.opened.isEmpty())
    }

    @Test fun onlyContentAddressesAreRead_neverFilePaths() {
        val source = FakeSource(bytes = ogg)
        val r = reader(source)
        for (bad in listOf("file:///data/data/com.whatsapp/files/x.opus", "/sdcard/x.opus", "http://example.com/a.ogg", "android.resource://a/1", "content:/missing-slash")) {
            assertEquals(bad, ShareFailure.NOT_A_CONTENT_ADDRESS, failure(r.read(bad)))
        }
        assertTrue("nothing was opened", source.opened.isEmpty())
        assertEquals(0, leftovers(r))
    }

    @Test fun aFileThatIsNotAudio_isRefused_andNothingIsKept() {
        val r = reader(FakeSource(declared = "audio/ogg", bytes = "hello, this is plain text".toByteArray()))
        assertEquals(ShareFailure.NOT_AUDIO, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aDeclaredNonAudioType_isRefusedEvenIfTheBytesLookLikeAudio() {
        val r = reader(FakeSource(declared = "text/plain", bytes = ogg))
        assertEquals(ShareFailure.NOT_AUDIO, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun anEmptyFile_isRefused() {
        val r = reader(FakeSource(bytes = ByteArray(0)))
        assertEquals(ShareFailure.EMPTY, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aRecordingOverTheLimit_isRefused_andTheCopyIsDeleted() {
        val r = reader(FakeSource(bytes = "OggS".toByteArray() + ByteArray(5_000)), max = 4_000)
        assertEquals(ShareFailure.TOO_LARGE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aRecordingExactlyAtTheLimit_isAccepted() {
        val bytes = "OggS".toByteArray() + ByteArray(996)
        assertEquals(1_000L, success(reader(FakeSource(bytes = bytes), max = 1_000).read(address)).bytes)
    }

    // ---- temporary permission ----

    @Test fun androidRefusingTheItem_isReportedAsAPermissionProblem() {
        val a = reader(FakeSource(declaredThrows = true))
        assertEquals(ShareFailure.PERMISSION_DENIED, failure(a.read(address)))
        val b = reader(FakeSource(openThrows = SecurityException("expired")))
        assertEquals(ShareFailure.PERMISSION_DENIED, failure(b.read(address)))
        assertEquals(0, leftovers(b))
    }

    @Test fun anItemThatCannotBeOpened_isUnreadable() {
        assertEquals(ShareFailure.UNREADABLE, failure(reader(FakeSource(bytes = null)).read(address)))
        assertEquals(ShareFailure.UNREADABLE, failure(reader(FakeSource(openThrows = IOException("gone"))).read(address)))
    }

    @Test fun aStreamThatBreaksHalfWay_leavesNoFileBehind() {
        val r = reader(FakeSource(streamFails = true, bytes = ogg))
        assertEquals(ShareFailure.UNREADABLE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun theAddressIsOpenedExactlyOnce_andNothingIsRequestedPersistently() {
        val source = FakeSource(bytes = ogg)
        reader(source).read(address)
        assertEquals(listOf(address), source.opened)
    }

    // ---- clean up ----

    @Test fun sweepRemovesOldLeftovers_butNotFreshOnes() {
        var now = 10_000_000L
        val r = reader(FakeSource(bytes = ogg), now = { now })
        val old = success(r.read(address)).file.also { it.setLastModified(now - 2 * 60 * 60 * 1000) }
        val fresh = success(r.read(address)).file.also { it.setLastModified(now) }
        r.sweep()
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun theAudioSnifferRefusesEmptyAndUnknownBytes() {
        assertNull(AudioSniffer.contentTypeOf(ByteArray(0)))
        assertNull(AudioSniffer.contentTypeOf("%PDF-1.4".toByteArray()))
        assertNull(AudioSniffer.contentTypeOf("<html>".toByteArray()))
    }
}
