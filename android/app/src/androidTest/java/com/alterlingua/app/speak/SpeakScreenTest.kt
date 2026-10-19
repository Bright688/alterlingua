package com.alterlingua.app.speak

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Runs on a device or emulator. */
class SpeakScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: SpeakState,
        chosen: MutableList<Language> = mutableListOf(),
        onShare: () -> Unit = {},
        onListen: () -> Unit = {},
        onType: (String) -> Unit = {},
        onOpenHistory: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AlterLinguaTheme { SpeakScreen(state, { chosen += it }, {}, {}, onType, {}, {}, onListen, onShare, {}, {}, onOpenHistory) }
        }
    }

    @Test fun idle_offersEveryLanguageExceptTheSpokenOne_andRecord() {
        val chosen = mutableListOf<Language>()
        show(SpeakState(Languages.English, Languages.Spanish), chosen)
        composeTestRule.onNodeWithTag("speak_record").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speak_target_ja").performClick()
        assertEquals(listOf(Languages.Japanese), chosen)
    }

    @Test fun result_showsTranscriptTranslationListenAndShare_withTheNeverSendNote() {
        var listened = 0
        var shared = 0
        val result = SpokenResult("en", "Are you coming tomorrow?", Languages.Spanish, "¿Vienes mañana?", File("x.wav"), "audio/wav")
        show(SpeakState(Languages.English, Languages.Spanish, SpeakPhase.Result(result)), onShare = { shared++ }, onListen = { listened++ })
        composeTestRule.onNodeWithTag("speak_transcript").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speak_translation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speak_share_note").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speak_listen").performClick()
        composeTestRule.onNodeWithTag("speak_share").performClick()
        assertEquals(1, listened); assertEquals(1, shared)
    }

    @Test fun whileRecording_theTargetChipsAreDisabled() {
        show(SpeakState(Languages.English, Languages.Spanish, SpeakPhase.Recording(2_000, 0.3f)))
        composeTestRule.onNodeWithTag("speak_target_fr").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("speak_stop").assertIsDisplayed()
    }

    @Test fun idle_typeInsteadRevealsAFieldThatSendsTheTypedText() {
        var typed: String? = null
        show(SpeakState(Languages.English, Languages.Spanish), onType = { typed = it })
        composeTestRule.onNodeWithTag("speak_type_instead").performClick()
        composeTestRule.onNodeWithTag("speak_type_field").performTextInput("Are you coming tomorrow?")
        composeTestRule.onNodeWithTag("speak_type_send").performClick()
        assertEquals("Are you coming tomorrow?", typed)
    }

    @Test fun theHistoryButtonIsReported() {
        var opened = 0
        show(SpeakState(Languages.English, Languages.Spanish), onOpenHistory = { opened++ })
        composeTestRule.onNodeWithTag("speak_history").performClick()
        assertEquals(1, opened)
    }

    @Test fun result_editRevealsTheOriginal_andTranslateSendsTheEditedText() {
        var typed: String? = null
        val result = SpokenResult("en", "Are you coming tomorrow?", Languages.Spanish, "¿Vienes mañana?", File("x.wav"), "audio/wav")
        show(SpeakState(Languages.English, Languages.Spanish, SpeakPhase.Result(result)), onType = { typed = it })
        composeTestRule.onNodeWithTag("speak_edit").performClick()
        composeTestRule.onNodeWithTag("speak_edit_field").performTextReplacement("Are you coming tomorrow morning?")
        composeTestRule.onNodeWithTag("speak_edit_confirm").performClick()
        assertEquals("Are you coming tomorrow morning?", typed)
    }

    @Test fun result_cancellingTheEdit_leavesTheOriginalTranscriptShown() {
        val result = SpokenResult("en", "Are you coming tomorrow?", Languages.Spanish, "¿Vienes mañana?", File("x.wav"), "audio/wav")
        show(SpeakState(Languages.English, Languages.Spanish, SpeakPhase.Result(result)))
        composeTestRule.onNodeWithTag("speak_edit").performClick()
        composeTestRule.onNodeWithTag("speak_edit_field").performTextReplacement("something else")
        composeTestRule.onNodeWithTag("speak_edit_cancel").performClick()
        composeTestRule.onNodeWithTag("speak_transcript").assertIsDisplayed()
    }
}
