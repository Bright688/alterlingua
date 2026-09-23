package com.alterlingua.app.share

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** Why shared audio could not be used. None of these involves any content of the audio. */
enum class ShareFailure {
    /** The share carried no file at all. */
    NO_AUDIO,

    /** Only `content://` addresses are read: never a file path, which could point at another app's private files. */
    NOT_A_CONTENT_ADDRESS,

    /** The file is not audio (checked by its first bytes as well as by its declared type). */
    NOT_AUDIO,
    TOO_LARGE,
    EMPTY,

    /** Android did not let AlterLingua read the item (the temporary permission is missing or has ended). */
    PERMISSION_DENIED,
    UNREADABLE,
}

/** A private copy of the shared audio. It exists only until the flow that made it deletes it. */
class SharedAudio(val file: File, val contentType: String, val bytes: Long)

sealed interface AudioReadResult {
    data class Success(val audio: SharedAudio) : AudioReadResult

    data class Failure(val reason: ShareFailure) : AudioReadResult
}

/** Where the shared bytes come from. The Android version is the ContentResolver, so a test can use plain streams. */
interface AudioSource {
    /** The type the sharing app declared for [address], or null. */
    fun declaredType(address: String): String?

    /** Opens the item, or null if it cannot be opened. May throw [SecurityException] when Android refuses. */
    fun open(address: String): InputStream?
}

/**
 * Securely reads audio another app shared with AlterLingua (CLAUDE.md 22).
 *
 * - Only a `content://` address is read, through Android's ContentResolver, using the temporary permission the share
 *   granted for this one item. Nothing is taken persistently and no other storage is touched; WhatsApp's private folders
 *   are never opened (they are not reachable that way, and file paths are refused).
 * - The bytes are copied at once into a private temporary file (the app's own cache), at most [maxBytes], so the flow does
 *   not depend on the permission afterwards.
 * - What the file is comes from its first bytes, not from the declared type alone; a file that is not audio is refused.
 * - The caller deletes the copy as soon as it has been processed; [sweep] removes leftovers (for example after a crash).
 */
class SharedAudioReader(
    private val source: AudioSource,
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun read(address: String?): AudioReadResult {
        if (address.isNullOrBlank()) return fail(ShareFailure.NO_AUDIO)
        if (!address.startsWith("content://", ignoreCase = true)) return fail(ShareFailure.NOT_A_CONTENT_ADDRESS)

        val declared = try {
            source.declaredType(address)
        } catch (_: SecurityException) {
            return fail(ShareFailure.PERMISSION_DENIED)
        }
        val declaredBase = declared?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val stream = try {
            source.open(address)
        } catch (_: SecurityException) {
            return fail(ShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            return fail(ShareFailure.UNREADABLE)
        } ?: return fail(ShareFailure.UNREADABLE)

        directory.mkdirs()
        val target = try {
            File.createTempFile("voice-", ".tmp", directory)
        } catch (_: IOException) {
            stream.close()
            return fail(ShareFailure.UNREADABLE)
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
                            return fail(ShareFailure.TOO_LARGE)
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
                    return fail(ShareFailure.EMPTY)
                }
                val type = AudioSniffer.contentTypeOf(head.copyOf(headSize))
                if (type == null) {
                    target.delete()
                    return fail(ShareFailure.NOT_AUDIO)
                }
                // A declared type that is clearly not audio is refused even if the bytes look like audio.
                if (declaredBase != null && !declaredBase.startsWith("audio/") && declaredBase != "application/ogg" && declaredBase != "application/octet-stream") {
                    target.delete()
                    return fail(ShareFailure.NOT_AUDIO)
                }
                AudioReadResult.Success(SharedAudio(target, type, total))
            }
        } catch (_: SecurityException) {
            target.delete()
            fail(ShareFailure.PERMISSION_DENIED)
        } catch (_: IOException) {
            target.delete()
            fail(ShareFailure.UNREADABLE)
        }
    }

    /** Deletes copies left behind longer than [olderThanMillis] ago (after a crash or a killed process). */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    private fun fail(reason: ShareFailure) = AudioReadResult.Failure(reason)

    companion object {
        /** The backend's default limit is 10 MB; a longer voice note is refused here rather than uploaded in vain. */
        const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024
        const val STALE_MILLIS = 60L * 60 * 1000
        private const val HEAD_BYTES = 16
        private const val BUFFER = 16 * 1024
    }
}

/** Tells the audio type from a file's first bytes. */
object AudioSniffer {
    /** A content type the backend accepts for these bytes, or null if they do not look like audio. */
    fun contentTypeOf(head: ByteArray): String? {
        fun starts(text: String, at: Int = 0) = head.size >= at + text.length && text.indices.all { head[at + it] == text[it].code.toByte() }
        fun b(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        return when {
            starts("OggS") -> "audio/ogg" // WhatsApp voice notes: Opus in an Ogg file
            starts("RIFF") && starts("WAVE", 8) -> "audio/wav"
            starts("fLaC") -> "audio/flac"
            starts("#!AMR") -> "audio/amr"
            starts("ftyp", 4) -> "audio/mp4"
            starts("ID3") -> "audio/mpeg"
            b(0) == 0x1A && b(1) == 0x45 && b(2) == 0xDF && b(3) == 0xA3 -> "audio/webm"
            b(0) == 0xFF && (b(1) and 0xF6) == 0xF0 -> "audio/aac" // ADTS
            b(0) == 0xFF && (b(1) and 0xE0) == 0xE0 -> "audio/mpeg"
            else -> null
        }
    }
}
