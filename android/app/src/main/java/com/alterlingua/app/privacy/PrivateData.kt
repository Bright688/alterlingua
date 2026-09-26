package com.alterlingua.app.privacy

import com.alterlingua.app.learning.lessons.DailyLessonStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.progress.ProgressLog
import java.io.File

/**
 * The app's temporary audio folders. Every recording and generated voice message lives in one of them, in the app's private
 * cache. Each flow deletes its own files when it finishes; [sweep] is the safety net for a crash or a killed process, run when
 * the app starts, and [deleteAll] is used when the user erases their data.
 */
class TemporaryAudioFolders(private val cacheDir: File, private val clock: () -> Long = System::currentTimeMillis) {

    /** Deletes audio files older than [olderThanMillis]. Newer ones may belong to a recording in progress. */
    fun sweep(olderThanMillis: Long = STALE_MILLIS) {
        val cutoff = clock() - olderThanMillis
        for (folder in folders()) folder.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    fun deleteAll() {
        for (folder in folders()) folder.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    private fun folders() = NAMES.map { File(cacheDir, it) }

    companion object {
        const val STALE_MILLIS = 60L * 60 * 1000

        /** Keep in step with where the app writes audio (see AppViewModelProvider). */
        val NAMES = listOf("voice", "pronunciation", "shared_audio", "spoken_audio", "captured_audio")
    }
}

/**
 * "Delete all learning data": removes everything AlterLingua has learned from the user's messages and practice: the
 * Personal Language Map of every language, the progress history, today's lesson, and any temporary audio. The user's
 * settings (languages, mode, switches) are kept. Every step is tried even if another fails, and the result says whether all
 * of them worked.
 */
class LearningDataEraser(
    private val map: LanguageMapService,
    private val progress: ProgressLog,
    private val lessons: DailyLessonStore,
    private val audio: TemporaryAudioFolders,
    /** Forgets in-memory message state (translated-notification text and the list of messages already translated). */
    private val forgetMessages: suspend () -> Unit = {},
) {
    suspend fun eraseAll(): Boolean {
        var everything = true
        suspend fun step(action: suspend () -> Unit) {
            try {
                action()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                everything = false
            }
        }
        step { map.deleteAll() }
        step { progress.deleteAll() }
        step { lessons.clear() }
        step { audio.deleteAll() }
        step { forgetMessages() }
        return everything
    }
}
