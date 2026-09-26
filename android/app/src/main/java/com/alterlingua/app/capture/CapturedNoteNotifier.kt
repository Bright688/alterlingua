package com.alterlingua.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alterlingua.app.R
import kotlin.math.abs

/**
 * The "Voice note captured" notification. It says only that a voice note is ready, never any words of it, and tapping it
 * opens the note. When the user has it switched on in Settings it is a normal notification, with sound and a pop-up; when it
 * is switched off nothing is posted at all. (Android also lets the user soften its channel, "Voice note captured", in the
 * phone's notification settings.)
 */
class CapturedNoteNotifier(private val context: Context) {

    fun notifyReady(address: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context)
        val id = RESULT_ID + abs(address.hashCode() % SLOTS)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, VoiceCaptureResultActivity::class.java)
                .putExtra(VoiceCaptureResultActivity.EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, READY_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setContentTitle(context.getString(R.string.capture_ready_title))
            .setContentText(context.getString(R.string.capture_ready_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, READY_CHANNEL)
                    .setSmallIcon(R.drawable.ic_tool_voicenote)
                    .setContentTitle(context.getString(R.string.notif_public_title))
                    .setContentText(context.getString(R.string.capture_public_text))
                    .build(),
            )
            .build()
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        /** The channel for the rare quiet messages about listening ("listening stopped", "no voice note heard"). */
        const val STATUS_CHANNEL = "voice_capture_status"

        /** The channel of the "Voice note captured" notification: normal importance, with sound and a pop-up. */
        const val READY_CHANNEL = "voice_capture_ready"
        private const val RESULT_ID = 5110
        private const val SLOTS = 40

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, context.getString(R.string.capture_channel_status), NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(READY_CHANNEL, context.getString(R.string.capture_ready_title), NotificationManager.IMPORTANCE_HIGH))
        }
    }
}
