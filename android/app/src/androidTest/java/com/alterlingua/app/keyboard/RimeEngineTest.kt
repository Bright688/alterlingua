package com.alterlingua.app.keyboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alterlingua.app.keyboard.engine.RimeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real librime on a device or emulator: loading, deploying the schema, pinyin to candidates, choosing one. */
@RunWith(AndroidJUnit4::class)
class RimeEngineTest {
    private val engine = RimeEngine(ApplicationProvider.getApplicationContext())

    @Test
    fun theLibraryAndSchemaLoad() {
        assertTrue("librime and the schema should load", engine.available)
    }

    @Test
    fun pinyinGivesHanziCandidates_andChoosingCommitsThem() {
        assertTrue(engine.available)
        var composition = engine.append("n")
        composition = engine.append("i")
        composition = engine.append("h")
        composition = engine.append("a")
        composition = engine.append("o")
        assertEquals("nihao", composition.typed)
        assertEquals("the engine's reading splits the syllables", "ni hao", composition.preedit)
        assertTrue("expected 你好 among ${composition.candidates}", "你好" in composition.candidates)
        val choice = engine.choose(composition.candidates.indexOf("你好"))
        assertEquals("你好", choice.committed)
        assertTrue(choice.remaining.isEmpty)
    }

    @Test
    fun backspaceRemovesTheLastLetter() {
        assertTrue(engine.available)
        engine.append("n"); engine.append("i")
        assertEquals("n", engine.deleteLast().preedit)
        engine.reset()
    }

    @Test
    fun strokeKeys_offerCharactersForTheStrokesTyped() {
        val stroke = RimeEngine(ApplicationProvider.getApplicationContext(), RimeEngine.SCHEMA_STROKE)
        assertTrue(stroke.available)
        var composition = com.alterlingua.app.keyboard.engine.Composition()
        "hs".forEach { composition = stroke.append(it.toString()) } // horizontal, vertical: 十 starts this way
        assertTrue("expected 十 in $composition", "十" in composition.candidates)
        assertEquals("hs", composition.typed)
        assertEquals("十", stroke.choose(composition.candidates.indexOf("十")).committed)
        stroke.close()
    }

    @Test
    fun zhuyinKeys_offerCharactersForTheBopomofoTyped() {
        val zhuyin = RimeEngine(ApplicationProvider.getApplicationContext(), RimeEngine.SCHEMA_ZHUYIN)
        assertTrue(zhuyin.available)
        var composition = com.alterlingua.app.keyboard.engine.Composition()
        "su".forEach { composition = zhuyin.append(it.toString()) } // ㄋ ㄧ = ni
        assertTrue("expected 你 in $composition", "你" in composition.candidates)
        zhuyin.reset()
        "sucl".forEach { composition = zhuyin.append(it.toString()) } // ㄋ ㄧ ㄏ ㄠ = ni hao
        assertTrue("expected 你好 in $composition", "你好" in composition.candidates)
        zhuyin.close()
    }
}
