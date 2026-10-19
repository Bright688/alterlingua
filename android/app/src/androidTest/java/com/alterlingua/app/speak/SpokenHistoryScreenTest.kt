package com.alterlingua.app.speak

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device or emulator. */
class SpokenHistoryScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val oneEntry = SpokenHistoryEntry(
        id = 1, createdAtMillis = 0L, sourceLanguage = Languages.English, targetLanguage = Languages.French,
        transcript = "Are you coming tomorrow?", translation = "Tu viens demain ?",
    )

    private fun show(state: SpokenHistoryUiState, onDelete: (Long) -> Unit = {}, onClearAll: () -> Unit = {}, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            AlterLinguaTheme { SpokenHistoryScreen(state, onDelete, onClearAll, onBack) }
        }
    }

    @Test fun emptyState_showsTheEmptyMessage_notTheList() {
        show(SpokenHistoryUiState(loading = false, entries = emptyList()))
        composeTestRule.onNodeWithTag("sph_empty").assertIsDisplayed()
    }

    @Test fun anEntry_showsItsTranscriptAndTranslation() {
        show(SpokenHistoryUiState(loading = false, entries = listOf(oneEntry)))
        composeTestRule.onNodeWithTag("sph_transcript_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sph_translation_1").assertIsDisplayed()
    }

    @Test fun deletingAnEntry_reportsItsId() {
        var deleted: Long? = null
        show(SpokenHistoryUiState(loading = false, entries = listOf(oneEntry)), onDelete = { deleted = it })
        composeTestRule.onNodeWithTag("sph_delete_1").performClick()
        assertEquals(1L, deleted)
    }

    @Test fun clearAll_asksForConfirmationFirst() {
        var cleared = 0
        show(SpokenHistoryUiState(loading = false, entries = listOf(oneEntry)), onClearAll = { cleared++ })
        composeTestRule.onNodeWithTag("sph_clear_all").performClick()
        assertEquals("not cleared until confirmed", 0, cleared)
        composeTestRule.onNodeWithTag("sph_clear_confirm").performClick()
        assertEquals(1, cleared)
    }

    @Test fun clearAll_cancellingTheConfirmation_doesNothing() {
        var cleared = 0
        show(SpokenHistoryUiState(loading = false, entries = listOf(oneEntry)), onClearAll = { cleared++ })
        composeTestRule.onNodeWithTag("sph_clear_all").performClick()
        composeTestRule.onNodeWithTag("sph_clear_cancel").performClick()
        assertEquals(0, cleared)
    }

    @Test fun back_isReported() {
        var back = 0
        show(SpokenHistoryUiState(loading = false, entries = emptyList()), onBack = { back++ })
        composeTestRule.onNodeWithTag("sph_back").performClick()
        assertEquals(1, back)
    }
}
