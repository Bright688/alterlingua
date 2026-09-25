package com.alterlingua.app.setup

/**
 * Where the microphone permission stands. Android does not say "never asked" and "permanently denied" apart by
 * itself, so we remember whether we have asked before (see [microphoneStatus]).
 */
enum class MicrophoneStatus {
    /** The user allowed the microphone (for now or always). */
    GRANTED,

    /** We have not asked yet. */
    NOT_ASKED,

    /** The user declined, and Android will still show the question if we ask again. */
    DECLINED,

    /** Android will not show the question again (declined twice, or blocked). Only Android settings can change it. */
    BLOCKED,
}

/** What is set up right now. Read from Android each time the user returns to the app. */
data class SetupStatus(
    val keyboardEnabled: Boolean = false,
    val keyboardSelected: Boolean = false,
    val notificationAccess: Boolean = false,
    /** AlterLingua may post its own notifications (needed to show translated messages). */
    val postNotifications: Boolean = false,
    val microphone: MicrophoneStatus = MicrophoneStatus.NOT_ASKED,
    /** AlterLinguaAccessibilityService is switched on in Android's Accessibility settings (off by default and optional). */
    val accessibilityServiceEnabled: Boolean = false,
) {
    /** The keyboard is turned on and is the one currently in use. */
    val keyboardReady: Boolean get() = keyboardEnabled && keyboardSelected
    val microphoneGranted: Boolean get() = microphone == MicrophoneStatus.GRANTED
}

/**
 * Decides the microphone state from what Android tells us:
 * - [granted]: the permission check.
 * - [askedBefore]: we have shown the system question at least once (saved by the app).
 * - [showRationale]: Android's "should show rationale" answer, which is true after a first decline.
 */
fun microphoneStatus(granted: Boolean, askedBefore: Boolean, showRationale: Boolean): MicrophoneStatus = when {
    granted -> MicrophoneStatus.GRANTED
    !askedBefore -> MicrophoneStatus.NOT_ASKED
    showRationale -> MicrophoneStatus.DECLINED
    else -> MicrophoneStatus.BLOCKED
}

/**
 * True when [flattenedInputMethodId] (what Android stores for the chosen keyboard, for example
 * "com.alterlingua.app/.keyboard.AlterLinguaKeyboardService") is the given keyboard service.
 * Handles both the short form (".keyboard.X") and the full class name.
 */
fun isSelectedKeyboard(flattenedInputMethodId: String?, packageName: String, serviceClassName: String): Boolean {
    val id = flattenedInputMethodId?.trim().orEmpty()
    val slash = id.indexOf('/')
    if (slash <= 0 || slash == id.lastIndex) return false
    val pkg = id.substring(0, slash)
    val cls = id.substring(slash + 1).let { if (it.startsWith(".")) pkg + it else it }
    return pkg == packageName && cls == serviceClassName
}
