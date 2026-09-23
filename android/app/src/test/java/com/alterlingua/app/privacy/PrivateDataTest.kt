package com.alterlingua.app.privacy

import com.alterlingua.app.learning.engine.Exposure
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningCandidate
import com.alterlingua.app.learning.engine.LearningEvent
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.engine.Usefulness
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.InMemoryDailyLessonStore
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.progress.InMemoryProgressStore
import com.alterlingua.app.learning.progress.ProgressLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivateDataTest {
    @get:Rule val folder = TemporaryFolder()

    private var now = 100_000_000L
    private val hour = 60L * 60 * 1000

    private fun cache(): File = folder.root

    private fun audio(name: String, folderName: String, age: Long): File {
        val dir = File(cache(), folderName).also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(byteArrayOf(1, 2, 3)); it.setLastModified(now - age) }
    }

    // ---- temporary audio ----

    @Test fun theStartUpSweepRemovesOldAudioFromEveryAudioFolder_andKeepsFreshAndUnrelatedFiles() {
        val old = TemporaryAudioFolders.NAMES.map { audio("old.tmp", it, 3 * hour) }
        val fresh = TemporaryAudioFolders.NAMES.map { audio("fresh.tmp", it, 5 * 60 * 1000) }
        val unrelated = audio("keep.txt", "somewhere_else", 5 * hour)
        TemporaryAudioFolders(cache()) { now }.sweep()
        assertTrue(old.none { it.exists() })
        assertTrue(fresh.all { it.exists() })
        assertTrue("only the audio folders are touched", unrelated.exists())
    }

    @Test fun everyFolderThatHoldsAudioIsCovered() {
        assertEquals(setOf("voice", "pronunciation", "shared_audio", "spoken_audio"), TemporaryAudioFolders.NAMES.toSet())
    }

    @Test fun deleteAllRemovesEverythingInThoseFolders() {
        val files = TemporaryAudioFolders.NAMES.map { audio("x.wav", it, 0) }
        TemporaryAudioFolders(cache()) { now }.deleteAll()
        assertTrue(files.none { it.exists() })
    }

    @Test fun aMissingFolderIsFine() {
        TemporaryAudioFolders(File(cache(), "nothing-here")) { now }.sweep()
        TemporaryAudioFolders(File(cache(), "nothing-here")) { now }.deleteAll()
    }

    // ---- delete all learning data ----

    private val mapStore = InMemoryLanguageMapStore()
    private val progressStore = InMemoryProgressStore()
    private val log = ProgressLog(progressStore, { now }, { java.time.ZoneOffset.UTC })
    private val map = LanguageMapService(mapStore, clock = { now }, progress = log)
    private val lessons = InMemoryDailyLessonStore()
    private var forgotten = 0

    private suspend fun fill() {
        for (language in listOf("fr", "es", "ja")) {
            val candidate = LearningCandidate("mot", "mot", UnitType.WORD, language, "en", Usefulness(0.5, emptyList()), Exposure(1, now, now))
            map.recordLearningEvent(LearningEvent("e", now, InteractionKind.INCOMING_MESSAGE, language, "en", listOf(candidate)))
        }
        lessons.save(DailyLesson("2026-09-21", "fr", emptyList()))
    }

    private fun eraser(forget: suspend () -> Unit = { forgotten++ }) =
        LearningDataEraser(map, log, lessons, TemporaryAudioFolders(cache()) { now }, forget)

    @Test fun erasingRemovesEveryLanguagesMap_theProgressHistory_todaysLesson_andTheAudio() = runTest {
        fill()
        val leftover = TemporaryAudioFolders.NAMES.map { audio("x.wav", it, 0) }
        assertTrue(eraser().eraseAll())
        for (language in listOf("fr", "es", "ja")) {
            assertTrue(language, mapStore.items(language).isEmpty())
            assertTrue(language, progressStore.days(language).isEmpty())
        }
        assertNull(lessons.load())
        assertTrue(leftover.none { it.exists() })
        assertEquals(1, forgotten)
    }

    @Test fun oneFailingStepDoesNotStopTheOthers_andIsReported() = runTest {
        fill()
        val failing = LearningDataEraser(
            map, object : ProgressLogStub() {}.log, lessons, TemporaryAudioFolders(cache()) { now },
        )
        val result = failing.eraseAll()
        assertFalse(result)
        assertTrue("the map was still erased", mapStore.items("fr").isEmpty())
        assertNull("and so was the lesson", lessons.load())
    }

    @Test fun aFailureToForgetMessages_isReported_butTheDataIsGone() = runTest {
        fill()
        assertFalse(eraser { error("boom") }.eraseAll())
        assertTrue(mapStore.items("fr").isEmpty())
    }

    @Test fun cancellationIsNotSwallowed() = runTest {
        fill()
        var thrown = false
        try {
            eraser { throw CancellationException("stop") }.eraseAll()
        } catch (_: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test fun erasingWithNothingStoredSucceeds() = runTest {
        assertTrue(eraser().eraseAll())
    }

    /** A progress log whose delete fails. */
    private open class ProgressLogStub {
        val log = ProgressLog(object : com.alterlingua.app.learning.progress.ProgressStore {
            override suspend fun update(date: String, language: String, change: (com.alterlingua.app.learning.progress.DailyActivity) -> com.alterlingua.app.learning.progress.DailyActivity) = Unit
            override suspend fun days(language: String) = emptyList<com.alterlingua.app.learning.progress.DailyActivity>()
            override suspend fun deleteLanguage(language: String) = Unit
            override suspend fun deleteAll(): Unit = throw java.io.IOException("disk error")
        })
    }
}
