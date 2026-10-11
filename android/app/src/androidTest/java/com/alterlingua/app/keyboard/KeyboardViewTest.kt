package com.alterlingua.app.keyboard

import android.view.View
import com.alterlingua.app.keyboard.TranslationUiState
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.learning.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on a device or emulator: builds the keyboard panel and presses keys on it. Needs no other app. */
class KeyboardViewTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun keys(root: View): List<KeyCapView> = buildList {
        if (root is KeyCapView) add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(keys(root.getChildAt(i)))
    }

    private fun AlterLinguaKeyboardView.key(label: String) = keys(this).firstOrNull { it.label == label }

    @Test
    fun lettersPage_hasAllLetters_underTheToolbar() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.render(KeyboardState())
        assertEquals(3, view.childCount) // the toolbar, the (hidden) candidate strip, and the keys
        for (letter in "qwertyuiopasdfghjklzxcvbnm") assertNotNull("missing $letter", view.key(letter.toString()))
    }

    @Test
    fun pressingAKey_reportsIt() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val pressed = mutableListOf<KeySpec>()
        view.onKey = { pressed += it }
        view.render(KeyboardState())
        view.key("q")!!.performClick()
        assertEquals(listOf<KeySpec>(KeySpec.Character("q")), pressed)
    }

    @Test
    fun shift_showsCapitals_andSymbolsPageShowsDigits() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.render(KeyboardState(shift = ShiftState.ONCE))
        assertNotNull(view.key("Q"))
        view.render(KeyboardState(page = KeyboardPage.SYMBOLS_1))
        assertNotNull(view.key("1"))
        assertTrue(view.key("q") == null)
        assertNotNull(view.key("ABC"))
    }

    // ---- toolbar ----

    private fun toolbarButtons(root: View): List<ToolbarButtonView> = buildList {
        if (root is ToolbarButtonView) add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(toolbarButtons(root.getChildAt(i)))
    }

    private fun AlterLinguaKeyboardView.chip(label: String) = toolbarButtons(this).firstOrNull { it.label == label }

    @Test
    fun theToolbarShowsTheTargetLanguage_forSeveralLanguages() = onMain {
        val view = AlterLinguaKeyboardView(context)
        for ((language, label) in listOf(
            Languages.Spanish to "AUTO \u2192 ES",
            Languages.German to "AUTO \u2192 DE",
            Languages.Chinese to "AUTO \u2192 ZH",
            Languages.Japanese to "AUTO \u2192 JA",
        )) {
            view.renderToolbar(ToolbarState(target = language, native = Languages.English))
            assertNotNull("missing $label", view.chip(label))
        }
    }

    @Test
    fun theLanguageList_showsNativeNames_andChoosingOneIsReported() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.render(KeyboardState())
        view.renderToolbar(ToolbarState(target = Languages.French, native = Languages.English, panel = ToolbarPanel.LANGUAGES))

        val names = descriptions(view)
        for (name in listOf("Fran\u00E7ais", "Espa\u00F1ol", "Deutsch", "Italiano", "Nederlands", "\u4E2D\u6587", "\u65E5\u672C\u8A9E")) {
            assertTrue("missing $name in $names", names.any { it.startsWith(name) })
        }
        assertTrue("English is the user's own language", names.none { it == "English" })

        allViews(view).first { it.contentDescription?.startsWith("Espa\u00F1ol") == true }.performClick()
        assertEquals(listOf<ToolbarAction>(ToolbarAction.SelectLanguage(Languages.Spanish)), actions)
    }

    @Test
    fun translateAndMicrophoneAreShown_andReported() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.renderToolbar(ToolbarState(target = Languages.French, native = Languages.English))
        view.chip("Translate")!!.performClick()
        toolbarButtons(view).first { it.contentDescription == "Microphone" }.performClick()
        toolbarButtons(view).first { it.contentDescription == "AlterLingua settings" }.performClick()
        assertEquals(listOf(ToolbarAction.Translate, ToolbarAction.Microphone, ToolbarAction.OpenSettings), actions)
    }

    @Test
    fun theKeysStillWork_whileTheLanguageListIsClosed() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val pressed = mutableListOf<KeySpec>()
        view.onKey = { pressed += it }
        view.render(KeyboardState())
        view.renderToolbar(ToolbarState(target = Languages.French, native = Languages.English))
        view.key("a")!!.performClick()
        assertEquals(listOf<KeySpec>(KeySpec.Character("a")), pressed)
    }

    private fun allViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(allViews(root.getChildAt(i)))
    }

    private fun descriptions(root: View) = allViews(root).mapNotNull { it.contentDescription?.toString() }

    // ---- translation status ----

    private fun texts(root: View): List<String> = allViews(root).mapNotNull { (it as? android.widget.TextView)?.text?.toString() }

    @Test
    fun translatedShowsUndo_andPressingItIsReported() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.renderTranslation(TranslationUiState.Translated(Languages.Spanish))
        assertTrue(texts(view).contains("Translated"))
        toolbarButtons(view).first { it.label == "Undo" }.performClick()
        assertEquals(listOf<ToolbarAction>(ToolbarAction.Undo), actions)
    }

    @Test
    fun translatingNamesTheTarget_inItsOwnLanguage() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.renderTranslation(TranslationUiState.Translating(Languages.Japanese))
        assertTrue(texts(view).any { it.contains("\u65E5\u672C\u8A9E") })
        view.renderTranslation(TranslationUiState.Translating(Languages.French))
        assertTrue(texts(view).any { it.contains("Fran\u00E7ais") })
    }

    @Test
    fun aFailureShowsItsMessageAndRetry_andIdleBringsTheToolbarBack() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.renderToolbar(ToolbarState(target = Languages.French, native = Languages.English))
        view.renderTranslation(TranslationUiState.Failed(com.alterlingua.app.translation.TranslationFailure.OFFLINE, canRetry = true))
        assertTrue(texts(view).any { it.contains("offline") })
        toolbarButtons(view).first { it.label == "Retry" }.performClick()
        assertEquals(listOf<ToolbarAction>(ToolbarAction.Retry), actions)

        view.renderTranslation(TranslationUiState.Idle)
        assertNotNull(view.chip("AUTO \u2192 FR"))
    }

    // ---- voice panel ----

    @Test
    fun recordingShowsTheSpokenLanguage_theTimer_andCancelAndStop() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.renderVoice(VoiceUiState.Recording(Languages.English, 7_000, List(20) { 0.5f }))
        assertTrue(texts(view).contains("Speak in English"))
        assertTrue(texts(view).contains("00:07"))
        toolbarButtons(view).first { it.label == "Stop" }.performClick()
        toolbarButtons(view).first { it.label == "Cancel" }.performClick()
        assertEquals(listOf(ToolbarAction.Voice(VoiceAction.STOP), ToolbarAction.Voice(VoiceAction.CANCEL)), actions)
    }

    @Test
    fun theSpokenLanguageNameFollowsTheUsersLanguage() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.renderVoice(VoiceUiState.Recording(Languages.French, 0, emptyList()))
        assertTrue(texts(view).contains("Speak in Fran\u00E7ais"))
    }

    @Test
    fun waitingShowsUnderstanding_thenTranslatingToTheSelectedLanguage() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.renderVoice(VoiceUiState.Understanding(Languages.Spanish))
        assertTrue(texts(view).contains("Understanding your message\u2026"))
        for ((language, name) in listOf(Languages.Spanish to "Espa\u00F1ol", Languages.French to "Fran\u00E7ais", Languages.Japanese to "\u65E5\u672C\u8A9E")) {
            view.renderVoice(VoiceUiState.Translating(language))
            assertTrue(texts(view).contains("Translating to $name\u2026"))
        }
    }

    @Test
    fun theResultShowsBothLanguagesAndTheActions() = onMain {
        val view = AlterLinguaKeyboardView(context)
        val actions = mutableListOf<ToolbarAction>()
        view.onToolbarAction = { actions += it }
        view.renderVoice(VoiceUiState.Result(Languages.English, "Are you coming tomorrow?", Languages.Japanese, "\u660E\u65E5\u6765\u307E\u3059\u304B\uFF1F"))
        val shown = texts(view)
        assertTrue(shown.contains("Original \u2014 English"))
        assertTrue(shown.contains("Translated \u2014 \u65E5\u672C\u8A9E"))
        for (label in listOf("Insert as text", "Edit", "Listen", "Record again")) {
            toolbarButtons(view).first { it.label == label }.performClick()
        }
        assertEquals(
            listOf(VoiceAction.INSERT, VoiceAction.EDIT, VoiceAction.LISTEN, VoiceAction.RECORD_AGAIN).map { ToolbarAction.Voice(it) },
            actions,
        )
        assertTrue("Share voice is not offered yet", toolbarButtons(view).none { it.label?.contains("Share", ignoreCase = true) == true })
    }

    @Test
    fun aFailureShowsItsMessage_andPermissionShowsAnExplanationBeforeTheButton() = onMain {
        val view = AlterLinguaKeyboardView(context)
        view.renderVoice(VoiceUiState.NeedsPermission)
        assertTrue(texts(view).any { it.contains("needs the microphone") })
        assertNotNull(toolbarButtons(view).firstOrNull { it.label == "Allow microphone" })

        view.renderVoice(VoiceUiState.Failed(com.alterlingua.app.translation.VoiceFailure.NO_SPEECH, canRetry = false))
        assertTrue(texts(view).any { it.contains("couldn't hear anything") })
        view.renderVoice(VoiceUiState.Idle)
        view.renderToolbar(ToolbarState(target = Languages.French, native = Languages.English))
        assertNotNull(view.chip("AUTO \u2192 FR"))
    }
}
