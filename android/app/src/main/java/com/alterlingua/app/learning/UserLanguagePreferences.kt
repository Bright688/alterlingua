package com.alterlingua.app.learning

/** How the language of what the user writes or says is found. */
enum class SourceDetection {
    /** Detected for every message or recording (shown as AUTO). */
    AUTO,

    /** Always the user's source language. */
    FIXED,
}

/**
 * The user's language configuration, as three independent settings plus how the source is found (CLAUDE.md 19.2 and 19.7).
 * No feature may assume [appLanguage] equals [sourceLanguage], or that the source is English.
 *
 * - [appLanguage]: what AlterLingua's own interface is shown in.
 * - [sourceLanguage]: what the user normally writes or speaks (their default), and what incoming messages are translated into.
 * - [sourceDetection]: whether the source is detected per message (AUTO) or fixed to [sourceLanguage].
 * - [activeTargetLanguage]: what messages are translated into; the language whose Personal Language Map is active.
 * - [locale]: the BCP-47 tag for the interface (a locale is a regional variant of the language, kept apart from its code).
 *
 * Keyboard layout preferences are not stored yet (the keyboard has one layout); see docs/localization.md.
 */
data class UserLanguagePreferences(
    val appLanguage: Language,
    val sourceLanguage: Language,
    val sourceDetection: SourceDetection,
    val activeTargetLanguage: Language,
) {
    val locale: String get() = appLanguage.locale

    /** The source to send with a translation request: "auto", or the fixed source language code. */
    val requestSource: String get() = if (sourceDetection == SourceDetection.AUTO) "auto" else sourceLanguage.code

    /** The compact direction shown on the keyboard toolbar, for example "AUTO → FR" or "ES → FR". */
    val directionLabel: String
        get() = (if (sourceDetection == SourceDetection.AUTO) "AUTO" else sourceLanguage.code.uppercase(java.util.Locale.ROOT)) +
            " → " + activeTargetLanguage.code.uppercase(java.util.Locale.ROOT)
}
