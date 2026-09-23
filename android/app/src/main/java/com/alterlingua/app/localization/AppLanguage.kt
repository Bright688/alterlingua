package com.alterlingua.app.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.UserSettings
import java.util.Locale

/**
 * Applies the user's APP language: the language AlterLingua's own interface (menus, Settings, errors, the keyboard's labels and
 * AlterLingua's notifications) is shown in. It is separate from the source and target languages (CLAUDE.md 19.2): it only decides
 * which `values-xx` string resources are used, and changing it never touches settings, progress or Personal Language Maps.
 *
 * Until the user has chosen an app language (first launch), the phone's own language is used.
 */
object AppLanguage {

    /** [base] with its resources in [language]; the same context when there is no language to apply. */
    fun wrap(base: Context, language: Language?): Context {
        if (language == null) return base
        val locale = Locale.forLanguageTag(language.locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    /** [base] in the user's chosen app language, or unchanged if none has been chosen yet. */
    fun forSettings(base: Context, settings: UserSettings): Context = wrap(base, settings.appLanguage.takeIf { settings.appLanguageChosen })

    /** A short key for "which app language is applied", used to notice when it changes. Empty means the phone's language. */
    fun keyOf(settings: UserSettings): String = if (settings.appLanguageChosen) settings.appLanguage.code else ""
}
