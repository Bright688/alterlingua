package com.alterlingua.app.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every interface language has every string, with the same placeholders, and the wording the spec asks for. */
class LocalizationResourcesTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.exists() }
    private val locales = listOf("fr", "es", "de", "it", "nl", "zh", "ja")
    private val brand = setOf("app_name", "keyboard_name", "notification_listener_name", "notif_public_title", "accessibility_service_name")

    private fun strings(folder: String): Map<String, String> {
        val text = File(res, "$folder/strings.xml").readText(Charsets.UTF_8)
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val english = strings("values")
    private fun placeholders(v: String) = Regex("%\\d\\$[sd]").findAll(v).map { it.value }.sorted().toList()

    @Test fun allSevenOtherInterfaceLanguagesHaveAResourceFile() {
        for (l in locales) assertTrue(l, File(res, "values-$l/strings.xml").exists())
    }

    @Test fun everyLanguageHasExactlyTheTranslatableStringsOfEnglish() {
        val expected = english.keys - brand
        for (l in locales) assertEquals(l, expected, strings("values-$l").keys)
    }

    @Test fun brandNamesAreNotTranslated() {
        val text = File(res, "values/strings.xml").readText()
        for (k in brand) assertTrue(k, text.contains("name=\"$k\" translatable=\"false\""))
        for (l in locales) assertTrue(l, strings("values-$l").keys.none { it in brand })
    }

    @Test fun placeholdersMatchEnglishInEveryLanguage() {
        for (l in locales) for ((k, v) in strings("values-$l")) assertEquals("$l $k", placeholders(english.getValue(k)), placeholders(v))
    }

    /** Strings that are legitimately the same as English in a language (shared spelling), reviewed by hand. */
    private val sameAsEnglishIsFine = setOf(
        "voice_original", "toolbar_microphone", "nav_home", "nav_words", "voice_stop", "voice_stop_listening", "translation_cancel", "voice_cancel",
        "action_ok", "onb_step_microphone", "mode_adaptive", "wd_status_count", "vn_original_lang", "onb_summary_assistance", "onb_summary_level",
    )

    @Test fun noTranslationIsEmpty_orLeftInEnglish() {
        val offenders = mutableListOf<String>()
        for (l in locales) for ((k, v) in strings("values-$l")) {
            assertTrue("$l $k is empty", v.isNotBlank())
            if (v == english.getValue(k) && english.getValue(k).length > 12 && k !in sameAsEnglishIsFine) offenders += "$l:$k"
        }
        assertEquals("translations left in English", emptyList<String>(), offenders)
    }

    @Test fun theAppLanguagePromptsAreTheOnesInTheSpec() {
        val expected = mapOf(
            "values" to "Choose your language", "values-fr" to "Choisissez votre langue", "values-es" to "Elige tu idioma", "values-de" to "Wähle deine Sprache",
            "values-it" to "Scegli la tua lingua", "values-nl" to "Kies je taal", "values-zh" to "选择您的语言", "values-ja" to "言語を選択",
        )
        for ((folder, prompt) in expected) assertEquals(folder, prompt, strings(folder).getValue("choose_language_prompt"))
    }

    @Test fun theKeyboardActionsAreTheOnesInTheSpec() {
        val translate = mapOf("values" to "Translate", "values-fr" to "Traduire", "values-es" to "Traducir", "values-de" to "Übersetzen", "values-it" to "Traduci", "values-nl" to "Vertalen", "values-zh" to "翻译", "values-ja" to "翻訳")
        val settings = mapOf("values" to "Settings", "values-fr" to "Paramètres", "values-es" to "Configuración", "values-de" to "Einstellungen", "values-it" to "Impostazioni", "values-nl" to "Instellingen", "values-zh" to "设置", "values-ja" to "設定")
        for ((f, v) in translate) assertEquals(f, v, strings(f).getValue("toolbar_translate"))
        for ((f, v) in settings) assertEquals(f, v, strings(f).getValue("nav_settings"))
    }

    @Test fun languagesAreNeverNamedInEnglishInTheirOwnSelector() {
        // The selector shows native names from the language catalogue, not from string resources.
        for (k in listOf("Spanish", "French", "German", "Italian", "Dutch", "Chinese", "Japanese")) {
            assertFalse(k, english.getValue("settings_source_language").contains(k))
        }
    }

    @Test fun appLanguageAndTheOtherTwoLanguageSettingsAreSeparateStrings() {
        for (folder in listOf("values") + locales.map { "values-$it" }) {
            val s = strings(folder)
            assertEquals(folder, 3, listOf("settings_app_language", "settings_source_language", "settings_target_language").map { s.getValue(it) }.toSet().size)
        }
    }
}
