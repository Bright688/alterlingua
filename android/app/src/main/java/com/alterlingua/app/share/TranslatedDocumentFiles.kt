package com.alterlingua.app.share

import java.io.File
import java.util.UUID

/**
 * Where a translated document is kept until the user saves or shares it: a folder in the app's private cache that
 * only the FileProvider's own declared paths reach (mirrors [com.alterlingua.app.speak.SpokenAudioFiles]).
 */
class TranslatedDocumentFiles(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    fun newFile(extension: String): File {
        directory.mkdirs()
        return File(directory, "translated-${UUID.randomUUID()}.$extension")
    }

    fun delete(file: File?) {
        file?.delete()
    }

    /** Removes anything older than [olderThanMillis]: leftovers of a crash or a killed screen. */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    companion object {
        const val STALE_MILLIS = 60L * 60 * 1000

        fun extensionFor(contentType: String): String = if (contentType == DocumentSniffer.PDF) "pdf" else "docx"
    }
}
