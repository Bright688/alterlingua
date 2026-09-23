package com.alterlingua.app.notifications

/**
 * Decides which messages in a notification are worth translating. It only ever uses what Android hands a notification
 * listener; it never looks for anything else, and it treats a notification the app hides as hidden.
 */
object NotificationExtractor {

    private const val MAX_CHARS = 5_000
    private const val WHATSAPP_NAME = "whatsapp"

    /** WhatsApp starts a notification for a photo, voice note and similar with one of these. There is no text to translate. */
    private val mediaMarkers = listOf("📷", "📸", "🎥", "🎬", "🎤", "🎧", "📄", "📍", "📎", "🖼", "🎵", "📹")

    fun extract(snapshot: NotificationSnapshot, isSource: (String) -> Boolean = IncomingSources::accepts): Extraction {
        fun skip(reason: SkipReason) = Extraction.Skipped(reason)

        if (snapshot.isOwnTranslation) return skip(SkipReason.OWN_NOTIFICATION)
        if (!isSource(snapshot.packageName)) return skip(SkipReason.NOT_A_SOURCE)
        if (snapshot.isGroupSummary) return skip(SkipReason.GROUP_SUMMARY) // "3 new messages": the messages have their own notifications
        if (snapshot.isOngoing) return skip(SkipReason.ONGOING)
        if (snapshot.category != null && snapshot.category != NotificationSnapshot.CATEGORY_MESSAGE) return skip(SkipReason.NOT_A_MESSAGE)
        if (snapshot.visibility == NotificationSnapshot.VISIBILITY_SECRET) return skip(SkipReason.HIDDEN_CONTENT)

        val title = snapshot.title?.trim().orEmpty()
        val raw: List<IncomingMessage> = if (snapshot.messages.isNotEmpty()) {
            snapshot.messages.map { IncomingMessage(it.sender?.trim().takeUnless { s -> s.isNullOrEmpty() } ?: title, it.text?.trim().orEmpty(), it.timestamp) }
        } else {
            // A plain notification: the title names the sender, and the text is the message.
            if (title.equals(WHATSAPP_NAME, ignoreCase = true)) return skip(SkipReason.HIDDEN_CONTENT) // the app's own name means a placeholder
            val text = (snapshot.bigText?.takeIf { it.isNotBlank() } ?: snapshot.text).orEmpty().trim()
            listOf(IncomingMessage(title, text, snapshot.postTime))
        }
        if (raw.all { it.text.isEmpty() }) return skip(SkipReason.MISSING_TEXT)

        var firstSkip: SkipReason? = null
        val keep = mutableListOf<IncomingMessage>()
        for (message in raw) {
            val reason = when {
                message.text.isEmpty() -> SkipReason.MISSING_TEXT
                mediaMarkers.any { message.text.startsWith(it) } -> SkipReason.MEDIA_ONLY
                message.text.none { it.isLetter() } -> SkipReason.NO_LETTERS
                message.text.length > MAX_CHARS -> SkipReason.TOO_LONG
                else -> null
            }
            if (reason == null) keep += message else if (firstSkip == null) firstSkip = reason
        }
        if (keep.isEmpty()) return skip(firstSkip ?: SkipReason.MISSING_TEXT)

        val name = snapshot.conversationTitle?.takeIf { it.isNotBlank() } ?: title
        return Extraction.Messages(
            conversationKey = snapshot.key,
            title = name,
            isGroup = snapshot.isGroupConversation,
            messages = keep,
            openIntent = snapshot.openIntent,
        )
    }
}
