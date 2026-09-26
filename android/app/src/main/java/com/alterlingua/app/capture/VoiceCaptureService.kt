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
import com.alterlingua.app.AlterLinguaApplication
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Records voice notes that chat apps play, so they can be transcribed and translated (Android 10+).
 *
 * The user asked for this (keyboard button, onboarding or Settings) and approved Android's screen-capture prompt, which
 * Android requires for every listening session and which shows a status-bar indicator for as long as it lasts. The service:
 * - captures only sound Android allows other apps to capture (media, game, unknown usages), and only from the chat apps
 *   the user named (matched by Android user id);
 * - in a single capture (keyboard button) records one voice note and stops; in a listening session (`keepListening`) it
 *   records every voice note those apps play, one after another, with no further taps, until the user taps Stop, the
 *   user ends it from Android's indicator, the screen locks (Android ends the session then) or the process is killed;
 * - keeps each recording only as a private temporary file in the app's cache (the newest few, for at most an hour), and
 *   sends nothing anywhere: the result screen uploads a recording only when the user opens it;
 * - never logs or describes the sound; its notifications say only that a voice note was captured.
 *
 * Android does not let the approval be saved and reused, so when a session ends by itself the service posts a notification
 * that starts a new one with a single tap and a single approval.
 */
class VoiceCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val stopRequested = AtomicBoolean(false)
    private var noteCounter = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Stop tapped in the notification: finish what has been heard so far, and end.
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
        val uids = intent.getIntArrayExtra(EXTRA_UIDS) ?: IntArray(0)
        val labels = intent.getStringArrayExtra(EXTRA_LABELS)?.toList().orEmpty()
        val keepListening = intent.getBooleanExtra(EXTRA_KEEP_LISTENING, false)

        createChannels()
        try {
            startForeground(LISTENING_ID, listeningNotification(labels, keepListening), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        stopRequested.set(false)
        VoiceCaptureState.setListening(true)
        job = scope.launch { capture(resultCode, data, uids, labels, keepListening) }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun capture(resultCode: Int, data: Intent, uids: IntArray, labels: List<String>, keepListening: Boolean) {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            val obtained: MediaProjection? = try {
                getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            } catch (_: RuntimeException) {
                null
            }
            val projection = obtained ?: return
            val ended = AtomicBoolean(false)
            // Android 14+ requires a callback before the projection is used; it also tells us the session ended.
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ended.set(true)
                }
            }, Handler(Looper.getMainLooper()))

            var record: AudioRecord? = null
            try {
                // Only these usages can ever be captured by another app; the user ids limit it to the chosen chat apps.
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .apply { uids.filter { it > 0 }.forEach { addMatchingUid(it) } }
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
                var finished = false
                while (!finished && scope.isActive && !stopRequested.get() && !ended.get()) {
                    // A listening session waits as long as it takes; a single capture gives up after a while.
                    val recorder = if (keepListening) VoiceNoteRecorder(giveUpMillis = Int.MAX_VALUE) else VoiceNoteRecorder()
                    var phase = RecorderPhase.WAITING
                    while (scope.isActive && !stopRequested.get() && !ended.get() && phase != RecorderPhase.DONE && phase != RecorderPhase.GAVE_UP) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read < 0) {
                            finished = true
                            break
                        }
                        phase = recorder.add(buffer, read)
                    }
                    val samples = recorder.result()
                    if (samples != null) {
                        saveAndAnnounce(samples)
                    } else if (!keepListening) {
                        announce(null) // nothing heard
                    }
                    if (!keepListening) finished = true
                }
            } catch (_: RuntimeException) {
                // The capture could not start or broke off; the notification below (for a session) says so.
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                runCatching { projection.stop() }
            }
        } finally {
            VoiceCaptureState.setListening(false)
            val manager = getSystemService(NotificationManager::class.java)
            // A session that ended by itself (not because the user stopped it) says so, and offers a one-tap restart.
            if (keepListening && !stopRequested.get()) {
                runCatching { manager.notify(STOPPED_ID, stoppedNotification(uids, labels)) }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Writes one recording and tells the user. Returns false if it could not be written. */
    private fun saveAndAnnounce(samples: ShortArray): Boolean {
        val directory = File(cacheDir, DIRECTORY).apply { mkdirs() }
        prune(directory)
        val file = File(directory, "note-${UUID.randomUUID().toString().replace("-", "").take(16)}.wav")
        return try {
            WavFile.write(file, samples, VoiceNoteRecorder.OUTPUT_RATE)
            val address = CapturedAudioSource.addressOf(file)
            announce(address)
            translateNow(address)
            true
        } catch (_: IOException) {
            file.delete()
            false
        }
    }

    /**
     * When the user has it switched on (Settings, on by default), sends the whole recording to be transcribed and translated
     * as soon as the voice note has ended, so the result is ready on the keyboard. It is one request for the whole note, never
     * pieces of it. With it off, the recording waits and is sent only when the user opens the notification.
     */
    private fun translateNow(address: String) {
        val app = application as AlterLinguaApplication
        val automatic = runBlocking { app.userSettings.settings.first().translateCapturedNotes }
        if (!automatic) return
        // The note's processing starts on the main thread, like every screen's does.
        Handler(Looper.getMainLooper()).post { app.capturedNotes.open(address, announce = true) }
    }

    /** Keeps the newest few recordings for at most an hour, so a long session cannot fill the phone. */
    private fun prune(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        val files = directory.listFiles { file -> file.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        files.forEachIndexed { index, file -> if (index >= MAX_KEPT - 1 || file.lastModified() < cutoff) file.delete() }
    }

    private fun announce(address: String?) {
        val id = if (address == null) NOTHING_ID else RESULT_ID + (noteCounter++ % MAX_KEPT)
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(id, resultNotification(address, id)) }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(LISTENING_CHANNEL, getString(R.string.capture_channel_listening), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, getString(R.string.capture_channel_result), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun listeningNotification(labels: List<String>, keepListening: Boolean): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val names = labels.joinToString(", ")
        val text = when {
            keepListening -> getString(R.string.capture_session_text, names)
            names.isBlank() -> getString(R.string.capture_listening_text_any)
            else -> getString(R.string.capture_listening_text, names)
        }
        return NotificationCompat.Builder(this, LISTENING_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setContentTitle(getString(if (keepListening) R.string.capture_session_title else R.string.capture_listening_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.capture_stop), stop)
            .build()
    }

    /** A notification that says only whether a voice note was captured. Never any words of it. */
    private fun resultNotification(address: String?, id: Int): Notification {
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
                id, // one per recording, so each keeps its own address
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

    /** "Listening stopped": tapping it opens the capture screen ready to start again (one approval from Android). */
    private fun stoppedNotification(uids: IntArray, labels: List<String>): Notification {
        val restart = PendingIntent.getActivity(
            this,
            STOPPED_ID,
            Intent(this, VoiceCaptureActivity::class.java)
                .putExtra(EXTRA_UIDS, uids)
                .putExtra(EXTRA_LABELS, labels.toTypedArray())
                .putExtra(EXTRA_KEEP_LISTENING, true)
                .putExtra(VoiceCaptureActivity.EXTRA_START_AT_ONCE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_tool_voicenote)
            .setContentTitle(getString(R.string.capture_stopped_title))
            .setContentText(getString(R.string.capture_stopped_text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(restart)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        VoiceCaptureState.setListening(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        /** Android user ids of the chat apps to listen to (empty: any app, for a single capture). */
        const val EXTRA_UIDS = "uids"

        /** The names of those apps, for the notification only. */
        const val EXTRA_LABELS = "labels"

        /** True for a listening session (record every voice note until stopped); false for one voice note. */
        const val EXTRA_KEEP_LISTENING = "keep_listening"
        const val ACTION_STOP = "com.alterlingua.app.capture.STOP"

        /** The folder in the app's cache where captured recordings wait (see TemporaryAudioFolders). */
        const val DIRECTORY = "captured_audio"
        private const val MAX_KEPT = 5
        private const val MAX_AGE_MILLIS = 60L * 60 * 1000
        private const val LISTENING_ID = 5101
        private const val RESULT_ID = 5110 // 5110 to 5114, one per kept recording
        private const val NOTHING_ID = 5102
        private const val STOPPED_ID = 5103
        private const val LISTENING_CHANNEL = "voice_capture_listening"
        private const val RESULT_CHANNEL = "voice_capture_result"
    }
}
