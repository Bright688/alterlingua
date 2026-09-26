package com.alterlingua.app.capturetest

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alterlingua.app.ui.theme.AlterLinguaTheme

/** A chat app that could be tested: found on the phone by its package name, or the "any app" option. */
data class Candidate(val label: String, val installed: Boolean, val uid: Int, val targetSdk: Int) {
    val isAnyApp: Boolean get() = uid <= 0 && installed
}

private val KNOWN_APPS = listOf(
    "WhatsApp" to "com.whatsapp",
    "WhatsApp Business" to "com.whatsapp.w4b",
    "Telegram" to "org.telegram.messenger",
    "Messenger" to "com.facebook.orca",
    "Signal" to "org.thoughtcrime.securesms",
    "Instagram" to "com.instagram.android",
    "Viber" to "com.viber.voip",
    "Google Messages" to "com.google.android.apps.messaging",
    "LINE" to "jp.naver.line.android",
    "Discord" to "com.discord",
)

@Suppress("DEPRECATION")
private fun loadCandidates(packageManager: PackageManager): List<Candidate> =
    KNOWN_APPS.map { (label, packageName) ->
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            Candidate(label, installed = true, uid = info.uid, targetSdk = info.targetSdkVersion)
        } catch (_: PackageManager.NameNotFoundException) {
            Candidate(label, installed = false, uid = -1, targetSdk = 0)
        }
    } + Candidate("Any app (no filter: everything capturable)", installed = true, uid = -1, targetSdk = 0)

/**
 * DEBUG BUILDS ONLY, opened from its own launcher icon ("AlterLingua capture test"). A test bench for one question: can
 * AlterLingua capture the sound of a voice note played inside a chat app? It leaves the voice-note Share feature alone.
 * Nothing is recorded to disk or sent anywhere; only how loud the captured sound was is shown.
 */
class CaptureTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlterLinguaTheme { CaptureTestScreen() } }
    }
}

@Composable
private fun CaptureTestScreen() {
    val context = LocalContext.current
    val candidates = remember { loadCandidates(context.packageManager) }
    var selected by remember { mutableStateOf(candidates.firstOrNull { it.installed && !it.isAnyApp } ?: candidates.last()) }
    val ui by CaptureTestState.ui.collectAsState()
    val history by CaptureTestState.history.collectAsState()

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(context, CaptureTestService::class.java)
                .putExtra(CaptureTestService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(CaptureTestService.EXTRA_DATA, data)
                .putExtra(CaptureTestService.EXTRA_UID, selected.uid)
                .putExtra(CaptureTestService.EXTRA_LABEL, selected.label)
                .putExtra(CaptureTestService.EXTRA_SECONDS, CaptureTestService.DEFAULT_SECONDS)
            CaptureTestState.ui.value = CaptureUiState.Listening(selected.label, CaptureTestService.DEFAULT_SECONDS, -96.0, emptySet())
            ContextCompat.startForegroundService(context, intent)
        } else {
            CaptureTestState.ui.value = CaptureUiState.Failed("The screen-capture prompt was not approved.")
        }
    }
    val microphoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } else {
            CaptureTestState.ui.value = CaptureUiState.Failed("The microphone permission was refused (needed to read captured audio).")
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            CaptureTestState.ui.value = CaptureUiState.Failed("Capturing another app's audio needs Android 10 or newer.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } else {
            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voice-note capture test", style = MaterialTheme.typography.headlineSmall)
            Text("Debug build only. Answers one question: does a chat app let AlterLingua capture the sound of a voice note while it plays?", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Records nothing to disk, sends nothing, logs nothing. Only how loud the captured sound was is shown. " +
                    "Android will show its screen-capture prompt: it lets AlterLingua capture what is played on the device (this test limits it to the app you choose).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("1. Choose the chat app", style = MaterialTheme.typography.titleMedium)
            candidates.forEach { candidate ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = candidate.installed) { selected = candidate },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == candidate, onClick = { selected = candidate }, enabled = candidate.installed)
                    Column {
                        Text(candidate.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                !candidate.installed -> "not installed"
                                candidate.isAnyApp -> "records every capturable sound on the phone"
                                else -> "installed; built for Android API ${candidate.targetSdk}" + if (candidate.targetSdk >= 29) " (captures allowed unless the app opts out)" else " (older apps must opt in)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("2. Start, approve Android's prompt, then play a voice note", style = MaterialTheme.typography.titleMedium)
            Text(
                "After you tap Start and approve the prompt, open ${selected.label}, and play a voice note from beginning to end. " +
                    "The test listens for ${CaptureTestService.DEFAULT_SECONDS} seconds (or tap Stop). Come back here to read the result.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (val state = ui) {
                is CaptureUiState.Listening -> {
                    Text("Listening to ${state.appLabel}… ${state.secondsLeft} s left, level ${"%.0f".format(state.livePeakDb)} dBFS", style = MaterialTheme.typography.titleMedium)
                    if (state.usagesSeen.isNotEmpty()) Text("Android reports playing: ${state.usagesSeen.joinToString { CaptureExplainer.usageName(it) }}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { CaptureTestState.stopRequested.set(true) }, modifier = Modifier.fillMaxWidth()) { Text("Stop now") }
                }
                is CaptureUiState.Failed -> {
                    Text("Could not run: ${state.reason}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = ::start, enabled = selected.installed, modifier = Modifier.fillMaxWidth()) { Text("Start test") }
                }
                CaptureUiState.Idle -> Button(onClick = ::start, enabled = selected.installed, modifier = Modifier.fillMaxWidth()) { Text("Start test") }
            }

            HorizontalDivider()
            Text("Results", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) Text("No results yet.", style = MaterialTheme.typography.bodyMedium)
            history.forEach { result ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${result.appLabel}: ${result.summary.verdict}", style = MaterialTheme.typography.titleSmall)
                    Text(CaptureExplainer.explain(result), style = MaterialTheme.typography.bodyMedium)
                    if (result.usagesSeen.isNotEmpty()) {
                        Text("Playback Android reported: ${result.usagesSeen.joinToString { CaptureExplainer.usageName(it) + if (CaptureExplainer.isCapturable(it)) " (capturable)" else " (not capturable)" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (result.policiesSeen.isNotEmpty()) {
                        Text("Capture policy seen: ${result.policiesSeen.joinToString { CaptureExplainer.policyName(it) }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
