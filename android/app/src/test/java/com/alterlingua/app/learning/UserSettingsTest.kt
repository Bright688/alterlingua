package com.alterlingua.app.learning

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {

    @Test
    fun defaults_areEnglishToFrench_fullSupport_eveningReminder() {
        val settings = UserSettings()
        assertEquals(Languages.English, settings.nativeLanguage)
        assertEquals(Languages.French, settings.targetLanguage)
        assertEquals(AssistanceMode.FULL_SUPPORT, settings.assistanceMode)
        assertEquals(LocalTime.of(20, 0), settings.reminderTime)
        assertEquals(false, settings.onboardingCompleted)
    }

    @Test
    fun catalogue_hasTheEightRequiredLanguages_withStandardCodes() {
        assertEquals(
            listOf("en", "fr", "es", "de", "it", "nl", "zh", "ja"),
            Languages.supported.map { it.code },
        )
    }

    @Test
    fun selectorNames_areTheLanguagesOwnNames() {
        // CLAUDE.md 6.2: native names, not "French", "Spanish" and so on.
        assertEquals(
            listOf("English", "Français", "Español", "Deutsch", "Italiano", "Nederlands", "中文", "日本語"),
            Languages.supported.map { it.displayName },
        )
        listOf("French", "Spanish", "German", "Italian", "Dutch", "Chinese", "Japanese").forEach { exonym ->
            assertFalse(exonym, Languages.supported.any { it.displayName == exonym })
        }
    }

    @Test
    fun secondaryName_isTheEnglishLabel_shownUnderTheNativeName() {
        assertEquals("Chinese", Languages.Chinese.secondaryName)
        assertEquals("Japanese", Languages.Japanese.secondaryName)
        assertEquals("French", Languages.French.secondaryName)
        assertEquals(null, Languages.English.secondaryName) // same name, nothing to add
        // The primary name is still the language's own name.
        assertEquals("中文", Languages.Chinese.displayName)
    }

    @Test
    fun codesAreUnique_andLanguageIsNotTheSameThingAsLocale() {
        val codes = Languages.supported.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        Languages.supported.forEach {
            assertTrue(it.locale.startsWith(it.code + "-"))
            assertNotEquals(it.code, it.locale)
        }
        assertEquals("zh", Languages.Chinese.code)
        assertEquals("zh-CN", Languages.Chinese.locale)
    }

    @Test
    fun writingSystems_areRecorded() {
        listOf(Languages.English, Languages.French, Languages.Spanish, Languages.German, Languages.Italian, Languages.Dutch)
            .forEach { assertEquals(WritingSystem.LATIN, it.writingSystem) }
        assertEquals(WritingSystem.CHINESE, Languages.Chinese.writingSystem)
        assertEquals(WritingSystem.JAPANESE, Languages.Japanese.writingSystem)
    }

    @Test
    fun capabilities_areSeparateFlags_andSpeechIsNotClaimedYet() {
        Languages.supported.forEach {
            assertTrue(it.translationSupported)
            assertTrue(it.learningSupported)
            // No speech provider is configured yet, so nothing claims speech support.
            assertFalse(it.speechToTextSupported)
            assertFalse(it.textToSpeechSupported)
        }
    }

    @Test
    fun selectionLists_offerAllEightLanguages() {
        assertEquals(Languages.supported, Languages.forNativeSelection)
        assertEquals(Languages.supported, Languages.forLearningSelection)
    }

    @Test
    fun fromCode_acceptsPlainCodes_locales_andScriptTags() {
        assertEquals(Languages.Chinese, Languages.fromCode("zh"))
        assertEquals(Languages.Chinese, Languages.fromCode("zh-CN"))
        assertEquals(Languages.Chinese, Languages.fromCode("zh-TW"))
        assertEquals(Languages.Chinese, Languages.fromCode("zh-Hans"))
        assertEquals(Languages.French, Languages.fromCode("fr-CI"))
        assertEquals(Languages.Japanese, Languages.fromCode("JA"))
        assertEquals(null, Languages.fromCode("xx"))
        assertEquals(null, Languages.fromCode(null))
    }

    @Test
    fun anyPairOfDifferentLanguages_canBeChosen() {
        val settings = UserSettings()
            .withNativeLanguage(Languages.Spanish)
            .withTargetLanguage(Languages.German)
        assertEquals(Languages.Spanish, settings.nativeLanguage)
        assertEquals(Languages.German, settings.targetLanguage)

        val toJapanese = UserSettings().withTargetLanguage(Languages.Japanese)
        assertEquals(Languages.English, toJapanese.nativeLanguage)
        assertEquals(Languages.Japanese, toJapanese.targetLanguage)
    }

    @Test
    fun choosingTheOtherLanguage_swapsThemBack() {
        val german = UserSettings(nativeLanguage = Languages.Spanish, targetLanguage = Languages.German)

        val nativeGerman = german.withNativeLanguage(Languages.German)
        assertEquals(Languages.German, nativeGerman.nativeLanguage)
        assertEquals(Languages.Spanish, nativeGerman.targetLanguage)

        val targetSpanish = german.withTargetLanguage(Languages.Spanish)
        assertEquals(Languages.Spanish, targetSpanish.targetLanguage)
        assertEquals(Languages.German, targetSpanish.nativeLanguage)
    }

    @Test
    fun nativeAndTarget_areNeverTheSame_forAnyCombination() {
        Languages.supported.forEach { native ->
            Languages.supported.forEach { target ->
                val settings = UserSettings().withNativeLanguage(native).withTargetLanguage(target)
                assertNotEquals("$native / $target", settings.nativeLanguage, settings.targetLanguage)
            }
        }
    }
}
