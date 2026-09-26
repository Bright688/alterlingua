package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarControllerTest {

    private val saved = mutableListOf<Language>()
    private val events = mutableListOf<ToolbarEvent>()
    private val toolbar = ToolbarController({ saved += it }, { events += it })

    private fun settings(native: Language = Languages.English, target: Language = Languages.French) =
        UserSettings(nativeLanguage = native, targetLanguage = target)

    // ---- the label follows the saved target ----

    @Test
    fun beforeTheSettingsAreRead_thereIsNoLanguageAndTheListCannotOpen() {
        assertEquals("AUTO → …", toolbar.state.label)
        toolbar.openLanguages()
        assertEquals(ToolbarPanel.KEYS, toolbar.state.panel)
    }

    @Test
    fun theLabelShowsAutoAndTheTargetCode_forEveryLanguage() {
        val expected = mapOf("en" to "EN", "fr" to "FR", "es" to "ES", "de" to "DE", "it" to "IT", "nl" to "NL", "zh" to "ZH", "ja" to "JA")
        for (language in Languages.supported) {
            val native = if (language == Languages.English) Languages.French else Languages.English
            toolbar.onSettingsChanged(settings(native = native, target = language))
            assertEquals("AUTO → ${expected.getValue(language.code)}", toolbar.state.label)
        }
    }

    @Test
    fun theLabelFollowsChangesToTheSavedSettings() {
        toolbar.onSettingsChanged(settings(target = Languages.Spanish))
        assertEquals("AUTO → ES", toolbar.state.label)
        toolbar.onSettingsChanged(settings(target = Languages.Japanese))
        assertEquals("AUTO → JA", toolbar.state.label)
    }

    // ---- the language list ----

    @Test
    fun theListOffersEveryLanguageExceptTheUsersOwn_inTheCatalogueOrder() {
        toolbar.onSettingsChanged(settings(native = Languages.English, target = Languages.French))
        assertEquals(
            listOf("Français", "Español", "Deutsch", "Italiano", "Nederlands", "中文", "日本語"),
            toolbar.state.selectable.map { it.displayName },
        )
    }

    @Test
    fun aFrenchSpeakerCanChooseEnglish() {
        toolbar.onSettingsChanged(settings(native = Languages.French, target = Languages.Spanish))
        val names = toolbar.state.selectable.map { it.displayName }
        assertTrue("English" in names)
        assertFalse("Français" in names)
        assertEquals(7, names.size)
    }

    @Test
    fun openingAndClosingTheList() {
        toolbar.onSettingsChanged(settings())
        toolbar.openLanguages()
        assertEquals(ToolbarPanel.LANGUAGES, toolbar.state.panel)
        toolbar.closeLanguages()
        assertEquals(ToolbarPanel.KEYS, toolbar.state.panel)
    }

    @Test
    fun choosingALanguage_savesIt_closesTheList_andUpdatesTheLabel() {
        toolbar.onSettingsChanged(settings(target = Languages.French))
        toolbar.openLanguages()
        toolbar.selectLanguage(Languages.Spanish)
        assertEquals(listOf(Languages.Spanish), saved)
        assertEquals(ToolbarPanel.KEYS, toolbar.state.panel)
        assertEquals("AUTO → ES", toolbar.state.label)
    }

    @Test
    fun choosingTheCurrentLanguageAgain_savesNothing() {
        toolbar.onSettingsChanged(settings(target = Languages.German))
        toolbar.openLanguages()
        toolbar.selectLanguage(Languages.German)
        assertTrue(saved.isEmpty())
        assertEquals(ToolbarPanel.KEYS, toolbar.state.panel)
    }

    @Test
    fun theUsersOwnLanguageCannotBeChosenAsTheTarget() {
        toolbar.onSettingsChanged(settings(native = Languages.English, target = Languages.French))
        toolbar.selectLanguage(Languages.English)
        assertTrue(saved.isEmpty())
        assertEquals("AUTO → FR", toolbar.state.label)
    }

    @Test
    fun switchingThroughSeveralLanguages_savesEachOne() {
        toolbar.onSettingsChanged(settings())
        val order = listOf(Languages.Spanish, Languages.German, Languages.Italian, Languages.Dutch, Languages.Chinese, Languages.Japanese, Languages.French)
        for (language in order) {
            toolbar.openLanguages()
            toolbar.selectLanguage(language)
            assertEquals("AUTO → ${language.code.uppercase()}", toolbar.state.label)
        }
        assertEquals(order, saved)
    }

    // ---- the other buttons ----

    @Test
    fun translateAndTheMicrophoneAskTheKeyboardToDoTheirJob_andChangeNothingElse() {
        toolbar.onSettingsChanged(settings(target = Languages.Italian))
        val before = toolbar.state
        toolbar.onTranslate()
        toolbar.onMicrophone()
        assertEquals(listOf(ToolbarEvent.Translate, ToolbarEvent.Voice), events)
        assertEquals(before, toolbar.state) // neither button changes the chosen language
        assertTrue(saved.isEmpty())
    }

    @Test
    fun theVoiceNoteButtonAsksToCaptureOne_andChangesNothingElse() {
        toolbar.onSettingsChanged(settings(target = Languages.Italian))
        val before = toolbar.state
        toolbar.onVoiceNote()
        assertEquals(listOf<ToolbarEvent>(ToolbarEvent.VoiceNote), events)
        assertEquals(before, toolbar.state)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun settingsAsksToOpenTheApp() {
        toolbar.onSettings()
        assertEquals(listOf<ToolbarEvent>(ToolbarEvent.OpenSettings), events)
    }

    @Test
    fun stateChangesAreReported() {
        val seen = mutableListOf<ToolbarState>()
        toolbar.onStateChanged = { seen += it }
        toolbar.onSettingsChanged(settings(target = Languages.Dutch))
        toolbar.openLanguages()
        toolbar.openLanguages() // no change, not reported again
        assertEquals(2, seen.size)
        assertEquals(ToolbarPanel.LANGUAGES, seen.last().panel)
    }
}
