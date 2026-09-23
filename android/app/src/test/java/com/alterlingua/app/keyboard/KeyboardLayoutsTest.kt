package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.KeyboardStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    private fun all(page: KeyboardPage) = KeyboardLayouts.rows(page).flatten()

    @Test
    fun everyRowFillsTheWidthExactly() {
        for (page in KeyboardPage.entries) {
            KeyboardLayouts.rows(page).forEachIndexed { index, row ->
                assertEquals("$page row $index", 10f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
            }
        }
    }

    @Test
    fun everyPageHasFourRows() {
        KeyboardPage.entries.forEach { assertEquals(4, KeyboardLayouts.rows(it).size) }
    }

    @Test
    fun lettersPageHasEachLatinLetterOnce_inQwertyOrder() {
        val letters = KeyboardLayouts.rows(KeyboardPage.LETTERS).take(3)
            .map { row -> row.filterIsInstance<KeySpec.Character>().joinToString("") { it.text } }
        assertEquals(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), letters)
    }

    @Test
    fun everyPageHasTheEssentialKeys() {
        for (page in KeyboardPage.entries) {
            val actions = all(page).filterIsInstance<KeySpec.Function>().map { it.action }
            assertTrue("$page backspace", KeyAction.BACKSPACE in actions)
            assertTrue("$page enter", KeyAction.ENTER in actions)
            assertTrue("$page space", KeyAction.SPACE in actions)
            assertTrue("$page switch keyboard", KeyAction.SWITCH_KEYBOARD in actions)
            val texts = all(page).filterIsInstance<KeySpec.Character>().map { it.text }
            assertTrue("$page comma", "," in texts)
            assertTrue("$page period", "." in texts)
        }
    }

    @Test
    fun shiftExistsOnlyOnTheLettersPage() {
        assertEquals(1, all(KeyboardPage.LETTERS).count { it is KeySpec.Function && it.action == KeyAction.SHIFT })
        assertEquals(0, all(KeyboardPage.SYMBOLS_1).count { it is KeySpec.Function && it.action == KeyAction.SHIFT })
    }

    @Test
    fun theSymbolPagesHaveDigitsAndCommonPunctuation() {
        val page1 = all(KeyboardPage.SYMBOLS_1).filterIsInstance<KeySpec.Character>().map { it.text }
        for (needed in listOf("0", "1", "9", "@", "#", "$", "-", "(", ")", "/", "\"", "'", ":", ";", "!", "?", "*", "&")) {
            assertTrue("missing $needed", needed in page1)
        }
        val page2 = all(KeyboardPage.SYMBOLS_2).filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("€" in page2 && "£" in page2)
    }

    @Test
    fun theTwoSymbolPagesAndLettersReachEachOther() {
        fun actions(page: KeyboardPage) = all(page).filterIsInstance<KeySpec.Function>().map { it.action }
        assertTrue(KeyAction.SHOW_SYMBOLS in actions(KeyboardPage.LETTERS))
        assertTrue(KeyAction.SHOW_MORE_SYMBOLS in actions(KeyboardPage.SYMBOLS_1))
        assertTrue(KeyAction.SHOW_SYMBOLS in actions(KeyboardPage.SYMBOLS_2))
        assertTrue(KeyAction.SHOW_LETTERS in actions(KeyboardPage.SYMBOLS_1))
        assertTrue(KeyAction.SHOW_LETTERS in actions(KeyboardPage.SYMBOLS_2))
    }

    private val codes = listOf("en", "fr", "es", "de", "it", "nl", "zh", "ja")

    private fun withoutExtras(language: String, page: KeyboardPage) =
        KeyboardLayouts.rows(page, language).let { if (KeyboardLayouts.hasExtras(language)) it.drop(1) else it }

    private fun letterRows(language: String) = withoutExtras(language, KeyboardPage.LETTERS).take(3)
        .map { row -> row.filterIsInstance<KeySpec.Character>().joinToString("") { it.text } }

    @Test
    fun everyLanguageHasFullRowsOnEveryPage() {
        for (language in codes) for (page in KeyboardPage.entries) {
            val rows = KeyboardLayouts.rows(page, language)
            assertEquals("$language $page rows", if (KeyboardLayouts.hasExtras(language)) 5 else 4, rows.size)
            rows.forEachIndexed { index, row ->
                assertEquals("$language $page row $index", 10f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
            }
        }
    }

    @Test
    fun frenchIsAzerty_germanIsQwertz_spanishHasNTilde() {
        assertEquals(listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"), letterRows("fr"))
        assertEquals(listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß"), letterRows("de"))
        assertEquals(listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"), letterRows("es"))
    }

    @Test
    fun englishItalianDutchKeepQwerty() {
        for (language in listOf("en", "it", "nl", "unknown")) {
            assertEquals(language, listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), letterRows(language))
        }
    }

    @Test
    fun digitsAreTheSameInEveryLanguage() {
        for (language in codes) {
            val first = withoutExtras(language, KeyboardPage.SYMBOLS_1).first().filterIsInstance<KeySpec.Character>().joinToString("") { it.text }
            assertEquals(language, "1234567890", first)
        }
    }

    @Test
    fun languagePunctuationIsOnTheSecondSymbolPage() {
        fun page2(language: String) = KeyboardLayouts.rows(KeyboardPage.SYMBOLS_2, language).flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("¿" in page2("es") && "¡" in page2("es"))
        assertTrue("«" in page2("fr") && "»" in page2("fr"))
        assertTrue("„" in page2("de") && "“" in page2("de"))
        assertTrue("¿" !in page2("en") && "«" !in page2("en"))
    }

    @Test
    fun accentedFormsAreOnLongPress_andFollowTheLanguage() {
        fun forms(language: String, letter: String) = KeyboardLayouts.rows(KeyboardPage.LETTERS, language).flatten()
            .filterIsInstance<KeySpec.Character>().first { it.text == letter }.alternates
        assertEquals(listOf("é", "è", "ê", "ë"), forms("fr", "e"))
        assertEquals(listOf("ç"), forms("fr", "c"))
        assertEquals(listOf("á"), forms("es", "a"))
        assertEquals(listOf("è", "é"), forms("it", "e"))
        assertTrue(forms("en", "e").isEmpty())
    }

    @Test
    fun capitalSharpSIsUsedInsteadOfDoubleS() {
        assertEquals("\u1E9E", KeySpec.Character.capital("ß"))
        assertEquals("Ñ", KeySpec.Character.capital("ñ"))
        assertEquals("É", KeySpec.Character.capital("é"))
        val shifted = KeyboardState(shift = ShiftState.ONCE)
        assertEquals("\u1E9E", KeySpec.Character("ß").typed(shifted))
    }

    @Test
    fun currencySignFollowsTheLanguage() {
        fun page1(language: String) = KeyboardLayouts.rows(KeyboardPage.SYMBOLS_1, language).flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("$" in page1("en") && "€" !in page1("en"))
        assertTrue("€" in page1("fr") && "€" in page1("de") && "$" !in page1("es"))
        assertTrue("¥" in page1("ja") && "¥" in page1("zh"))
    }

    @Test
    fun japaneseIsATwelveKeyKanaLayoutWhoseKeysOfferTheRestOfTheRow() {
        val rows = withoutExtras("ja", KeyboardPage.LETTERS)
        val first = rows.flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertEquals(listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "、", "。"), first)
        fun forms(key: String) = rows.flatten().filterIsInstance<KeySpec.Character>().first { it.text == key }.alternates
        assertEquals(listOf("か", "き", "く", "け", "こ", "が", "ぎ", "ぐ", "げ", "ご"), forms("か"))
        assertTrue("っ" in forms("た") && "ぱ" in forms("は") && "ゃ" in forms("や") && "ん" in forms("わ"))
        assertTrue("shift is not offered", rows.flatten().none { it is KeySpec.Function && it.action == KeyAction.SHIFT })
    }

    @Test
    fun japanesePunctuationBrackets_areOnTheSecondSymbolPage() {
        val page2 = KeyboardLayouts.rows(KeyboardPage.SYMBOLS_2, "ja").flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("「" in page2 && "」" in page2)
    }

    @Test
    fun chineseHasFullWidthPunctuationAndKeepsPinyinLetters() {
        val letters = KeyboardLayouts.rows(KeyboardPage.LETTERS, "zh").flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("，" in letters && "。" in letters && "," !in letters)
        val page1 = KeyboardLayouts.rows(KeyboardPage.SYMBOLS_1, "zh").flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        for (mark in listOf("、", "“", "”", "：", "；", "！", "？")) assertTrue(mark, mark in page1)
        val page2 = KeyboardLayouts.rows(KeyboardPage.SYMBOLS_2, "zh").flatten().filterIsInstance<KeySpec.Character>().map { it.text }
        assertTrue("《" in page2 && "》" in page2)
    }

    @Test
    fun everyLanguageExceptEnglishHasARowOfItsOwnCharacters_onEveryPage() {
        assertTrue(!KeyboardLayouts.hasExtras("en"))
        for (language in codes - "en") {
            assertTrue(language, KeyboardLayouts.hasExtras(language, KeyboardStyle.PINYIN_26))
            for (page in KeyboardPage.entries) {
                val first = KeyboardLayouts.rows(page, language, KeyboardStyle.PINYIN_26).first().filterIsInstance<KeySpec.Character>()
                assertTrue("$language $page has keys", first.isNotEmpty())
                assertTrue("$language $page is not all ASCII", first.any { it.text.any { c -> c.code > 127 } })
            }
        }
    }

    @Test
    fun theExtrasRowHoldsEachLanguagesOwnCharacters() {
        fun extras(language: String) = KeyboardLayouts.rows(KeyboardPage.LETTERS, language, KeyboardStyle.PINYIN_26).first().filterIsInstance<KeySpec.Character>().joinToString("") { it.text }
        assertTrue("é" in extras("fr") && "ç" in extras("fr") && "œ" in extras("fr"))
        assertTrue("ñ" in extras("es") && "¿" in extras("es") && "¡" in extras("es"))
        assertTrue("€" in extras("de") && "„" in extras("de") && "“" in extras("de"))
        assertTrue("à" in extras("it") && "ì" in extras("it") && "ù" in extras("it"))
        assertTrue("ë" in extras("nl") && "ï" in extras("nl"))
        assertTrue("，" in extras("zh") && "。" in extras("zh") && "？" in extras("zh") && "、" in extras("zh"))
        assertTrue("「" in extras("ja") && "」" in extras("ja") && "ー" in extras("ja"))
    }

    @Test
    fun japaneseRomajiIsQwertyWithJapanesePunctuation_andKanaKeysFlick() {
        val romaji = KeyboardLayouts.rows(KeyboardPage.LETTERS, "ja", KeyboardStyle.ROMAJI).drop(1) // after the extras row
        assertEquals("qwertyuiop", romaji[0].filterIsInstance<KeySpec.Character>().joinToString("") { it.text })
        val bottom = romaji.last().filterIsInstance<KeySpec.Character>().map { it.text }
        assertEquals(listOf("、", "。"), bottom)
        val kana = KeyboardLayouts.rows(KeyboardPage.LETTERS, "ja", KeyboardStyle.KANA).flatten().filterIsInstance<KeySpec.Character>()
        assertEquals(listOf("い", "う", "え", "お"), kana.first { it.text == "あ" }.flick)
        assertEquals(listOf("き", "く", "け", "こ"), kana.first { it.text == "か" }.flick)
        assertEquals(listOf("（", "ゆ", "）", "よ"), kana.first { it.text == "や" }.flick)
        assertTrue(kana.filter { it.flick.isNotEmpty() }.all { it.flick.size == 4 })
    }

    @Test
    fun chineseDefaultsToStrokeKeys_whoseKeysAreTheStrokes() {
        val rows = KeyboardLayouts.rows(KeyboardPage.LETTERS, "zh")
        val keys = rows.flatten().filterIsInstance<KeySpec.Character>()
        val strokes = keys.filter { it.label != null }
        assertEquals(listOf("⼀", "⼁", "⼃", "⼂", "⼄"), strokes.map { it.label })
        assertEquals("the engine is sent h s p n z", listOf("h", "s", "p", "n", "z"), strokes.map { it.text })
        assertTrue("no Latin letters a-z on the keys", keys.none { it.label == null && it.text.length == 1 && it.text[0] in 'a'..'z' })
        assertTrue(!KeyboardLayouts.hasExtras("zh"))
    }

    @Test
    fun zhuyinKeysShowBopomofoAndTypeTheStandardKeyPositions() {
        val rows = KeyboardLayouts.rows(KeyboardPage.LETTERS, "zh", KeyboardStyle.ZHUYIN)
        assertEquals("four rows of symbols and the bottom row", 5, rows.size)
        val keys = rows.flatten().filterIsInstance<KeySpec.Character>().filter { it.label != null }
        assertEquals("all 37 bopomofo symbols; the tone marks are left out", 37, keys.size)
        fun typed(symbol: String) = keys.first { it.label == symbol }.text
        assertEquals("1", typed("ㄅ"))
        assertEquals("q", typed("ㄆ"))
        assertEquals("a", typed("ㄇ"))
        assertEquals("z", typed("ㄈ"))
        assertEquals("s", typed("ㄋ"))
        assertEquals("u", typed("ㄧ"))
        assertEquals("all bopomofo symbols are Chinese, none is a Latin letter", true, keys.all { it.label!!.single().code > 0x3100 })
    }

    @Test
    fun everyChineseAndJapaneseStyleFillsEveryRowExactly() {
        for (language in listOf("zh", "ja")) for (style in KeyboardStyle.forLanguage(language)) for (page in KeyboardPage.entries) {
            KeyboardLayouts.rows(page, language, style).forEachIndexed { index, row ->
                assertEquals("$language $style $page row $index", 10f, row.sumOf { it.weight.toDouble() }.toFloat(), 0.001f)
            }
        }
    }

    @Test
    fun everyKeyboardHasExactlyOneHandwritingKey_onEveryPage() {
        for (language in codes) {
            val styles: List<KeyboardStyle?> = KeyboardStyle.forLanguage(language).ifEmpty { listOf(null) }
            for (style in styles) for (page in KeyboardPage.entries) {
                val count = KeyboardLayouts.rows(page, language, style).flatten().count { it is KeySpec.Function && it.action == KeyAction.HANDWRITING }
                assertEquals("$language $style $page", 1, count)
            }
        }
    }
}
