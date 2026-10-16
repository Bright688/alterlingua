package com.alterlingua.app.share

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** Why a shared image could not be used. None of these involves what the image actually shows. */
enum class ImageShareFailure {
    NO_IMAGE,

    /** Only `content://` addresses are read: never a file path, which could point at another app's private files. */
    NOT_A_CONTENT_ADDRESS,

    /** The file is not an image (checked by its first bytes as well as by its declared type). */
    NOT_AN_IMAGE,
    TOO_LARGE,
    EMPTY,

    /** Android did not let AlterLingua read the item (the temporary permission is missing or has ended). */
    PERMISSION_DENIED,
    UNREADABLE,
}

/** A private copy of the shared image. It exists only until the flow that made it deletes it. */
class SharedImage(val file: File, val contentType: String, val bytes: Long)

sealed interface ImageReadResult {
    data class Success(val image: SharedImage) : ImageReadResult

    data class Failure(val reason: ImageShareFailure) : ImageReadResult
}

/**
 * Securely reads an image another app shared with AlterLingua (Standard/Premium's image translation), the same
 * pattern [SharedAudioReader] already established for voice notes:
 *
 * - Only a `content://` address is read, through Android's ContentResolver, using the temporary permission the share
 *   granted for this one item. No other storage is touched.
 * - The bytes are copied at once into a private temporary file (the app's own cache), at most [maxBytes].
 * - What the file is comes from its first bytes, not from the declared type alone; a file that is not an image is refused.
 * - The caller deletes the copy as soon as it has been processed; [sweep] removes leftovers (for example after a crash).
 */
class SharedImageReader(
    private val source: AudioSource,
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun read(address: String?): ImageReadResult {
        if (address.isNullOrBlank()) return fail(ImageShareFailure.NO_IMAGE)
        if (!address.startsWith("content://", ignoreCase = true)) return fail(ImageShareFailure.NOT_A_CONTENT_ADDRESS)

        val declared = try {
            source.declaredType(address)
        } catch (_: SecurityException) {
            return fail(ImageShareFailure.PERMISSION_DENIED)
        }
        val declaredBase = declared?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val stream = try {
            source.open(address)
        } catch (_: SecurityException) {
            return fail(ImageShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            return fail(ImageShareFailure.UNREADABLE)
        } ?: return fail(ImageShareFailure.UNREADABLE)

        directory.mkdirs()
        val target = try {
            File.createTempFile("image-", ".tmp", directory)
        } catch (_: IOException) {
            stream.close()
            return fail(ImageShareFailure.UNREADABLE)
        }
        return try {
            stream.use { input ->
                var total = 0L
                val head = ByteArray(HEAD_BYTES)
                var headSize = 0
                target.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            target.delete()
                            return fail(ImageShareFailure.TOO_LARGE)
                        }
                        if (headSize < HEAD_BYTES) {
                            val take = minOf(HEAD_BYTES - headSize, read)
                            System.arraycopy(buffer, 0, head, headSize, take)
                            headSize += take
                        }
                        out.write(buffer, 0, read)
                    }
                }
                if (total == 0L) {
                    target.delete()
                    return fail(ImageShareFailure.EMPTY)
                }
                val type = ImageSniffer.contentTypeOf(head.copyOf(headSize))
                if (type == null) {
                    target.delete()
                    return fail(ImageShareFailure.NOT_AN_IMAGE)
                }
                // A declared type that is clearly not an image is refused even if the bytes look like one.
                if (declaredBase != null && !declaredBase.startsWith("image/")) {
                    target.delete()
                    return fail(ImageShareFailure.NOT_AN_IMAGE)
                }
                ImageReadResult.Success(SharedImage(target, type, total))
            }
        } catch (_: SecurityException) {
            target.delete()
            fail(ImageShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            target.delete()
            fail(ImageShareFailure.UNREADABLE)
        }
    }

    /** Deletes copies left behind longer than [olderThanMillis] ago (after a crash or a killed process). */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    private fun fail(reason: ImageShareFailure) = ImageReadResult.Failure(reason)

    companion object {
        /** Generous for a shared photo or screenshot; a much larger file is refused rather than OCR'd in vain. */
        const val DEFAULT_MAX_BYTES = 15L * 1024 * 1024
        const val STALE_MILLIS = 60L * 60 * 1000
        private const val HEAD_BYTES = 16
        private const val BUFFER = 16 * 1024
    }
}

/** Tells the image type from a file's first bytes. */
object ImageSniffer {
    /** A content type for these bytes, or null if they do not look like an image AlterLingua can decode and OCR. */
    fun contentTypeOf(head: ByteArray): String? {
        fun starts(text: String, at: Int = 0) = head.size >= at + text.length && text.indices.all { head[at + it] == text[it].code.toByte() }
        fun b(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        return when {
            b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF -> "image/jpeg"
            b(0) == 0x89 && starts("PNG", 1) -> "image/png"
            starts("RIFF") && starts("WEBP", 8) -> "image/webp"
            starts("GIF87a") || starts("GIF89a") -> "image/gif"
            starts("BM") -> "image/bmp"
            else -> null
        }
    }
}
