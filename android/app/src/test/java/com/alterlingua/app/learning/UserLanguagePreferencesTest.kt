package com.alterlingua.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three language settings are separate, and no feature may assume one equals another (CLAUDE.md 19.10). */
class UserLanguagePreferencesTest {

    private val spanishUserLearningGerman = UserSettings(
        nativeLanguage = Languages.Spanish, targetLanguage = Languages.German, appLanguage = Languages.Spanish, appLanguageChosen = true,
    )

    @Test fun theExampleFromTheSpec_appEspanol_sourceEspanol_targetDeutsch() {
        val p = spanishUserLearningGerman.languagePreferences
        assertEquals(Languages.Spanish, p.appLanguage)
        assertEquals(Languages.Spanish, p.sourceLanguage)
        assertEquals(Languages.German, p.activeTargetLanguage)
        assertEquals("es-ES", p.locale)
    }

    @Test fun theAppLanguageDoesNotHaveToEqualTheSourceLanguage() {
        val p = UserSettings(appLanguage = Languages.Japanese, nativeLanguage = Languages.Spanish, targetLanguage = Languages.French).languagePreferences
        assertNotEquals(p.appLanguage, p.sourceLanguage)
        assertEquals("ja-JP", p.locale)
    }

    @Test fun changingTheAppLanguage_changesNothingElse() {
        val before = spanishUserLearningGerman.copy(level = LanguageLevel.INTERMEDIATE, otherLevels = mapOf("fr" to LanguageLevel.BEGINNER))
        for (language in Languages.supported) {
            val after = before.copy(appLanguage = language, appLanguageChosen = true)
            assertEquals(before.nativeLanguage, after.nativeLanguage)
            assertEquals(before.targetLanguage, after.targetLanguage)
            assertEquals(before.level, after.level)
            assertEquals(before.otherLevels, after.otherLevels)
            assertEquals(before.assistanceMode, after.assistanceMode)
        }
    }

    @Test fun changingTheSourceOrTargetDoesNotChangeTheAppLanguage() {
        val s = spanishUserLearningGerman.withTargetLanguage(Languages.Japanese).withNativeLanguage(Languages.French)
        assertEquals(Languages.Spanish, s.appLanguage)
        assertTrue(s.appLanguageChosen)
    }

    @Test fun autoDetectionSendsAuto_aFixedSourceSendsItsCode() {
        val auto = spanishUserLearningGerman.copy(detectSourceAutomatically = true).languagePreferences
        val fixed = spanishUserLearningGerman.copy(detectSourceAutomatically = false).languagePreferences
        assertEquals(SourceDetection.AUTO, auto.sourceDetection)
        assertEquals("auto", auto.requestSource)
        assertEquals(SourceDetection.FIXED, fixed.sourceDetection)
        assertEquals("es", fixed.requestSource)
    }

    @Test fun theKeyboardDirectionIsAutoOrTheSourceCode_towardTheTarget() {
        assertEquals("AUTO → DE", spanishUserLearningGerman.copy(detectSourceAutomatically = true).languagePreferences.directionLabel)
        assertEquals("ES → DE", spanishUserLearningGerman.copy(detectSourceAutomatically = false).languagePreferences.directionLabel)
    }

    @Test fun everySupportedSource_worksAsAFixedSourceToEveryOtherTarget() {
        for (source in Languages.supported) for (target in Languages.supported.filter { it != source }) {
            val p = UserSettings(nativeLanguage = source, targetLanguage = target, detectSourceAutomatically = false).languagePreferences
            assertEquals(source.code, p.requestSource)
            assertEquals("${source.code.uppercase()} → ${target.code.uppercase()}", p.directionLabel)
        }
    }

    @Test fun byDefault_sourceDetectionIsOn_andTheAppLanguageIsNotYetChosen() {
        val d = UserSettings()
        assertTrue(d.detectSourceAutomatically)
        assertFalse(d.appLanguageChosen)
    }
}
