package com.alterlingua.app.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses a real DataStore file in a temporary folder, so it checks the actual saving and loading. */
class DataStoreUserSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.newStore(): DataStore<Preferences> = newStore2("settings")

    private fun TestScope.newStore2(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { folder.newFile("$name.preferences_pb") },
        )

    @Test
    fun eachLanguageKeepsItsOwnLevel_afterSwitchingTargets() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        repo.update { UserSettings(targetLanguage = Languages.French, level = LanguageLevel.INTERMEDIATE) }
        repo.update { it.withTargetLanguage(Languages.Spanish).copy(level = LanguageLevel.SOME_BASICS) }

        val spanish = repo.settings.first()
        assertEquals("es", spanish.targetLanguage.code)
        assertEquals(LanguageLevel.SOME_BASICS, spanish.level)
        assertEquals(LanguageLevel.INTERMEDIATE, spanish.otherLevels["fr"])

        repo.update { it.withTargetLanguage(Languages.French) }
        val french = repo.settings.first()
        assertEquals(LanguageLevel.INTERMEDIATE, french.level)
        assertEquals(LanguageLevel.SOME_BASICS, french.otherLevels["es"])
    }

    @Test
    fun aLevelSavedByAnEarlierVersion_isStillReadForTheTarget() = runTest {
        val store = newStore()
        store.edit {
            it[SettingsKeys.TargetLanguage] = "de"
            it[SettingsKeys.Level] = LanguageLevel.INTERMEDIATE.name
        }
        assertEquals(LanguageLevel.INTERMEDIATE, DataStoreUserSettingsRepository(store).settings.first().level)
    }

    @Test
    fun emptyStore_givesDefaults() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        assertEquals(UserSettings(), repo.settings.first())
    }

    @Test
    fun everySavedAnswer_isReadBack() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        val chosen = UserSettings(
            onboardingCompleted = true,
            welcomeSeen = true,
            nativeLanguage = Languages.French,
            targetLanguage = Languages.English,
            appLanguage = Languages.Japanese,
            appLanguageChosen = true,
            detectSourceAutomatically = false,
            autoTranslateEnabled = true,
            purpose = LearningPurpose.TRAVEL,
            level = LanguageLevel.INTERMEDIATE,
            assistanceMode = AssistanceMode.ADAPTIVE,
            dailyReminderEnabled = false,
            reminderTime = LocalTime.of(7, 45),
            microphonePermissionAsked = true,
            incomingTranslationEnabled = false,
            floatingTranslationEnabled = true,
            liveChatTranslationEnabled = true,
            liveChatTranslationConsentGiven = true,
            learningFromMessagesEnabled = false,
        )

        repo.update { chosen }

        assertEquals(chosen, repo.settings.first())
    }

    @Test
    fun everySupportedLanguage_canBeSavedAndReadBack() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        Languages.supported.forEach { target ->
            repo.update { it.withTargetLanguage(target) }
            val saved = repo.settings.first()
            assertEquals(target, saved.targetLanguage)
            assertNotEquals(saved.nativeLanguage, saved.targetLanguage)
        }
        repo.update { it.copy(nativeLanguage = Languages.Japanese, targetLanguage = Languages.Chinese) }
        val saved = repo.settings.first()
        assertEquals(Languages.Japanese, saved.nativeLanguage)
        assertEquals(Languages.Chinese, saved.targetLanguage)
    }

    @Test
    fun languagesAreSavedByTheirBaseCode_notByLocaleOrScript() = runTest {
        val store = newStore()
        val repo = DataStoreUserSettingsRepository(store)
        repo.update { it.copy(nativeLanguage = Languages.Japanese, targetLanguage = Languages.Chinese) }

        val raw = store.data.first()
        assertEquals("zh", raw[SettingsKeys.TargetLanguage])
        assertEquals("ja", raw[SettingsKeys.NativeLanguage])
    }

    @Test
    fun aLocaleOrScriptTagSavedEarlier_stillLoadsTheRightLanguage() = runTest {
        listOf("zh", "zh-Hans", "zh-CN").forEach { saved ->
            val store = newStore2(saved)
            store.edit { it[SettingsKeys.TargetLanguage] = saved }
            assertEquals(saved, Languages.Chinese, DataStoreUserSettingsRepository(store).settings.first().targetLanguage)
        }
    }

    @Test
    fun update_receivesTheLatestSavedValue() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        repo.update { it.copy(purpose = LearningPurpose.STUDY) }
        repo.update { it.copy(level = LanguageLevel.SOME_BASICS) }

        val saved = repo.settings.first()
        assertEquals(LearningPurpose.STUDY, saved.purpose)
        assertEquals(LanguageLevel.SOME_BASICS, saved.level)
    }

    @Test
    fun unrecognisedOrDamagedValues_fallBackToDefaults() = runTest {
        val store = newStore()
        store.edit {
            it[SettingsKeys.AssistanceMode] = "NOT_A_MODE"
            it[SettingsKeys.Purpose] = "???"
            it[SettingsKeys.NativeLanguage] = "zz"
            it[SettingsKeys.ReminderMinuteOfDay] = 99_999
        }
        val saved = DataStoreUserSettingsRepository(store).settings.first()

        val defaults = UserSettings()
        assertEquals(defaults.assistanceMode, saved.assistanceMode)
        assertEquals(defaults.purpose, saved.purpose)
        assertEquals(defaults.nativeLanguage, saved.nativeLanguage)
        assertEquals(defaults.reminderTime, saved.reminderTime)
    }

    @Test
    fun reminderTime_keepsHoursAndMinutes_includingMidnight() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        listOf(LocalTime.MIDNIGHT, LocalTime.of(23, 59), LocalTime.of(8, 30)).forEach { time ->
            repo.update { it.copy(reminderTime = time) }
            assertEquals(time, repo.settings.first().reminderTime)
        }
    }

    @Test
    fun onboardingFlag_startsFalse_andIsSaved() = runTest {
        val repo = DataStoreUserSettingsRepository(newStore())
        assertTrue(!repo.settings.first().onboardingCompleted)
        repo.update { it.copy(onboardingCompleted = true) }
        assertTrue(repo.settings.first().onboardingCompleted)
    }
}
