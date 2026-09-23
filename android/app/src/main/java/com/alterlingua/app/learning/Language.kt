package com.alterlingua.app.learning

/** How a language is written. Text handling must never assume Latin letters or spaces between words. */
enum class WritingSystem { LATIN, CHINESE, JAPANESE }

/**
 * One language in the central catalogue (CLAUDE.md "Language Support", sections 6.1 and 6.6).
 *
 * The identity of a language is its base [code] (for example "zh"). A [locale] such as "zh-CN" is a separate idea:
 * it is the default regional or script variant used where that matters (speech, for example), and it is never
 * part of what identifies the language or what is saved.
 *
 * The support flags describe what is available today. Providers can differ per language, so nothing here assumes
 * that translation, speech recognition, text-to-speech and lessons cover the same languages.
 */
data class Language(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val locale: String,
    val writingSystem: WritingSystem,
    val translationSupported: Boolean = true,
    val speechToTextSupported: Boolean = false,
    val textToSpeechSupported: Boolean = false,
    val learningSupported: Boolean = true,
) {
    /** The name shown in language selectors: the language's own name (section 6.2). */
    val displayName: String get() = nativeName

    /**
     * A smaller helper label shown under [displayName] in selectors, so people who cannot read the script can still
     * tell languages apart ("Chinese" under 中文). It is null when the two names are the same (English).
     */
    val secondaryName: String? get() = englishName.takeIf { it != nativeName }
}

/** The central language catalogue. Add a language here (and give it sample content) to offer it. */
object Languages {
    // Speech support is false until a speech provider is configured (later milestones).
    val English = Language("en", "English", "English", "en-US", WritingSystem.LATIN)
    val French = Language("fr", "Français", "French", "fr-FR", WritingSystem.LATIN)
    val Spanish = Language("es", "Español", "Spanish", "es-ES", WritingSystem.LATIN)
    val German = Language("de", "Deutsch", "German", "de-DE", WritingSystem.LATIN)
    val Italian = Language("it", "Italiano", "Italian", "it-IT", WritingSystem.LATIN)
    val Dutch = Language("nl", "Nederlands", "Dutch", "nl-NL", WritingSystem.LATIN)
    val Chinese = Language("zh", "中文", "Chinese", "zh-CN", WritingSystem.CHINESE)
    val Japanese = Language("ja", "日本語", "Japanese", "ja-JP", WritingSystem.JAPANESE)

    /** The initial languages, in the order they appear in selectors. Not a permanent closed list. */
    val supported: List<Language> = listOf(English, French, Spanish, German, Italian, Dutch, Chinese, Japanese)

    /** Languages the user can choose as "my language". */
    val forNativeSelection: List<Language> get() = supported.filter { it.translationSupported }

    /** Languages the user can choose to communicate and learn in. */
    val forLearningSelection: List<Language> get() = supported.filter { it.translationSupported && it.learningSupported }

    /**
     * Finds a language from a saved value. Accepts a plain code ("zh") and also a locale or script tag
     * ("zh-CN", "zh-Hans"), so values saved by an earlier version still load.
     */
    fun fromCode(value: String?): Language? {
        val base = value?.substringBefore('-')?.lowercase() ?: return null
        return supported.firstOrNull { it.code == base }
    }
}
