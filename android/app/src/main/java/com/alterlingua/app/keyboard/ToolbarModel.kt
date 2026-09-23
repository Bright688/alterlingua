package com.alterlingua.app.keyboard

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.UserSettings
import java.util.Locale

/** What the area under the toolbar shows: the keys, or the "Translate to" language list. */
enum class ToolbarPanel { KEYS, LANGUAGES }

/**
 * The toolbar's state. [target] and [native] come from the saved settings; both are null until they have been read,
 * so the toolbar never flashes a wrong language.
 */
data class ToolbarState(
    val target: Language? = null,
    val native: Language? = null,
    /** True: the source is detected (AUTO). False: it is always [native]. */
    val detectSource: Boolean = true,
    val panel: ToolbarPanel = ToolbarPanel.KEYS,
) {
    /** For example "AUTO → ES" or, with a fixed source, "EN → ES". AUTO means the source language is detected. */
    val label: String
        get() = (if (detectSource || native == null) "AUTO" else native.code.uppercase(Locale.ROOT)) +
            " → " + (target?.code?.uppercase(Locale.ROOT) ?: "…")

    /**
     * The languages that can be chosen as the translation target: every language that supports it except the user's
     * own language (a target equal to "my language" would swap the two in the app's settings).
     */
    val selectable: List<Language>
        get() = Languages.forLearningSelection.filter { it.code != native?.code }
}

/** Things the toolbar asks the keyboard service to do or show. */
sealed interface ToolbarEvent {
    /** The user tapped Translate. */
    data object Translate : ToolbarEvent

    /** The user tapped the microphone. */
    data object Voice : ToolbarEvent
    data object OpenSettings : ToolbarEvent
}

/**
 * The toolbar's brain. It is separate from [KeyboardController] on purpose: choosing a translation language has
 * nothing to do with the typing layout, and this class has no access to the text field at all, so Translate and the
 * microphone cannot change what the user has typed.
 */
class ToolbarController(
    private val saveTarget: (Language) -> Unit,
    private val onEvent: (ToolbarEvent) -> Unit,
) {
    var state = ToolbarState()
        private set

    var onStateChanged: ((ToolbarState) -> Unit)? = null

    /** The saved settings were read or changed. */
    fun onSettingsChanged(settings: UserSettings) {
        update(state.copy(target = settings.targetLanguage, native = settings.nativeLanguage, detectSource = settings.detectSourceAutomatically))
    }

    fun openLanguages() {
        if (state.target != null) update(state.copy(panel = ToolbarPanel.LANGUAGES))
    }

    fun closeLanguages() = update(state.copy(panel = ToolbarPanel.KEYS))

    /** Chooses a translation target. Only languages in [ToolbarState.selectable] are accepted. */
    fun selectLanguage(language: Language) {
        if (language !in state.selectable) return
        if (language != state.target) saveTarget(language)
        update(state.copy(target = language, panel = ToolbarPanel.KEYS))
    }

    fun onTranslate() = onEvent(ToolbarEvent.Translate)

    fun onMicrophone() = onEvent(ToolbarEvent.Voice)

    fun onSettings() = onEvent(ToolbarEvent.OpenSettings)

    private fun update(new: ToolbarState) {
        if (new == state) return
        state = new
        onStateChanged?.invoke(new)
    }
}
