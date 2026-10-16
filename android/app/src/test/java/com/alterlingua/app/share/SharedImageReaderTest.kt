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

private class FakeImageSource(
    var declared: String? = "image/jpeg",
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

class SharedImageReaderTest {
    @get:Rule val folder = TemporaryFolder()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(2_000) { 7 }
    private val address = "content://com.whatsapp.provider.media/item/1"

    private fun reader(source: AudioSource, max: Long = SharedImageReader.DEFAULT_MAX_BYTES, now: () -> Long = System::currentTimeMillis) =
        SharedImageReader(source, folder.root.resolve("cache"), max, now)

    private fun leftovers(r: SharedImageReader): Int = folder.root.resolve("cache").listFiles()?.size ?: 0

    private fun failure(r: ImageReadResult) = (r as ImageReadResult.Failure).reason
    private fun success(r: ImageReadResult) = (r as ImageReadResult.Success).image

    // ---- what is accepted ----

    @Test fun aSharedPhoto_isCopiedIntoAPrivateTemporaryFile() {
        val r = reader(FakeImageSource(declared = "image/jpeg", bytes = jpeg))
        val image = success(r.read(address))
        assertEquals("image/jpeg", image.contentType)
        assertEquals(jpeg.size.toLong(), image.bytes)
        assertTrue(image.file.readBytes().contentEquals(jpeg))
        assertTrue(image.file.absolutePath.startsWith(folder.root.absolutePath))
    }

    @Test fun eachSupportedFormat_isRecognisedFromItsBytes() {
        val samples = mapOf(
            "image/jpeg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            "image/png" to byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()),
            "image/webp" to "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.ISO_8859_1),
            "image/gif" to "GIF89a".toByteArray(),
            "image/bmp" to "BM".toByteArray(),
        )
        for ((type, bytes) in samples) {
            val r = reader(FakeImageSource(declared = "image/*", bytes = bytes + ByteArray(50)))
            assertEquals(type, success(r.read(address)).contentType)
        }
    }

    @Test fun aMissingDeclaredType_isFine_ifTheBytesAreAnImage() {
        assertEquals("image/jpeg", success(reader(FakeImageSource(declared = null, bytes = jpeg)).read(address)).contentType)
    }

    // ---- what is refused ----

    @Test fun noAddress_isRefused_withoutOpeningAnything() {
        val source = FakeImageSource(bytes = jpeg)
        val r = reader(source)
        assertEquals(ImageShareFailure.NO_IMAGE, failure(r.read(null)))
        assertEquals(ImageShareFailure.NO_IMAGE, failure(r.read("  ")))
        assertTrue(source.opened.isEmpty())
    }

    @Test fun onlyContentAddressesAreRead_neverFilePaths() {
        val source = FakeImageSource(bytes = jpeg)
        val r = reader(source)
        for (bad in listOf("file:///data/data/com.whatsapp/files/x.jpg", "/sdcard/x.jpg", "http://example.com/a.jpg", "android.resource://a/1", "content:/missing-slash")) {
            assertEquals(bad, ImageShareFailure.NOT_A_CONTENT_ADDRESS, failure(r.read(bad)))
        }
        assertTrue("nothing was opened", source.opened.isEmpty())
        assertEquals(0, leftovers(r))
    }

    @Test fun aFileThatIsNotAnImage_isRefused_andNothingIsKept() {
        val r = reader(FakeImageSource(declared = "image/jpeg", bytes = "hello, this is plain text".toByteArray()))
        assertEquals(ImageShareFailure.NOT_AN_IMAGE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aDeclaredNonImageType_isRefusedEvenIfTheBytesLookLikeOne() {
        val r = reader(FakeImageSource(declared = "text/plain", bytes = jpeg))
        assertEquals(ImageShareFailure.NOT_AN_IMAGE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun anEmptyFile_isRefused() {
        val r = reader(FakeImageSource(bytes = ByteArray(0)))
        assertEquals(ImageShareFailure.EMPTY, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aPhotoOverTheLimit_isRefused_andTheCopyIsDeleted() {
        val r = reader(FakeImageSource(bytes = jpeg + ByteArray(5_000)), max = 4_000)
        assertEquals(ImageShareFailure.TOO_LARGE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aPhotoExactlyAtTheLimit_isAccepted() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(997)
        assertEquals(1_000L, success(reader(FakeImageSource(bytes = bytes), max = 1_000).read(address)).bytes)
    }

    // ---- temporary permission ----

    @Test fun androidRefusingTheItem_isReportedAsAPermissionProblem() {
        val a = reader(FakeImageSource(declaredThrows = true))
        assertEquals(ImageShareFailure.PERMISSION_DENIED, failure(a.read(address)))
        val b = reader(FakeImageSource(openThrows = SecurityException("expired")))
        assertEquals(ImageShareFailure.PERMISSION_DENIED, failure(b.read(address)))
        assertEquals(0, leftovers(b))
    }

    @Test fun anItemThatCannotBeOpened_isUnreadable() {
        assertEquals(ImageShareFailure.UNREADABLE, failure(reader(FakeImageSource(bytes = null)).read(address)))
        assertEquals(ImageShareFailure.UNREADABLE, failure(reader(FakeImageSource(openThrows = IOException("gone"))).read(address)))
    }

    @Test fun aStreamThatBreaksHalfWay_leavesNoFileBehind() {
        val r = reader(FakeImageSource(streamFails = true, bytes = jpeg))
        assertEquals(ImageShareFailure.UNREADABLE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun theAddressIsOpenedExactlyOnce() {
        val source = FakeImageSource(bytes = jpeg)
        reader(source).read(address)
        assertEquals(listOf(address), source.opened)
    }

    // ---- clean up ----

    @Test fun sweepRemovesOldLeftovers_butNotFreshOnes() {
        var now = 10_000_000L
        val r = reader(FakeImageSource(bytes = jpeg), now = { now })
        val old = success(r.read(address)).file.also { it.setLastModified(now - 2 * 60 * 60 * 1000) }
        val fresh = success(r.read(address)).file.also { it.setLastModified(now) }
        r.sweep()
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun theImageSnifferRefusesEmptyAndUnknownBytes() {
        assertNull(ImageSniffer.contentTypeOf(ByteArray(0)))
        assertNull(ImageSniffer.contentTypeOf("%PDF-1.4".toByteArray()))
        assertNull(ImageSniffer.contentTypeOf("<html>".toByteArray()))
    }
}
