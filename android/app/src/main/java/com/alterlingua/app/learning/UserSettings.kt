package com.alterlingua.app.learning

import java.time.LocalTime

/** Why the user is learning the language. */
enum class LearningPurpose { WORK, TRAVEL, FAMILY, STUDY }

/** The user's current level in one language they are learning. */
enum class LanguageLevel { BEGINNER, SOME_BASICS, INTERMEDIATE }

/**
 * How a language is typed, for the languages that have more than one common way. The Latin-script languages have one style
 * (their own layout), so they are not listed here.
 */
enum class KeyboardStyle {
    /** 中文: the five basic strokes as keys (the default). */
    STROKE,

    /** 中文: bopomofo symbols as keys (Zhuyin), on the standard key positions. */
    ZHUYIN,

    /** 中文: pinyin typed with Latin letters on a QWERTY keyboard. */
    PINYIN_26,

    /** 日本語: the 12-key kana keyboard with flick and hold (the default). */
    KANA,

    /** 日本語: romaji on a QWERTY keyboard, converted to kana and kanji. */
    ROMAJI,
    ;

    companion object {
        /** The styles offered for [languageCode], the default first; empty when the language has only one way of typing. */
        fun forLanguage(languageCode: String): List<KeyboardStyle> = when (languageCode) {
            "zh" -> listOf(STROKE, ZHUYIN, PINYIN_26)
            "ja" -> listOf(KANA, ROMAJI)
            else -> emptyList()
        }
    }
}

/**
 * Everything captured in onboarding. Saved on the phone with DataStore.
 *
 * Three language settings, kept apart on purpose (they have different jobs and must never be tied to each other):
 * - [appLanguage]: the language AlterLingua itself is shown in (menus, lessons navigation, Settings, errors, the keyboard's own
 *   labels). Independent of the other two; changing it never changes or erases anything else.
 * - [nativeLanguage]: the user's source/default language, the one they normally write or speak, and the language incoming
 *   messages are translated into. With [detectSourceAutomatically] the source of each message is detected instead of assumed.
 * - [targetLanguage]: the language messages are translated into and the one being learned; the Personal Language Map follows it.
 */
data class UserSettings(
    val onboardingCompleted: Boolean = false,
    /** True once the first-launch welcome screen (the app's promise, before any language question) has been shown. */
    val welcomeSeen: Boolean = false,
    val nativeLanguage: Language = Languages.English,
    /** The language AlterLingua is displayed in. Only applied once [appLanguageChosen] is true; before that the phone's language is used. */
    val appLanguage: Language = Languages.English,
    val appLanguageChosen: Boolean = false,
    /** True: the language of each message or recording is detected (AUTO). False: the source is always [nativeLanguage]. */
    val detectSourceAutomatically: Boolean = true,
    /** True: the keyboard translates by itself a short pause after typing stops. False (the default): only the
     * Translate button triggers it. Off by default so nothing changes for anyone who has not chosen this. */
    val autoTranslateEnabled: Boolean = false,
    val targetLanguage: Language = Languages.French,
    val purpose: LearningPurpose = LearningPurpose.WORK,
    /** The level in the current [targetLanguage]. */
    val level: LanguageLevel = LanguageLevel.BEGINNER,
    /**
     * Levels the user gave for their other learning languages, by language code (never includes [targetLanguage]).
     * Kept so switching the target language and back does not lose the answer for either one.
     */
    val otherLevels: Map<String, LanguageLevel> = emptyMap(),
    val assistanceMode: AssistanceMode = AssistanceMode.FULL_SUPPORT,
    val dailyReminderEnabled: Boolean = true,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    /** Whether AlterLingua picks useful words and phrases out of translated messages. It keeps only those units, never the messages. */
    val learningFromMessagesEnabled: Boolean = true,
    /** Whether incoming messages from a supported chat app (see IncomingSources) are translated into the user's own language (needs notification access). */
    val incomingTranslationEnabled: Boolean = true,
    /** True once the system microphone question has been shown. Lets setup tell "never asked" from "blocked". */
    val microphonePermissionAsked: Boolean = false,
    /** The chosen typing style per language code, only for languages with more than one (see [KeyboardStyle.forLanguage]). */
    val keyboardStyles: Map<String, KeyboardStyle> = emptyMap(),
) {
    /** The typing style in use for [languageCode]: the saved one if it is valid for that language, otherwise its default; null if it has only one. */
    fun keyboardStyleFor(languageCode: String): KeyboardStyle? {
        val offered = KeyboardStyle.forLanguage(languageCode)
        return keyboardStyles[languageCode]?.takeIf { it in offered } ?: offered.firstOrNull()
    }

    /**
     * Sets the language the user writes in. The two languages must differ, so if the user picks the current
     * target language, the two swap places.
     */
    fun withNativeLanguage(language: Language): UserSettings =
        if (targetLanguage == language) {
            copy(nativeLanguage = language).changingTargetTo(nativeLanguage)
        } else {
            copy(nativeLanguage = language)
        }

    /** Sets the target language. If the user picks the current native language, the two swap places. */
    fun withTargetLanguage(language: Language): UserSettings =
        copy(nativeLanguage = if (nativeLanguage == language) targetLanguage else nativeLanguage)
            .changingTargetTo(language)

    /** The level is about one language, so it follows the target: the old answer is kept, the new language's is shown. */
    private fun changingTargetTo(language: Language): UserSettings {
        if (language == targetLanguage) return this
        val remembered = otherLevels + (targetLanguage.code to level)
        return copy(
            targetLanguage = language,
            level = remembered[language.code] ?: LanguageLevel.BEGINNER,
            otherLevels = remembered - language.code,
        )
    }

    /** The same settings seen as language preferences, with the source choice made explicit (see [UserLanguagePreferences]). */
    val languagePreferences: UserLanguagePreferences
        get() = UserLanguagePreferences(
            appLanguage = appLanguage,
            sourceLanguage = nativeLanguage,
            sourceDetection = if (detectSourceAutomatically) SourceDetection.AUTO else SourceDetection.FIXED,
            activeTargetLanguage = targetLanguage,
        )

    companion object {
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)
    }
}
