package com.alterlingua.app.keyboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.storage.DataStoreUserSettingsRepository
import com.alterlingua.app.storage.setTargetLanguage
import com.alterlingua.app.testing.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The toolbar reading and writing the target language through the saved settings, across several languages. */
class ToolbarSettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { folder.newFile("settings.preferences_pb") })

    @Test
    fun everyTargetIsSavedAndReadBack_includingChineseAndJapanese() = runTest {
        val repo = DataStoreUserSettingsRepository(store())
        repo.update { UserSettings(nativeLanguage = Languages.English, targetLanguage = Languages.French) }

        for (language in listOf(Languages.Spanish, Languages.German, Languages.Italian, Languages.Dutch, Languages.Chinese, Languages.Japanese, Languages.French)) {
            repo.setTargetLanguage(language)
            val settings = repo.settings.first()
            assertEquals(language, settings.targetLanguage)
            assertEquals(Languages.English, settings.nativeLanguage) // the user's own language never changes
        }
    }

    @Test
    fun aNewKeyboardSession_showsTheLastChosenTarget() = runTest {
        val repo = DataStoreUserSettingsRepository(store())
        repo.setTargetLanguage(Languages.Japanese)

        val session = ToolbarController({}, {})
        session.onSettingsChanged(repo.settings.first())
        assertEquals("AUTO → JA", session.state.label)
    }

    @Test
    fun choosingATargetThroughTheToolbar_writesItToTheSettings() = runTest {
        val repo = FakeUserSettingsRepository(UserSettings(nativeLanguage = Languages.English, targetLanguage = Languages.French))
        val toolbar = ToolbarController(saveTarget = { language -> kotlinx.coroutines.runBlocking { repo.setTargetLanguage(language) } }, onEvent = {})
        toolbar.onSettingsChanged(repo.current)

        toolbar.openLanguages()
        toolbar.selectLanguage(Languages.Chinese)

        assertEquals(Languages.Chinese, repo.current.targetLanguage)
        assertEquals("AUTO → ZH", toolbar.state.label)
    }

    @Test
    fun aFrenchSpeakerChoosingEnglish_keepsFrenchAsTheirOwnLanguage() = runTest {
        val repo = FakeUserSettingsRepository(UserSettings(nativeLanguage = Languages.French, targetLanguage = Languages.Spanish))
        val toolbar = ToolbarController(saveTarget = { language -> kotlinx.coroutines.runBlocking { repo.setTargetLanguage(language) } }, onEvent = {})
        toolbar.onSettingsChanged(repo.current)

        toolbar.selectLanguage(Languages.English)

        assertEquals(Languages.English, repo.current.targetLanguage)
        assertEquals(Languages.French, repo.current.nativeLanguage)
        assertEquals("AUTO → EN", toolbar.state.label)
    }

    @Test
    fun changingTheTarget_keepsTheOtherOnboardingAnswers() = runTest {
        val repo = DataStoreUserSettingsRepository(store())
        repo.update {
            UserSettings(
                onboardingCompleted = true,
                targetLanguage = Languages.French,
                level = LanguageLevel.INTERMEDIATE,
                assistanceMode = AssistanceMode.ADAPTIVE,
            )
        }
        repo.setTargetLanguage(Languages.Spanish)
        val settings = repo.settings.first()
        assertEquals(true, settings.onboardingCompleted)
        assertEquals(AssistanceMode.ADAPTIVE, settings.assistanceMode)
        assertEquals(LanguageLevel.BEGINNER, settings.level) // Español has its own level
        assertEquals(LanguageLevel.INTERMEDIATE, settings.otherLevels["fr"]) // Français keeps its own
    }
}
