package com.alterlingua.app.capturetest

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. A feasibility test: can AlterLingua capture the sound a chat app plays when a voice note is played?
 *
 * It records nothing to disk, sends nothing anywhere and logs nothing. It only measures how loud the captured sound was
 * each second, and asks Android which kinds of playback were active, so that a silent result can be explained. See
 * docs/build-log.md for why this exists and what it can and cannot tell us.
 *
 * Needs the user's approval of Android's screen-capture prompt for every run (Android 14+), a foreground service of type
 * mediaProjection, and the microphone permission.
 */
class CaptureTestService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        if (data == null || resultCode != Activity.RESULT_OK || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            CaptureTestState.ui.value = CaptureUiState.Failed("The screen-capture approval was not given.")
            stopSelf()
            return START_NOT_STICKY
        }
        val uid = intent.getIntExtra(EXTRA_UID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS)

        createChannel()
        val notification = notification(label, seconds)
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (error: RuntimeException) {
            CaptureTestState.ui.value = CaptureUiState.Failed(error.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }
        CaptureTestState.stopRequested.set(false)
        job?.cancel()
        job = scope.launch { capture(resultCode, data, uid, label, seconds) }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun capture(resultCode: Int, data: Intent, uid: Int, label: String, listenSeconds: Int) {
        val audioManager = getSystemService(AudioManager::class.java)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureTestState.ui.value = CaptureUiState.Failed("The microphone permission is missing (needed to read captured audio).")
            finishService()
            return
        }
        val obtained: MediaProjection? = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (error: RuntimeException) {
            fail(error)
            return
        }
        val projection = obtained ?: run {
            CaptureTestState.ui.value = CaptureUiState.Failed("Android gave no projection.")
            finishService()
            return
        }
        val ended = AtomicBoolean(false)
        // Android 14+ requires a callback to be registered before the projection is used; it also tells us when the user ends it.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                ended.set(true)
            }
        }, Handler(Looper.getMainLooper()))

        var record: AudioRecord? = null
        val usagesSeen = linkedSetOf<Int>()
        val policiesSeen = linkedSetOf<Int>()
        val meter = CaptureMeter(SAMPLE_RATE, CHANNELS)
        try {
            // Only these three usages can ever be captured by another app; the UID limits it to the one chat app chosen.
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .apply { if (uid > 0) addMatchingUid(uid) }
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, SAMPLE_RATE * 4))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord was not initialised" }
            record.startRecording()

            val buffer = ShortArray(SAMPLE_RATE / 5 * CHANNELS) // 200 ms
            val end = SystemClock.elapsedRealtime() + listenSeconds * 1000L
            var lastPublish = 0L
            while (scope.isActive && SystemClock.elapsedRealtime() < end && !CaptureTestState.stopRequested.get() && !ended.get()) {
                val read = record.read(buffer, 0, buffer.size)
                check(read >= 0) { "read failed ($read)" }
                meter.add(buffer, read)
                val now = SystemClock.elapsedRealtime()
                if (now - lastPublish >= 400) {
                    lastPublish = now
                    observePlayback(audioManager, usagesSeen, policiesSeen)
                    val left = ((end - now) / 1000L).toInt().coerceAtLeast(0)
                    CaptureTestState.ui.value = CaptureUiState.Listening(label, left, meter.livePeakDb, usagesSeen.toSet())
                }
            }
            val result = CaptureResult(label, meter.summary(), usagesSeen.toSet(), policiesSeen.toSet(), ended.get() && !CaptureTestState.stopRequested.get())
            CaptureTestState.history.value = listOf(result) + CaptureTestState.history.value
            CaptureTestState.ui.value = CaptureUiState.Idle
        } catch (error: RuntimeException) {
            CaptureTestState.ui.value = CaptureUiState.Failed(error.javaClass.simpleName + (error.message?.let { ": ${safe(it)}" } ?: ""))
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { projection.stop() }
            finishService()
        }
    }

    /** Notes which kinds of playback Android says are active right now. Kinds only: never which app, never any content. */
    private fun observePlayback(audioManager: AudioManager, usages: MutableSet<Int>, policies: MutableSet<Int>) {
        val playing = runCatching { audioManager.activePlaybackConfigurations }.getOrNull() ?: return
        for (configuration in playing) {
            val attributes = runCatching { configuration.audioAttributes }.getOrNull() ?: continue
            usages += attributes.usage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) policies += attributes.allowedCapturePolicy
        }
    }

    private fun fail(error: RuntimeException) {
        CaptureTestState.ui.value = CaptureUiState.Failed(error.javaClass.simpleName)
        finishService()
    }

    private fun finishService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** An exception message is kept only if it looks like a short technical note, and is cut short. */
    private fun safe(message: String): String = message.take(80).filter { it.isLetterOrDigit() || it in " ._:-()" }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Capture test", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(label: String, seconds: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Capture test")
            .setContentText("Listening to $label for up to $seconds s. Play a voice note in that app now.")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_UID = "uid"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SECONDS = "seconds"
        const val DEFAULT_SECONDS = 45
        private const val SAMPLE_RATE = 44_100
        private const val CHANNELS = 2
        private const val NOTIFICATION_ID = 4711
        private const val CHANNEL_ID = "capture_test"
    }
}
