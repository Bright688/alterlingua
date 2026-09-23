package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceLanguageToolbarTest {
    private fun toolbar() = ToolbarController(saveTarget = {}, onEvent = {})

    @Test fun withDetectionOn_theToolbarShowsAuto() {
        val t = toolbar()
        t.onSettingsChanged(UserSettings(nativeLanguage = Languages.Spanish, targetLanguage = Languages.French, detectSourceAutomatically = true))
        assertEquals("AUTO → FR", t.state.label)
    }

    @Test fun withAFixedSource_theToolbarShowsTheSourceCode() {
        val t = toolbar()
        t.onSettingsChanged(UserSettings(nativeLanguage = Languages.Spanish, targetLanguage = Languages.French, detectSourceAutomatically = false))
        assertEquals("ES → FR", t.state.label)
    }

    @Test fun everyDirection_isShownWithItsOwnCodes() {
        for (source in Languages.supported) for (target in Languages.forLearningSelection.filter { it != source }) {
            val t = toolbar()
            t.onSettingsChanged(UserSettings(nativeLanguage = source, targetLanguage = target, detectSourceAutomatically = false))
            assertEquals("${source.code.uppercase()} → ${target.code.uppercase()}", t.state.label)
        }
    }

    @Test fun switchingDetectionOnAndOff_updatesTheLabel() {
        val t = toolbar()
        val s = UserSettings(nativeLanguage = Languages.Japanese, targetLanguage = Languages.English, detectSourceAutomatically = false)
        t.onSettingsChanged(s)
        assertEquals("JA → EN", t.state.label)
        t.onSettingsChanged(s.copy(detectSourceAutomatically = true))
        assertEquals("AUTO → EN", t.state.label)
    }
}
