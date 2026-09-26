package com.alterlingua.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alterlingua.app.AlterLinguaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. Fires the "Voice note captured" notification exactly as the app does when a voice note has been
 * translated, including the Settings switch (on posts it, off does not), so the notification can be checked with `adb` without
 * playing a voice note:
 *
 *   adb shell am broadcast -n com.alterlingua.app/.capture.DebugCapturedNoteReceiver -a com.alterlingua.app.DEBUG_CAPTURED_NOTE
 *
 * It sends no audio anywhere; the notification opens a note that does not exist, which the result screen reports as unreadable.
 */
class DebugCapturedNoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AlterLinguaApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CapturedNoteAlerts(app.userSettings) { address -> CapturedNoteNotifier(app).notifyReady(address) }
                    .onTranslated(CapturedAudioSource.PREFIX + "debug-test.wav")
            } finally {
                pending.finish()
            }
        }
    }
}
