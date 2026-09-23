package com.alterlingua.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.alterlingua.app.R

/**
 * DEBUG BUILDS ONLY. Posts a notification shaped like a WhatsApp message (a messaging-style notification with a
 * sender and a text), so the incoming translation can be tried with `adb` and no second phone:
 *
 *   adb shell am broadcast -n com.alterlingua.app/.notifications.DebugTestMessageReceiver \
 *     -a com.alterlingua.app.DEBUG_TEST_MESSAGE --es sender "Marie" --es text "Tu viens demain ?"
 *
 * Optional: --es group "Family" makes it a group chat. Debug builds also let the listener read AlterLingua's own
 * notifications (see IncomingSources), which is what makes this work.
 */
class DebugTestMessageReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val text = intent.getStringExtra("text") ?: return
        val group = intent.getStringExtra("group")

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Test messages (debug)", NotificationManager.IMPORTANCE_DEFAULT))

        val me = Person.Builder().setName("Me").build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, System.currentTimeMillis(), Person.Builder().setName(sender).build())
        if (group != null) style.setConversationTitle(group).setGroupConversation(true)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tool_translate)
            .setContentTitle(group ?: sender)
            .setContentText(if (group != null) "$sender: $text" else text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        // One notification per conversation, like WhatsApp: the same chat replaces the previous one.
        manager.notify("debug-test", (group ?: sender).hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "debug_test_messages"
    }
}
