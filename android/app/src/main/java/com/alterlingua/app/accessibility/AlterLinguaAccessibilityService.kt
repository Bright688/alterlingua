package com.alterlingua.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.notifications.IncomingSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads the text Android's accessibility tree exposes for a supported chat app's own screen while that app is in the
 * foreground, so a translation can appear live without waiting for a notification (CLAUDE.md section 39).
 *
 * Scope is minimised at the OS level, not just in code: `res/xml/accessibility_service_config.xml`'s
 * `android:packageNames` means Android itself never delivers an event from any app outside that list to this
 * service — the [IncomingSources.accepts] check below is a second, redundant guard, not the only one. This is not an
 * accessibility tool (it does not declare `isAccessibilityTool`); it is a translation feature that happens to use
 * this API, off by default, and inert until the user separately enables both the app's own setting and Android's
 * Accessibility permission for this service.
 *
 * It only ever reads text. It never taps, scrolls, fills in or otherwise acts on anything in the app it is reading
 * (CLAUDE.md section 37), and nothing it reads is saved — only the translation result is shown, exactly as
 * [LiveChatTranslator] and [SeenMessages][com.alterlingua.app.notifications.SeenMessages] already behave for the
 * notification-based translator.
 */
class AlterLinguaAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val liveChatTranslator get() = (application as AlterLinguaApplication).liveChatTranslator

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = e.packageName?.toString() ?: return
        if (!IncomingSources.accepts(packageName)) return // belt and braces: the config's packageNames already restricts delivery to this point
        val root = rootInActiveWindow ?: return
        val texts = ChatScreenExtractor.extract(AccessibilityTreeReader.read(root))
        if (texts.isNotEmpty()) scope.launch { liveChatTranslator.handle(packageName, texts) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
