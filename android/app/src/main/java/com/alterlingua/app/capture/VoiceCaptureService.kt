package com.alterlingua.app.capture

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.alterlingua.app.R
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Records one voice note that a chat app plays, so it can be transcribed and translated (Android 10+).
 *
 * The user asked for this (keyboard button, then Start) and approved Android's screen-capture prompt, which Android requires
 * for every run and which shows a status-bar indicator for as long as the capture lasts. The service:
 * - captures only sound Android allows other apps to capture (media, game, unknown usages), and only from the one chat app
 *   the keyboard was typing into when the button was pressed;
 * - starts when sound arrives, ends when the voice note ends (or the user taps Stop, or after 90 seconds of nothing), and
 *   then stops the capture completely;
 * - keeps the recording only as a private temporary file in the app's cache, which the result screen deletes when it reads it;
 * - never logs, sends or describes the sound; its notifications say only that a voice note was captured or not.
 *
 * Nothing is sent anywhere from here: the result screen does the transcribing and translating, when the user opens it.
 */
class VoiceCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val stopRequested = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Stop tapped in the notification: finish what has been heard so far.
            stopRequested.set(true)
            return START_NOT_STICKY
        }
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        if (data == null || resultCode != Activity.RESULT_OK || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY // one capture at a time
        val uid = intent.getIntExtra(EXTRA_UID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        createChannels()
        try {
            startForeground(LISTENING_ID, listeningNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        stopRequested.set(false)
        job = scope.launch { capture(resultCode, data, uid) }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun capture(resultCode: Int, data: Intent, uid: Int) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            finishWith(null)
            return
        }
        val projection: MediaProjection? = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (_: RuntimeException) {
            null
        }
        if (projection == null) {
            finishWith(null)
            return
        }
        val ended = AtomicBoolean(false)
        // Android 14+ requires a callback before the projection is used; it also tells us the user ended the capture.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                ended.set(true)
            }
        }, Handler(Looper.getMainLooper()))

        var record: AudioRecord? = null
        var samples: ShortArray? = null
        val recorder = VoiceNoteRecorder()
        try {
            // Only these usages can ever be captured by another app; the UID limits it to the one chat app chosen.
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .apply { if (uid > 0) addMatchingUid(uid) }
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(VoiceNoteRecorder.DEFAULT_INPUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minimum = AudioRecord.getMinBufferSize(VoiceNoteRecorder.DEFAULT_INPUT_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, VoiceNoteRecorder.DEFAULT_INPUT_RATE * 4))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.startRecording()

            val buffer = ShortArray(VoiceNoteRecorder.DEFAULT_INPUT_RATE / 5 * VoiceNoteRecorder.DEFAULT_CHANNELS) // 200 ms
            while (scope.isActive && !stopRequested.get() && !ended.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) break
                val phase = recorder.add(buffer, read)
                if (phase == RecorderPhase.DONE || phase == RecorderPhase.GAVE_UP) break
            }
            samples = recorder.result()
        } catch (_: RuntimeException) {
            samples = recorder.result()
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { projection.stop() }
        }
        finishWith(samples)
    }

    /** Writes the recording (if there is one) and tells the user, then ends the service. */
    private fun finishWith(samples: ShortArray?) {
        var address: String? = null
        if (samples != null) {
            val directory = File(cacheDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, "note-${UUID.randomUUID().toString().replace("-", "").take(16)}.wav")
            try {
                WavFile.write(file, samples, VoiceNoteRecorder.OUTPUT_RATE)
                address = CapturedAudioSource.addressOf(file)
            } catch (_: IOException) {
                file.delete()
            }
        }
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(RESULT_ID, resultNotification(address)) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(LISTENING_CHANNEL, getString(R.string.capture_channel_listening), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, getString(R.string.capture_channel_result), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun listeningNotification(label: String): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (label.isBlank()) getString(R.string.capture_listening_text_any) else getString(R.string.capture_listening_text, label)
        return NotificationCompat.Builder(this, LISTENING_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setContentTitle(getString(R.string.capture_listening_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.capture_stop), stop)
            .build()
    }

    /** A notification that says only whether a voice note was captured. Never any words of it. */
    private fun resultNotification(address: String?): Notification {
        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, RESULT_CHANNEL)
                    .setSmallIcon(R.drawable.ic_tool_voicenote)
                    .setContentTitle(getString(R.string.notif_public_title))
                    .setContentText(getString(R.string.capture_public_text))
                    .build(),
            )
        if (address != null) {
            val open = PendingIntent.getActivity(
                this,
                1,
                Intent(this, VoiceCaptureResultActivity::class.java)
                    .putExtra(VoiceCaptureResultActivity.EXTRA_ADDRESS, address)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentTitle(getString(R.string.capture_ready_title))
                .setContentText(getString(R.string.capture_ready_text))
                .setContentIntent(open)
        } else {
            builder.setContentTitle(getString(R.string.capture_nothing_title))
                .setContentText(getString(R.string.capture_nothing_text))
        }
        return builder.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_UID = "uid"
        const val EXTRA_LABEL = "label"
        const val ACTION_STOP = "com.alterlingua.app.capture.STOP"

        /** The folder in the app's cache where captured recordings wait (see TemporaryAudioFolders). */
        const val DIRECTORY = "captured_audio"
        private const val LISTENING_ID = 5101
        private const val RESULT_ID = 5102
        private const val LISTENING_CHANNEL = "voice_capture_listening"
        private const val RESULT_CHANNEL = "voice_capture_result"
    }
}
