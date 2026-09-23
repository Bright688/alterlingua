package com.alterlingua.app.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.learning.pronunciation.PracticePhase
import com.alterlingua.app.learning.pronunciation.PracticeState
import com.alterlingua.app.learning.pronunciation.PronunciationFeedback
import com.alterlingua.app.learning.pronunciation.PronunciationVerdict
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class PracticePanelTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: PracticeState, onListen: () -> Unit = {}, onRepeat: () -> Unit = {}, onStop: () -> Unit = {}) {
        composeTestRule.setContent { AlterLinguaTheme { PracticePanel(state, onListen, onRepeat, onStop, {}) } }
    }

    @Test fun idle_showsListenAndRepeat_asWorkingButtons() {
        var listened = 0
        var repeated = 0
        show(PracticeState("acompte", canListen = true), { listened++ }, { repeated++ })
        composeTestRule.onNodeWithTag("lesson_listen").assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag("lesson_repeat").performClick()
        assertEquals(1, listened)
        assertEquals(1, repeated)
    }

    @Test fun withoutAVoice_listenIsDisabled_andSaysWhy() {
        show(PracticeState("acompte", canListen = false))
        composeTestRule.onNodeWithTag("lesson_listen").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("lesson_listen_unavailable").assertIsDisplayed()
    }

    @Test fun recording_showsTheStopButton() {
        var stopped = 0
        show(PracticeState("acompte", canListen = true, phase = PracticePhase.Recording(800, 0.4f)), onStop = { stopped++ })
        composeTestRule.onNodeWithTag("lesson_practice_recording").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lesson_repeat").performClick()
        assertEquals(1, stopped)
    }

    @Test fun feedback_showsTheVerdict_andNoPercentage() {
        show(PracticeState("acompte", true, phase = PracticePhase.Feedback(PronunciationFeedback(PronunciationVerdict.GOOD, "acompte"), 1)))
        composeTestRule.onNodeWithTag("lesson_practice_verdict").assertIsDisplayed()
    }
}
