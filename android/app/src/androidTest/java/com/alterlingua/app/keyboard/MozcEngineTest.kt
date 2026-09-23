package com.alterlingua.app.keyboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alterlingua.app.keyboard.engine.MozcEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Mozc library on a device or emulator: loading, romaji to hiragana, candidates and choosing one. */
@RunWith(AndroidJUnit4::class)
class MozcEngineTest {
    private val engine = MozcEngine(ApplicationProvider.getApplicationContext())

    @Test
    fun theLibraryAndDataLoad() {
        assertTrue("libmozc.so and mozc.data should load", engine.available)
    }

    @Test
    fun romajiBecomesHiraganaWithCandidates() {
        assertTrue(engine.available)
        var composition = engine.append("k")
        "yo".forEach { composition = engine.append(it.toString()) }
        composition = engine.append("u")
        assertEquals("きょう", composition.preedit)
        assertTrue("expected candidates, got ${composition.candidates}", composition.candidates.isNotEmpty())
        engine.reset()
    }

    @Test
    fun kanaInputAndChoosingACandidateCommitsKanji() {
        assertTrue(engine.available)
        var composition = engine.append("き")
        for (kana in listOf("ょ", "う")) composition = engine.append(kana)
        assertEquals("きょう", composition.preedit)
        val index = composition.candidates.indexOf("今日")
        assertTrue("今日 should be offered, got ${composition.candidates}", index >= 0)
        val choice = engine.choose(index)
        assertEquals("今日", choice.committed)
        assertTrue(choice.remaining.isEmpty)
    }

    @Test
    fun backspaceRemovesTheLastKana() {
        assertTrue(engine.available)
        engine.append("あ"); engine.append("い")
        assertEquals("あ", engine.deleteLast().preedit)
        engine.reset()
    }
}
