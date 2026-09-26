package com.alterlingua.app.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alterlingua.app.R
import com.alterlingua.app.localization.LocalizedActivity
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/**
 * "Capture a voice note" and "Listen for voice notes": opened by the keyboard's voice-note button (one voice note), and by
 * onboarding, Settings and the "listening stopped" notification (a listening session). It explains what will happen, and
 * only when the user taps Start (or, from the "listening stopped" notification, at once, because that tap is the user's
 * request) asks for what Android requires: notifications (to say when a voice note is ready), the microphone permission
 * (which Android requires to read captured sound, though the microphone itself is not used) and Android's own
 * screen-capture approval, which Android requires for every session and which shows a status-bar indicator while it lasts.
 * It then starts [VoiceCaptureService] and closes, so the chat app is in front again.
 */
class VoiceCaptureActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uids = intent.getIntArrayExtra(VoiceCaptureService.EXTRA_UIDS) ?: IntArray(0)
        val labels = intent.getStringArrayExtra(VoiceCaptureService.EXTRA_LABELS)?.toList().orEmpty()
        val keepListening = intent.getBooleanExtra(VoiceCaptureService.EXTRA_KEEP_LISTENING, false)
        val startAtOnce = intent.getBooleanExtra(EXTRA_START_AT_ONCE, false)
        setContent {
            AlterLinguaTheme {
                CaptureScreen(
                    uids = uids,
                    labels = labels,
                    keepListening = keepListening,
                    startAtOnce = startAtOnce && savedInstanceState == null,
                    supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        /** Start asking for permissions as soon as the screen opens (used by the "listening stopped" notification). */
        const val EXTRA_START_AT_ONCE = "start_at_once"
    }
}

@Composable
private fun CaptureScreen(uids: IntArray, labels: List<String>, keepListening: Boolean, startAtOnce: Boolean, supported: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<Int?>(null) }
    val listening by VoiceCaptureState.listening.collectAsStateWithLifecycle()
    val names = labels.joinToString(", ")

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val start = Intent(context, VoiceCaptureService::class.java)
                .putExtra(VoiceCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(VoiceCaptureService.EXTRA_DATA, data)
                .putExtra(VoiceCaptureService.EXTRA_UIDS, uids)
                .putExtra(VoiceCaptureService.EXTRA_LABELS, labels.toTypedArray())
                .putExtra(VoiceCaptureService.EXTRA_KEEP_LISTENING, keepListening)
            ContextCompat.startForegroundService(context, start)
            onClose()
        } else {
            problem = R.string.capture_problem_not_approved
        }
    }
    fun askForProjection() = projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askForProjection() else problem = R.string.capture_problem_microphone
    }
    fun askForMicrophone() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            askForProjection()
        } else {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askForMicrophone() else problem = R.string.capture_problem_notifications
    }

    fun start() {
        problem = null
        val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotifications) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else askForMicrophone()
    }
    LaunchedEffect(startAtOnce, supported, listening) {
        if (startAtOnce && supported && !listening) start()
    }

    Surface(modifier = Modifier.fillMaxSize().testTag("screen_voice_capture"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (keepListening) R.string.capture_session_title else R.string.capture_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    keepListening -> stringResource(R.string.capture_session_intro, names)
                    names.isBlank() -> stringResource(R.string.capture_intro_any)
                    else -> stringResource(R.string.capture_intro, names)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AlterLinguaCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.capture_step_1), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(if (keepListening) R.string.capture_session_step_2 else R.string.capture_step_2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(if (keepListening) R.string.capture_session_step_3 else R.string.capture_step_3),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                stringResource(R.string.capture_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("voice_capture_privacy"),
            )
            if (!supported) {
                Text(stringResource(R.string.capture_problem_old_android), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (listening) {
                Text(stringResource(R.string.capture_already_listening), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("voice_capture_already"))
            }
            problem?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("voice_capture_problem"))
            }
            if (supported && !listening) {
                Button(onClick = ::start, modifier = Modifier.fillMaxWidth().testTag("voice_capture_start")) { Text(stringResource(R.string.capture_start)) }
            }
            if (listening) {
                Button(
                    onClick = {
                        context.startService(Intent(context, VoiceCaptureService::class.java).setAction(VoiceCaptureService.ACTION_STOP))
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("voice_capture_stop"),
                ) { Text(stringResource(R.string.capture_stop)) }
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("voice_capture_cancel")) { Text(stringResource(R.string.voice_cancel)) }
        }
    }
}
