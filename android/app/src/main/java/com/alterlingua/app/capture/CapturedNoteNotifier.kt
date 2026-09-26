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
 * The quiet "Voice note captured" notification. It says only that a voice note is ready, never any words of it, and tapping it
 * opens the note. It is low importance on purpose: no pop-up banner and no sound. The user can switch it off in Settings
 * (and Android lets them silence its channel), so it can be muted completely.
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
        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setContentTitle(context.getString(R.string.capture_ready_title))
            .setContentText(context.getString(R.string.capture_ready_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, STATUS_CHANNEL)
                    .setSmallIcon(R.drawable.ic_tool_voicenote)
                    .setContentTitle(context.getString(R.string.notif_public_title))
                    .setContentText(context.getString(R.string.capture_public_text))
                    .build(),
            )
            .build()
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        /** The channel for every quiet message about voice-note listening. */
        const val STATUS_CHANNEL = "voice_capture_status"
        private const val RESULT_ID = 5110
        private const val SLOTS = 40

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, context.getString(R.string.capture_channel_status), NotificationManager.IMPORTANCE_LOW))
        }
    }
}
