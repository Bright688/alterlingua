package com.alterlingua.app.capture

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A chat app installed on this phone that AlterLingua knows how to listen to. [uid] is Android's user id for it. */
data class ChatApp(val packageName: String, val label: String, val uid: Int)

/**
 * The chat apps offered for voice-note capture. A fixed list, so that AlterLingua asks Android about these packages only
 * (see the `<queries>` block in the manifest) instead of needing the permission to see every app on the phone.
 */
object KnownChatApps {
    val packages: List<String> = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.facebook.orca",
        "org.thoughtcrime.securesms",
        "com.instagram.android",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.discord",
    )
}

/** Which of the known chat apps are installed. An interface so that tests do not need Android. */
fun interface ChatAppSource {
    fun installed(): List<ChatApp>
}

class PackageManagerChatApps(private val packageManager: PackageManager) : ChatAppSource {
    override fun installed(): List<ChatApp> = KnownChatApps.packages.mapNotNull { name ->
        try {
            val info = packageManager.getApplicationInfo(name, 0)
            ChatApp(name, packageManager.getApplicationLabel(info).toString(), info.uid)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

/** What a listening session is asked to do: which apps to capture voice notes from, and how. */
data class CaptureRequest(val apps: List<ChatApp>, val keepListening: Boolean) {
    /** No apps means "any app" for a single capture; a session that keeps listening always names its apps. */
    val isValid: Boolean get() = !keepListening || apps.isNotEmpty()

    fun labels(): String = apps.joinToString(", ") { it.label }

    companion object {
        /** Only the chosen apps that are installed, in the order the phone lists them. */
        fun forChosen(chosen: Set<String>, installed: List<ChatApp>) = CaptureRequest(installed.filter { it.packageName in chosen }, keepListening = true)
    }
}

/** Builds the intent that opens the "capture" screen for a request, so the keyboard, onboarding and Settings share one way. */
object VoiceCaptureLauncher {
    fun intent(context: Context, request: CaptureRequest, startAtOnce: Boolean = false): Intent =
        Intent(context, VoiceCaptureActivity::class.java)
            .putExtra(VoiceCaptureService.EXTRA_UIDS, request.apps.map { it.uid }.toIntArray())
            .putExtra(VoiceCaptureService.EXTRA_LABELS, request.apps.map { it.label }.toTypedArray())
            .putExtra(VoiceCaptureService.EXTRA_KEEP_LISTENING, request.keepListening)
            .putExtra(VoiceCaptureActivity.EXTRA_START_AT_ONCE, startAtOnce)
}

/** Whether a listening session is running right now. In memory only: it is false again whenever the app's process is. */
object VoiceCaptureState {
    private val listeningNow = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningNow.asStateFlow()

    fun setListening(value: Boolean) {
        listeningNow.value = value
    }
}
