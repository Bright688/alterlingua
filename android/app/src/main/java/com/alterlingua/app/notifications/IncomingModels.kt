package com.alterlingua.app.notifications

import com.alterlingua.app.BuildConfig

/**
 * The chat apps whose notifications AlterLingua reads. Nothing from any app outside this list is ever looked at
 * (CLAUDE.md sections 21, 38): a new app is added here, not by teaching the notification listener or the translator
 * about it individually. Debug builds add AlterLingua itself, so the feature can be tried with test messages (see
 * docs/progress.md).
 */
object IncomingSources {
    const val WHATSAPP = "com.whatsapp"

    /** Package name to the app's own display name (used to recognise the placeholder title some apps post when they hide message content, e.g. plain "Telegram" instead of a sender). */
    private val KNOWN: Map<String, String> = mapOf(
        WHATSAPP to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger",
        "org.thoughtcrime.securesms" to "Signal",
    )

    private val extra: Set<String> = BuildConfig.EXTRA_INCOMING_PACKAGES.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun accepts(packageName: String): Boolean = packageName in KNOWN || packageName in extra

    /** Null when the package has no known display name (an extra debug-only package). */
    fun displayNameOf(packageName: String): String? = KNOWN[packageName]

    /** For Settings copy: the apps this build can translate incoming messages from. */
    val displayNames: List<String> get() = KNOWN.values.toList()
}

/** One message inside a notification, as Android exposes it. */
data class SnapshotMessage(val sender: String?, val text: String?, val timestamp: Long) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "SnapshotMessage(redacted)"
}

/**
 * The parts of a notification AlterLingua looks at, copied out of Android's objects so the rules that use them can be
 * tested without a phone. It lives in memory only while one notification is processed.
 */
data class NotificationSnapshot(
    val packageName: String,
    val key: String,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val category: String? = null,
    val visibility: Int = VISIBILITY_PRIVATE,
    val postTime: Long = 0,
    /** True for the translated notifications AlterLingua itself posts, so they are never translated again. */
    val isOwnTranslation: Boolean = false,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val conversationTitle: String? = null,
    val isGroupConversation: Boolean = false,
    val messages: List<SnapshotMessage> = emptyList(),
    /** WhatsApp's own "open this chat" action, exactly as the notification offers it (a PendingIntent). Opaque here. */
    val openIntent: Any? = null,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "NotificationSnapshot(redacted)"
    companion object {
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1
        const val CATEGORY_MESSAGE = "msg"
    }
}

/** A message worth translating. */
data class IncomingMessage(val sender: String, val text: String, val timestamp: Long) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "IncomingMessage(redacted)"
}

/** Why a notification was left alone. */
enum class SkipReason {
    NOT_A_SOURCE,
    OWN_NOTIFICATION,
    GROUP_SUMMARY,
    ONGOING,
    NOT_A_MESSAGE,

    /** The app hides the message text (secret visibility, or a generic placeholder). */
    HIDDEN_CONTENT,
    MISSING_TEXT,

    /** A photo, voice note or similar, which has no text to translate. */
    MEDIA_ONLY,

    /** Only emoji, numbers or symbols. */
    NO_LETTERS,
    TOO_LONG,
}

sealed interface Extraction {
    data class Messages(val conversationKey: String, val title: String, val isGroup: Boolean, val messages: List<IncomingMessage>, val openIntent: Any?) : Extraction

    data class Skipped(val reason: SkipReason) : Extraction
}

/** A message after translation. */
data class TranslatedLine(val sender: String, val text: String, val sourceLanguage: String) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslatedLine(redacted)"
}

/** What the translated notification for one conversation shows. */
data class TranslatedConversation(
    val key: String,
    val title: String,
    val isGroup: Boolean,
    val lines: List<TranslatedLine>,
    val openIntent: Any?,
) {
    /** Never printed: this holds private text, so it cannot reach a log or a crash report. */
    override fun toString(): String = "TranslatedConversation(redacted)"
}

/** How an incoming message ended, without any of its text. Shown as one line in Settings. */
enum class IncomingOutcomeKind {
    TRANSLATED,
    ALREADY_IN_YOUR_LANGUAGE,
    HIDDEN_CONTENT,
    NOTIFICATIONS_BLOCKED,
    OFFLINE,
    BACKEND_UNAVAILABLE,
    TIMEOUT,
    TRANSLATION_FAILED,
    UNSUPPORTED_LANGUAGE,
    LANGUAGE_UNDETECTED,
    TOO_LONG,
    NOT_CONFIGURED,
}

data class IncomingOutcome(val kind: IncomingOutcomeKind, val sourceLanguage: String? = null, val atMillis: Long = 0)
