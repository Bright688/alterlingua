package com.alterlingua.app.share

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

/** Why a shared document could not be used. None of these involves what the document actually says. */
enum class DocumentShareFailure {
    NO_DOCUMENT,

    /** Only `content://` addresses are read: never a file path, which could point at another app's private files. */
    NOT_A_CONTENT_ADDRESS,

    /** Not a PDF or a Word (.docx) file, checked by its own bytes, not only by its declared type. */
    UNSUPPORTED_DOCUMENT_TYPE,
    TOO_LARGE,
    EMPTY,

    /** Android did not let AlterLingua read the item (the temporary permission is missing or has ended). */
    PERMISSION_DENIED,
    UNREADABLE,
}

/** A private copy of the shared document. It exists only until the flow that made it deletes it. */
class SharedDocument(val file: File, val contentType: String, val bytes: Long)

sealed interface DocumentReadResult {
    data class Success(val document: SharedDocument) : DocumentReadResult

    data class Failure(val reason: DocumentShareFailure) : DocumentReadResult
}

/**
 * Securely reads a PDF or Word (.docx) file another app shared with AlterLingua (Premium's document translation),
 * the same pattern [SharedAudioReader] and [SharedImageReader] already established:
 *
 * - Only a `content://` address is read, through Android's ContentResolver, using the temporary permission the share
 *   granted for this one item. No other storage is touched.
 * - The bytes are copied at once into a private temporary file (the app's own cache), at most [maxBytes].
 * - What the file is comes from its own bytes ([DocumentSniffer]), not from the declared type alone.
 * - The caller deletes the copy as soon as it has been uploaded for translation; [sweep] removes leftovers (for
 *   example after a crash).
 */
class SharedDocumentReader(
    private val source: AudioSource,
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun read(address: String?): DocumentReadResult {
        if (address.isNullOrBlank()) return fail(DocumentShareFailure.NO_DOCUMENT)
        if (!address.startsWith("content://", ignoreCase = true)) return fail(DocumentShareFailure.NOT_A_CONTENT_ADDRESS)

        val declared = try {
            source.declaredType(address)
        } catch (_: SecurityException) {
            return fail(DocumentShareFailure.PERMISSION_DENIED)
        }
        val declaredBase = declared?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val stream = try {
            source.open(address)
        } catch (_: SecurityException) {
            return fail(DocumentShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            return fail(DocumentShareFailure.UNREADABLE)
        } ?: return fail(DocumentShareFailure.UNREADABLE)

        directory.mkdirs()
        val target = try {
            File.createTempFile("document-", ".tmp", directory)
        } catch (_: IOException) {
            stream.close()
            return fail(DocumentShareFailure.UNREADABLE)
        }
        return try {
            stream.use { input ->
                var total = 0L
                target.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            target.delete()
                            return fail(DocumentShareFailure.TOO_LARGE)
                        }
                        out.write(buffer, 0, read)
                    }
                }
                if (total == 0L) {
                    target.delete()
                    return fail(DocumentShareFailure.EMPTY)
                }
                val type = DocumentSniffer.contentTypeOf(target)
                if (type == null) {
                    target.delete()
                    return fail(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE)
                }
                // A declared type that clearly contradicts the real one is refused, even though many Share sources
                // declare a generic "application/octet-stream" for a document and are not contradicting anything.
                if (declaredBase != null && declaredBase != "application/octet-stream" && declaredBase != type) {
                    target.delete()
                    return fail(DocumentShareFailure.UNSUPPORTED_DOCUMENT_TYPE)
                }
                DocumentReadResult.Success(SharedDocument(target, type, total))
            }
        } catch (_: SecurityException) {
            target.delete()
            fail(DocumentShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            target.delete()
            fail(DocumentShareFailure.UNREADABLE)
        }
    }

    /** Deletes copies left behind longer than [olderThanMillis] ago (after a crash or a killed process). */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    private fun fail(reason: DocumentShareFailure) = DocumentReadResult.Failure(reason)

    companion object {
        /** Generous for a shared document; a much larger file is refused rather than uploaded in vain. */
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
        const val STALE_MILLIS = 60L * 60 * 1000
        private const val BUFFER = 16 * 1024
    }
}

/** Tells a PDF or Word (.docx) file apart from anything else, from the completed file's own bytes. */
object DocumentSniffer {
    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** The real content type of [file], or null if it is neither a PDF nor a Word (.docx) document. */
    fun contentTypeOf(file: File): String? {
        val head = ByteArray(8)
        val read = try {
            file.inputStream().use { it.read(head) }
        } catch (_: IOException) {
            return null
        }
        if (read < 4) return null
        if (read >= 5 && head[0] == 0x25.toByte() && head[1] == 0x50.toByte() && head[2] == 0x44.toByte() && head[3] == 0x46.toByte() && head[4] == 0x2D.toByte()) {
            return PDF // "%PDF-"
        }
        val isZip = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() && head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        return if (isZip && isDocx(file)) DOCX else null
    }

    /** A .docx is a zip archive; a real one has word/document.xml inside, which also tells it apart from a .zip,
     * a .xlsx or a .pptx that merely shares the zip container format. */
    private fun isDocx(file: File): Boolean = try {
        ZipFile(file).use { zip -> zip.getEntry("word/document.xml") != null }
    } catch (_: IOException) {
        false
    }
}
