package com.alterlingua.app.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Copies out of a notification only what Android legitimately exposes to a notification listener: its title, text,
 * big text, message list (for the messaging style) and flags. It does not look at anything else, and it is only ever
 * given notifications from an accepted source (WhatsApp).
 */
object NotificationSnapshotReader {

    /** Set on the translated notifications AlterLingua posts, so it never translates its own. */
    const val EXTRA_OWN_TRANSLATION = "com.alterlingua.app.extra.OWN_TRANSLATION"

    fun read(sbn: StatusBarNotification): NotificationSnapshot = read(sbn.packageName, sbn.key, sbn.notification, sbn.postTime)

    fun read(packageName: String, key: String, notification: Notification, postTime: Long): NotificationSnapshot {
        val extras = notification.extras
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: RuntimeException) {
            null // a malformed message list: fall back to the plain title and text
        }
        return NotificationSnapshot(
            packageName = packageName,
            key = key,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            category = notification.category,
            visibility = notification.visibility,
            postTime = if (notification.`when` > 0) notification.`when` else postTime,
            isOwnTranslation = extras?.getBoolean(EXTRA_OWN_TRANSLATION, false) == true,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation ?: false,
            messages = style?.messages.orEmpty().map { SnapshotMessage(it.person?.name?.toString(), it.text?.toString(), it.timestamp) },
            openIntent = notification.contentIntent,
        )
    }
}
