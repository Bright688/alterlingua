package com.alterlingua.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on a device or emulator. Builds REAL notifications shaped like WhatsApp's (messaging style, with senders), reads
 * them with the real reader, and posts the real translated notification. Needs no WhatsApp and no network.
 */
class IncomingNotificationDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private fun builder() = NotificationCompat.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)

    private fun read(notification: Notification) =
        NotificationSnapshotReader.read("com.whatsapp", "0|com.whatsapp|1|null|1", notification, postTime = 1_000)

    private fun messagingStyle(group: String? = null): NotificationCompat.MessagingStyle {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        style.addMessage("Tu viens demain ?", 10, Person.Builder().setName("Marie").build())
        style.addMessage("Kommst du morgen?", 20, Person.Builder().setName("Paul").build())
        if (group != null) style.setConversationTitle(group).setGroupConversation(true)
        return style
    }

    @Test
    fun readsASingleChatMessage() {
        val snapshot = read(builder().setContentTitle("Marie").setContentText("Tu viens demain ?").setCategory(Notification.CATEGORY_MESSAGE).build())
        assertEquals("Marie", snapshot.title)
        assertEquals("Tu viens demain ?", snapshot.text)
        assertFalse(snapshot.isGroupSummary || snapshot.isOngoing || snapshot.isOwnTranslation)
        assertEquals(Notification.CATEGORY_MESSAGE, snapshot.category)
    }

    @Test
    fun readsAMessagingStyleGroupChat_withSenders() {
        val snapshot = read(builder().setContentTitle("Family").setStyle(messagingStyle(group = "Family")).setCategory(Notification.CATEGORY_MESSAGE).build())
        assertEquals("Family", snapshot.conversationTitle)
        assertTrue(snapshot.isGroupConversation)
        assertEquals(listOf("Marie" to "Tu viens demain ?", "Paul" to "Kommst du morgen?"), snapshot.messages.map { it.sender to it.text })
        val extraction = NotificationExtractor.extract(snapshot, isSource = { true }) as Extraction.Messages
        assertEquals(2, extraction.messages.size)
    }

    @Test
    fun readsTheFlagsThatMakeANotificationASummaryOrOngoing() {
        assertTrue(read(builder().setContentTitle("WhatsApp").setGroupSummary(true).setGroup("g").build()).isGroupSummary)
        assertTrue(read(builder().setContentTitle("WhatsApp").setOngoing(true).build()).isOngoing)
    }

    @Test
    fun readsSecretVisibility() {
        val snapshot = read(builder().setContentTitle("Marie").setContentText("hidden").setVisibility(NotificationCompat.VISIBILITY_SECRET).build())
        assertEquals(NotificationSnapshot.VISIBILITY_SECRET, snapshot.visibility)
        assertEquals(SkipReason.HIDDEN_CONTENT, (NotificationExtractor.extract(snapshot, isSource = { true }) as Extraction.Skipped).reason)
    }

    @Test
    fun aNotificationWithNothingInIt_doesNotCrashTheReader() {
        val snapshot = read(builder().build())
        assertEquals(SkipReason.MISSING_TEXT, (NotificationExtractor.extract(snapshot, isSource = { true }) as Extraction.Skipped).reason)
    }

    @Test
    fun theTranslatedNotificationIsPosted_withTitleTextAndSourceLanguage_andMarkedAsOurOwn() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        val presenter = TranslatedNotificationPresenter(context)
        assertTrue("notifications are off for AlterLingua on this device", presenter.canPost())

        val key = "device-test-conversation"
        presenter.show(TranslatedConversation(key, "Marie", false, listOf(TranslatedLine("Marie", "Are you coming tomorrow?", "fr")), openIntent = null))
        // The system posts notifications asynchronously: give it a moment before reading them back.
        var posted = manager.activeNotifications.firstOrNull { it.tag == "alterlingua-translation" && it.id == key.hashCode() }
        for (attempt in 1..30) {
            if (posted != null) break
            Thread.sleep(100)
            posted = manager.activeNotifications.firstOrNull { it.tag == "alterlingua-translation" && it.id == key.hashCode() }
        }
        assertNotNull("the translated notification was not posted", posted)
        val extras = posted!!.notification.extras
        assertEquals("Marie", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Are you coming tomorrow?", extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Translated from Français", extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(NotificationSnapshotReader.read(posted).isOwnTranslation) // so the listener never translates it again

        presenter.remove(key)
        Thread.sleep(500)
        assertTrue(manager.activeNotifications.none { it.tag == "alterlingua-translation" && it.id == key.hashCode() })
    }
}
