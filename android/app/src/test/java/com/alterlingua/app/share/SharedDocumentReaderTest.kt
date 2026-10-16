package com.alterlingua.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class FakeDocumentSource(
    var declared: String? = DocumentSniffer.PDF,
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

/** A real, tiny PDF: enough to pass the magic-byte check. */
private fun makePdfBytes(): ByteArray = ("%PDF-1.4\n%%EOF\n" + "x".repeat(50)).toByteArray()

/** A real zip with a word/document.xml entry: enough to pass the .docx check. */
private fun makeDocxBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write("<xml>hello</xml>".toByteArray())
        zip.closeEntry()
    }
    return out.toByteArray()
}

class SharedDocumentReaderTest {
    @get:Rule val folder = TemporaryFolder()

    private val pdf = makePdfBytes()
    private val docx = makeDocxBytes()
    private val address = "content://com.whatsapp.provider.media/item/1"

    private fun reader(source: AudioSource, max: Long = SharedDocumentReader.DEFAULT_MAX_BYTES, now: () -> Long = System::currentTimeMillis) =
        SharedDocumentReader(source, folder.root.resolve("cache"), max, now)

    private fun leftovers(r: SharedDocumentReader): Int = folder.root.resolve("cache").listFiles()?.size ?: 0

    private fun failure(r: DocumentReadResult) = (r as DocumentReadResult.Failure).reason
    private fun success(r: DocumentReadResult) = (r as DocumentReadResult.Success).document

    // ---- what is accepted ----

    @Test fun aSharedPdf_isCopiedIntoAPrivateTemporaryFile() {
        val r = reader(FakeDocumentSource(declared = DocumentSniffer.PDF, bytes = pdf))
        val document = success(r.read(address))
        assertEquals(DocumentSniffer.PDF, document.contentType)
        assertEquals(pdf.size.toLong(), document.bytes)
        assertTrue(document.file.readBytes().contentEquals(pdf))
        assertTrue(document.file.absolutePath.startsWith(folder.root.absolutePath))
    }

    @Test fun aSharedDocx_isCopiedIntoAPrivateTemporaryFile() {
        val r = reader(FakeDocumentSource(declared = DocumentSniffer.DOCX, bytes = docx))
        val document = success(r.read(address))
        assertEquals(DocumentSniffer.DOCX, document.contentType)
        assertTrue(document.file.readBytes().contentEquals(docx))
    }

    @Test fun aMissingDeclaredType_isFine_ifTheBytesAreADocument() {
        assertEquals(DocumentSniffer.PDF, success(reader(FakeDocumentSource(declared = null, bytes = pdf)).read(address)).contentType)
    }

    @Test fun aGenericOctetStreamDeclaredType_isFine_ifTheBytesAreADocument() {
        assertEquals(DocumentSniffer.DOCX, success(reader(FakeDocumentSource(declared = "application/octet-stream", bytes = docx)).read(address)).contentType)
    }

    // ---- what is refused ----

    @Test fun noAddress_isRefused_withoutOpeningAnything() {
        val source = FakeDocumentSource(bytes = pdf)
        val r = reader(source)
        assertEquals(DocumentShareFailure.NO_DOCUMENT, failure(r.read(null)))
        assertEquals(DocumentShareFailure.NO_DOCUMENT, failure(r.read("  ")))
        assertTrue(source.opened.isEmpty())
    }

    @Test fun onlyContentAddressesAreRead_neverFilePaths() {
        val source = FakeDocumentSource(bytes = pdf)
        val r = reader(source)
        for (bad in listOf("file:///data/data/com.whatsapp/files/x.pdf", "/sdcard/x.pdf", "http://example.com/a.pdf", "content:/missing-slash")) {
            assertEquals(bad, DocumentShareFailure.NOT_A_CONTENT_ADDRESS, failure(r.read(bad)))
        }
        assertTrue("nothing was opened", source.opened.isEmpty())
        assertEquals(0, leftovers(r))
    }

    @Test fun aFileThatIsNotASupportedDocument_isRefused_andNothingIsKept() {
        val r = reader(FakeDocumentSource(declared = DocumentSniffer.PDF, bytes = "hello, this is plain text".toByteArray()))
        assertEquals(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aPlainZipThatIsNotADocx_isRefused() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("hello.txt"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
        }
        val r = reader(FakeDocumentSource(declared = DocumentSniffer.DOCX, bytes = out.toByteArray()))
        assertEquals(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE, failure(r.read(address)))
    }

    @Test fun aDeclaredTypeThatContradictsTheRealOne_isRefused() {
        val r = reader(FakeDocumentSource(declared = DocumentSniffer.DOCX, bytes = pdf))
        assertEquals(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE, failure(r.read(address)))
    }

    @Test fun anEmptyFile_isRefused() {
        val r = reader(FakeDocumentSource(bytes = ByteArray(0)))
        assertEquals(DocumentShareFailure.EMPTY, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun aDocumentOverTheLimit_isRefused_andTheCopyIsDeleted() {
        val r = reader(FakeDocumentSource(bytes = pdf + ByteArray(5_000)), max = 4_000)
        assertEquals(DocumentShareFailure.TOO_LARGE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    // ---- temporary permission ----

    @Test fun androidRefusingTheItem_isReportedAsAPermissionProblem() {
        val a = reader(FakeDocumentSource(declaredThrows = true))
        assertEquals(DocumentShareFailure.PERMISSION_DENIED, failure(a.read(address)))
        val b = reader(FakeDocumentSource(openThrows = SecurityException("expired")))
        assertEquals(DocumentShareFailure.PERMISSION_DENIED, failure(b.read(address)))
        assertEquals(0, leftovers(b))
    }

    @Test fun anItemThatCannotBeOpened_isUnreadable() {
        assertEquals(DocumentShareFailure.UNREADABLE, failure(reader(FakeDocumentSource(bytes = null)).read(address)))
        assertEquals(DocumentShareFailure.UNREADABLE, failure(reader(FakeDocumentSource(openThrows = IOException("gone"))).read(address)))
    }

    @Test fun aStreamThatBreaksHalfWay_leavesNoFileBehind() {
        val r = reader(FakeDocumentSource(streamFails = true, bytes = pdf))
        assertEquals(DocumentShareFailure.UNREADABLE, failure(r.read(address)))
        assertEquals(0, leftovers(r))
    }

    @Test fun theAddressIsOpenedExactlyOnce() {
        val source = FakeDocumentSource(bytes = pdf)
        reader(source).read(address)
        assertEquals(listOf(address), source.opened)
    }

    // ---- clean up ----

    @Test fun sweepRemovesOldLeftovers_butNotFreshOnes() {
        var now = 10_000_000L
        val r = reader(FakeDocumentSource(bytes = pdf), now = { now })
        val old = success(r.read(address)).file.also { it.setLastModified(now - 2 * 60 * 60 * 1000) }
        val fresh = success(r.read(address)).file.also { it.setLastModified(now) }
        r.sweep()
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun theDocumentSnifferRefusesEmptyAndUnknownBytes() {
        val missing = folder.newFile("missing-content")
        missing.writeBytes(ByteArray(0))
        assertNull(DocumentSniffer.contentTypeOf(missing))
        val html = folder.newFile("html")
        html.writeBytes("<html>".toByteArray())
        assertNull(DocumentSniffer.contentTypeOf(html))
    }
}
