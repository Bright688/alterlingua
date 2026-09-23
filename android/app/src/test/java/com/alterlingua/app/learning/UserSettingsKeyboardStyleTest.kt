package com.alterlingua.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSettingsKeyboardStyleTest {
    @Test
    fun languagesWithOneWayOfTyping_haveNoStyle() {
        val settings = UserSettings()
        for (code in listOf("en", "fr", "es", "de", "it", "nl")) assertNull(code, settings.keyboardStyleFor(code))
    }

    @Test
    fun chineseDefaultsToStrokeKeys_andJapaneseToKana() {
        val settings = UserSettings()
        assertEquals(KeyboardStyle.STROKE, settings.keyboardStyleFor("zh"))
        assertEquals(KeyboardStyle.KANA, settings.keyboardStyleFor("ja"))
    }

    @Test
    fun chineseOffersStrokesZhuyinAndPinyin_japaneseKanaAndRomaji() {
        assertEquals(listOf(KeyboardStyle.STROKE, KeyboardStyle.ZHUYIN, KeyboardStyle.PINYIN_26), KeyboardStyle.forLanguage("zh"))
        assertEquals(listOf(KeyboardStyle.KANA, KeyboardStyle.ROMAJI), KeyboardStyle.forLanguage("ja"))
    }

    @Test
    fun aSavedStyleIsUsedOnlyForItsOwnLanguage() {
        val settings = UserSettings(keyboardStyles = mapOf("zh" to KeyboardStyle.ZHUYIN, "ja" to KeyboardStyle.ZHUYIN))
        assertEquals(KeyboardStyle.ZHUYIN, settings.keyboardStyleFor("zh"))
        assertEquals("a style from another language is ignored", KeyboardStyle.KANA, settings.keyboardStyleFor("ja"))
    }
}
