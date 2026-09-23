package com.alterlingua.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages

/**
 * Posts AlterLingua's own translated notification, for example "Marie / Are you coming tomorrow? / Translated from
 * Français". It is a separate notification: WhatsApp's own notification and chat are not touched or rewritten.
 *
 * Tapping it runs WhatsApp's own "open this chat" action, exactly as WhatsApp's notification offers it. Nothing else
 * of WhatsApp's is used (no reply actions, no private storage, no unofficial API).
 */
class TranslatedNotificationPresenter(
    context: Context,
    /** The user's app language right now, or null for the phone's language. AlterLingua's own notification follows it. */
    private val appLanguage: () -> com.alterlingua.app.learning.Language? = { null },
) : TranslationPresenter {

    private val appContext = context.applicationContext
    private val context: Context get() = com.alterlingua.app.localization.AppLanguage.wrap(appContext, appLanguage())
    private val manager = NotificationManagerCompat.from(appContext)

    override fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel()
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission") // checked in canPost() by areNotificationsEnabled()
    override fun show(conversation: TranslatedConversation) {
        if (!canPost()) return
        val latest = conversation.lines.last()
        val from = Languages.fromCode(latest.sourceLanguage)?.displayName ?: latest.sourceLanguage.uppercase()

        fun line(sender: String, text: String) = if (conversation.isGroup && sender.isNotBlank()) "$sender: $text" else text

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tool_translate)
            .setContentTitle(conversation.title)
            .setContentText(line(latest.sender, latest.text))
            .setSubText(context.getString(R.string.notif_translated_from, from))
            .setStyle(NotificationCompat.BigTextStyle().bigText(conversation.lines.joinToString("\n") { line(it.sender, it.text) }))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            // The lock screen shows only a generic line unless the user has chosen to show content there.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tool_translate)
                    .setContentTitle(context.getString(R.string.notif_public_title))
                    .setContentText(context.getString(R.string.notif_public_text))
                    .build(),
            )
            .addExtras(Bundle().apply { putBoolean(NotificationSnapshotReader.EXTRA_OWN_TRANSLATION, true) })
            .apply { (conversation.openIntent as? PendingIntent)?.let { setContentIntent(it) } }
            .build()
        manager.notify(TAG, idFor(conversation.key), notification)
    }

    override fun remove(key: String) {
        manager.cancel(TAG, idFor(key))
    }

    private fun ensureChannel() {
        // Quiet on purpose: WhatsApp already alerts; this one is for reading the translation.
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = context.getString(R.string.notif_channel_description)
        manager.createNotificationChannel(channel)
    }

    private fun idFor(key: String) = key.hashCode()

    private companion object {
        const val CHANNEL_ID = "translated_messages"
        const val TAG = "alterlingua-translation"
        const val GROUP = "alterlingua-translations"
    }
}
