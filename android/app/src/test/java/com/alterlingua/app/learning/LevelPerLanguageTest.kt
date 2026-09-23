package com.alterlingua.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LevelPerLanguageTest {

    @Test
    fun changingTheTarget_showsThatLanguagesOwnLevel_andKeepsTheOldOne() {
        val french = UserSettings(targetLanguage = Languages.French, level = LanguageLevel.INTERMEDIATE)

        val spanish = french.withTargetLanguage(Languages.Spanish)
        assertEquals(LanguageLevel.BEGINNER, spanish.level) // never answered for Español yet
        assertEquals(LanguageLevel.INTERMEDIATE, spanish.otherLevels["fr"])

        val spanishAnswered = spanish.copy(level = LanguageLevel.SOME_BASICS)
        val backToFrench = spanishAnswered.withTargetLanguage(Languages.French)
        assertEquals(LanguageLevel.INTERMEDIATE, backToFrench.level)
        assertEquals(LanguageLevel.SOME_BASICS, backToFrench.otherLevels["es"])
        assertFalse(backToFrench.otherLevels.containsKey("fr"))
    }

    @Test
    fun englishToFrench_andEnglishToSpanish_areBothPlainConfigurations() {
        val enFr = UserSettings(nativeLanguage = Languages.English, targetLanguage = Languages.French)
        val enEs = enFr.withTargetLanguage(Languages.Spanish)
        assertEquals("en", enEs.nativeLanguage.code)
        assertEquals("es", enEs.targetLanguage.code)
    }

    @Test
    fun swappingNativeAndTarget_movesTheLevelWithTheTargetLanguage() {
        val s = UserSettings(
            nativeLanguage = Languages.English,
            targetLanguage = Languages.French,
            level = LanguageLevel.SOME_BASICS,
        )
        // Picking Français as "my language" swaps: English becomes the target.
        val swapped = s.withNativeLanguage(Languages.French)
        assertEquals("fr", swapped.nativeLanguage.code)
        assertEquals("en", swapped.targetLanguage.code)
        assertEquals(LanguageLevel.BEGINNER, swapped.level)
        assertEquals(LanguageLevel.SOME_BASICS, swapped.otherLevels["fr"])
    }

    @Test
    fun choosingTheSameTargetAgain_changesNothing() {
        val s = UserSettings(level = LanguageLevel.INTERMEDIATE)
        assertEquals(s, s.withTargetLanguage(s.targetLanguage))
    }
}
