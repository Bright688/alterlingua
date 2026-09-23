package com.alterlingua.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationExtractorTest {

    private val whatsappOnly = { pkg: String -> pkg == "com.whatsapp" }

    private fun snapshot(
        title: String? = "Marie",
        text: String? = "Tu viens demain ?",
        pkg: String = "com.whatsapp",
        block: NotificationSnapshot.() -> NotificationSnapshot = { this },
    ) = NotificationSnapshot(packageName = pkg, key = "0|com.whatsapp|1|null|10001", category = "msg", title = title, text = text, postTime = 1_000).block()

    private fun extract(s: NotificationSnapshot) = NotificationExtractor.extract(s, whatsappOnly)

    /** Uses the real, full source list, for tests about which apps are accepted rather than about extraction itself. */
    private fun extractFromAnySupportedApp(s: NotificationSnapshot) = NotificationExtractor.extract(s)

    private fun skipped(s: NotificationSnapshot) = (extract(s) as Extraction.Skipped).reason

    private fun messages(s: NotificationSnapshot) = extract(s) as Extraction.Messages

    // ---- the plain case ----

    @Test
    fun aPlainMessage_isTheSendersNameAndText() {
        val result = messages(snapshot())
        assertEquals("Marie", result.title)
        assertEquals(listOf(IncomingMessage("Marie", "Tu viens demain ?", 1_000)), result.messages)
        assertEquals(false, result.isGroup)
    }

    @Test
    fun messagesInAnyScriptAreKept() {
        for (text in listOf("明日来ますか？", "你明天来吗？", "Kommst du morgen?", "¿Vienes mañana?", "Kom je morgen?")) {
            assertEquals(text, messages(snapshot(text = text)).messages.single().text)
        }
    }

    @Test
    fun theLongerTextIsPreferredWhenThereIsOne() {
        val long = "Salut ! Tu viens demain soir chez nous pour le dîner ? On mange vers vingt heures."
        assertEquals(long, messages(snapshot(text = "Salut ! Tu viens demain soir chez nous pour…") { copy(bigText = long) }).messages.single().text)
    }

    @Test
    fun theOpenActionIsPassedThroughUntouched() {
        val open = Any()
        assertTrue(messages(snapshot { copy(openIntent = open) }).openIntent === open)
    }

    // ---- only WhatsApp, and never our own notifications ----

    @Test
    fun otherAppsAreNeverRead() {
        for (pkg in listOf("com.instagram.android", "com.google.android.gm", "com.viber.voip", "com.whatsapp.fake")) {
            assertEquals(pkg, SkipReason.NOT_A_SOURCE, skipped(snapshot(pkg = pkg)))
        }
    }

    @Test
    fun alterLinguasOwnTranslationsAreNeverTranslatedAgain() {
        assertEquals(SkipReason.OWN_NOTIFICATION, skipped(snapshot { copy(isOwnTranslation = true) }))
    }

    @Test
    fun theDefaultSourceListCoversTheSupportedChatApps_andNothingElse() {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca", "org.thoughtcrime.securesms")) {
            assertTrue(pkg, IncomingSources.accepts(pkg))
        }
        for (pkg in listOf("com.instagram.android", "com.google.android.gm", "com.viber.voip")) {
            assertTrue(pkg, !IncomingSources.accepts(pkg))
        }
    }

    @Test
    fun aGenericPlaceholderUnderTheAppNameIsHidden_forEverySupportedApp() {
        for ((pkg, name) in mapOf("com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram", "com.facebook.orca" to "Messenger", "org.thoughtcrime.securesms" to "Signal")) {
            val result = extractFromAnySupportedApp(snapshot(pkg = pkg, title = name, text = "1 new message"))
            assertEquals(pkg, SkipReason.HIDDEN_CONTENT, (result as Extraction.Skipped).reason)
        }
    }

    // ---- grouped, ongoing and non-message notifications ----

    @Test
    fun aGroupSummaryIsSkipped_becauseTheMessagesHaveTheirOwnNotifications() {
        assertEquals(SkipReason.GROUP_SUMMARY, skipped(snapshot(title = "WhatsApp", text = "3 new messages from 2 chats") { copy(isGroupSummary = true) }))
    }

    @Test
    fun ongoingAndNonMessageNotificationsAreSkipped() {
        assertEquals(SkipReason.ONGOING, skipped(snapshot(title = "WhatsApp", text = "Checking for new messages") { copy(isOngoing = true) }))
        assertEquals(SkipReason.NOT_A_MESSAGE, skipped(snapshot { copy(category = "call") }))
        assertEquals(SkipReason.NOT_A_MESSAGE, skipped(snapshot { copy(category = "progress") }))
    }

    @Test
    fun aMissingCategoryIsStillTreatedAsAMessage() {
        assertTrue(extract(snapshot { copy(category = null) }) is Extraction.Messages)
    }

    // ---- hidden or missing content ----

    @Test
    fun secretNotificationsAreHidden() {
        assertEquals(SkipReason.HIDDEN_CONTENT, skipped(snapshot { copy(visibility = NotificationSnapshot.VISIBILITY_SECRET) }))
    }

    @Test
    fun aGenericPlaceholderUnderTheAppNameIsHidden() {
        assertEquals(SkipReason.HIDDEN_CONTENT, skipped(snapshot(title = "WhatsApp", text = "1 new message")))
        assertEquals(SkipReason.HIDDEN_CONTENT, skipped(snapshot(title = "whatsapp", text = "New message")))
    }

    @Test
    fun missingOrBlankTextIsSkipped() {
        for (text in listOf(null, "", "   ", "\n")) {
            assertEquals(SkipReason.MISSING_TEXT, skipped(snapshot(text = text)))
        }
        assertEquals(SkipReason.MISSING_TEXT, skipped(snapshot(title = null, text = null)))
    }

    @Test
    fun aMessageWithNoTitleStillWorks_withAnEmptySender() {
        assertEquals("", messages(snapshot(title = null)).messages.single().sender)
    }

    // ---- media and things with no words ----

    @Test
    fun photosVoiceNotesAndOtherMediaAreSkipped() {
        for (text in listOf("📷 Photo", "🎤 Voice message (0:12)", "🎥 Video", "📄 Document", "📍 Location")) {
            assertEquals(text, SkipReason.MEDIA_ONLY, skipped(snapshot(text = text)))
        }
    }

    @Test
    fun emojiNumbersAndSymbolsAloneAreSkipped() {
        for (text in listOf("👍", "12:30", "!!!", "😂😂 123")) {
            assertEquals(text, SkipReason.NO_LETTERS, skipped(snapshot(text = text)))
        }
    }

    @Test
    fun aVeryLongMessageIsSkipped() {
        assertEquals(SkipReason.TOO_LONG, skipped(snapshot(text = "a".repeat(5_001))))
    }

    // ---- several messages in one notification (WhatsApp's messaging style) ----

    @Test
    fun aConversationNotification_giveEveryMessageWithItsSender() {
        val s = snapshot(title = "Family", text = "Papa: Tu viens ?") {
            copy(
                conversationTitle = "Family",
                isGroupConversation = true,
                messages = listOf(
                    SnapshotMessage("Papa", "Tu viens ?", 10),
                    SnapshotMessage("Maman", "Kommst du morgen?", 20),
                ),
            )
        }
        val result = messages(s)
        assertEquals("Family", result.title)
        assertEquals(true, result.isGroup)
        assertEquals(listOf(IncomingMessage("Papa", "Tu viens ?", 10), IncomingMessage("Maman", "Kommst du morgen?", 20)), result.messages)
    }

    @Test
    fun inAMixedNotification_onlyTheTextMessagesAreKept() {
        val s = snapshot { copy(messages = listOf(SnapshotMessage("Marie", "📷 Photo", 1), SnapshotMessage("Marie", "Tu viens demain ?", 2), SnapshotMessage("Marie", "👍", 3))) }
        assertEquals(listOf("Tu viens demain ?"), messages(s).messages.map { it.text })
    }

    @Test
    fun aMessageWithoutASenderUsesTheTitle() {
        val s = snapshot(title = "Marie") { copy(messages = listOf(SnapshotMessage(null, "Tu viens demain ?", 1))) }
        assertEquals("Marie", messages(s).messages.single().sender)
    }

    @Test
    fun aNotificationWhoseMessagesAreAllMediaIsSkipped() {
        val s = snapshot { copy(messages = listOf(SnapshotMessage("Marie", "🎤 Voice message", 1))) }
        assertEquals(SkipReason.MEDIA_ONLY, skipped(s))
    }
}
