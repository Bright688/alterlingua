package com.alterlingua.app.localization

import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs on a device or emulator: the app language picks the matching string resources. */
class AppLanguageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun eachAppLanguage_showsItsOwnWording() {
        val translate = mapOf("en" to "Translate", "fr" to "Traduire", "es" to "Traducir", "de" to "Übersetzen", "it" to "Traduci", "nl" to "Vertalen", "zh" to "翻译", "ja" to "翻訳")
        for (language in Languages.supported) {
            assertEquals(language.code, translate.getValue(language.code), AppLanguage.wrap(context, language).getString(R.string.toolbar_translate))
        }
    }

    @Test fun noChosenLanguage_leavesTheContextAlone() {
        assertEquals(context, AppLanguage.wrap(context, null))
    }
}
