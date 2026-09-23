package com.alterlingua.app.share

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class SharedVoiceScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val result = VoiceNoteResult(
        Languages.French, "fr", "Je viens de parler au fournisseur.", Languages.English, "I just spoke with the supplier.", false,
        listOf(UsefulUnit("fournisseur", UnitType.WORD, "supplier", MasteryStatus.LEARNING)), savedToMap = true, canListen = true,
    )

    @Test
    fun androidOffersAlterLinguaInTheShareSheet_forAnAudioItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val send = Intent(Intent.ACTION_SEND).setType("audio/ogg")
        val ours = context.packageManager.queryIntentActivities(send, 0).filter { it.activityInfo.packageName == context.packageName }
        assertTrue(ours.any { it.activityInfo.name.endsWith("ShareVoiceActivity") })
        val text = Intent(Intent.ACTION_SEND).setType("text/plain")
        assertTrue("not offered for plain text", context.packageManager.queryIntentActivities(text, 0).none { it.activityInfo.packageName == context.packageName && it.activityInfo.name.endsWith("ShareVoiceActivity") })
    }

    @Test
    fun result_showsTranscript_translation_units_andActions() {
        var listened = 0
        var reviewed = 0
        composeTestRule.setContent {
            AlterLinguaTheme { SharedVoiceScreen(SharedVoiceState.Result(result), { listened++ }, { reviewed++ }, {}, {}) }
        }
        composeTestRule.onNodeWithTag("shared_voice_transcript").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shared_voice_translation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shared_voice_unit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shared_voice_privacy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shared_voice_listen").performClick()
        composeTestRule.onNodeWithTag("shared_voice_review").performClick()
        assertEquals(1, listened)
        assertEquals(1, reviewed)
    }

    @Test
    fun result_withoutAVoice_disablesListen_andWithoutSavedUnits_disablesReview() {
        composeTestRule.setContent {
            AlterLinguaTheme { SharedVoiceScreen(SharedVoiceState.Result(result.copy(canListen = false, savedToMap = false)), {}, {}, {}, {}) }
        }
        composeTestRule.onNodeWithTag("shared_voice_listen").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("shared_voice_review").assertIsNotEnabled()
    }

    @Test
    fun working_andError_statesAreShown() {
        composeTestRule.setContent {
            AlterLinguaTheme { SharedVoiceScreen(SharedVoiceState.Failed(voice = VoiceFailure.NO_SPEECH), {}, {}, {}, {}) }
        }
        composeTestRule.onNodeWithTag("shared_voice_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shared_voice_close").assertIsDisplayed()
    }
}
