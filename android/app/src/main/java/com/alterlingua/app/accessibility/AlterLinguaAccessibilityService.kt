package com.alterlingua.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.notifications.IncomingSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads the messages a supported chat app shows on screen while it is in the foreground and draws each one's
 * translation directly under it (CLAUDE.md section 39).
 *
 * Scope is minimised at the OS level, not just in code: `res/xml/accessibility_service_config.xml`'s
 * `android:packageNames` means Android itself never delivers an event from any app outside that list to this
 * service; the [IncomingSources.accepts] checks below are a second, redundant guard. This is not an accessibility tool
 * (it does not declare `isAccessibilityTool`); it is a translation feature that happens to use this API, off by
 * default, and inert until the user separately enables both the app's own setting and Android's Accessibility
 * permission for this service.
 *
 * It only ever reads text and positions, and draws a caption layer that cannot be touched. It never taps, scrolls,
 * fills in or otherwise acts on anything in the app it is reading (CLAUDE.md section 37). Nothing it reads is saved:
 * the last reading and [LiveChatTranslator]'s answers live in this process's memory only, and are dropped when the
 * service stops.
 *
 * Flow: an event pokes [ReadScheduler] (a flood of events becomes a few readings; while the screen is scrolling the
 * reading waits until it settles, and the captions are hidden meanwhile because their positions are out of date) ->
 * the visible tree is read -> [ChatScreenExtractor] keeps the messages with their positions -> captions already known
 * are drawn at once -> [LiveChatTranslator] translates the rest in the background, and each arrival is drawn as it lands.
 */
class AlterLinguaAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translator get() = (application as AlterLinguaApplication).liveChatTranslator
    private val status get() = (application as AlterLinguaApplication).liveChatStatus
    private val reads = ReadScheduler(scope) { readScreen() }

    private var overlay: CaptionOverlay? = null
    private var conversation = Conversation(emptyList(), Bounds(0, 0, 0, 0))
    private var watch: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = CaptionOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val packageName = e.packageName?.toString() ?: return
        if (!IncomingSources.accepts(packageName)) return // belt and braces: the config's packageNames already restricts delivery to this point
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                clearCaptions() // the layout is moving or has changed: what was drawn is now in the wrong place
                reads.pokeWhenSettled()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> reads.pokeAtMostEvery()
        }
    }

    /** Reads the screen in front, draws what is already translated, and starts translating what is not. */
    private suspend fun readScreen() {
        val root = rootInActiveWindow
        if (root == null || !isSupported(root.packageName)) {
            status.skipped()
            clearCaptions()
            return
        }
        if (!translator.isEnabled()) {
            clearCaptions()
            return
        }
        val snapshot = AccessibilityTreeReader.read(root)
        @Suppress("DEPRECATION")
        root.recycle()
        conversation = ChatScreenExtractor.extract(snapshot)
        val shown = render()
        // Numbers only, for the status line in Settings: tells a wrong screen reading apart from a wrong drawing.
        status.read(snapshot.nodes.size, snapshot.nodes.count { it.isEditable }, conversation.messages.size, shown)
        val texts = conversation.messages.map { it.text }
        if (texts.isNotEmpty()) {
            scope.launch(Dispatchers.Default) { translator.prepare(texts) { scope.launch { status.captionsDrawn(render()) } } }
        }
    }

    /**
     * Draws a caption for every message whose translation is known, where it covers nothing (see [CaptionPlacer]);
     * removes the overlay when there is nothing to show. Returns how many captions are now drawn.
     */
    private fun render(): Int {
        val current = overlay ?: return 0
        if (conversation.messages.isEmpty()) {
            current.hide()
            return 0
        }
        val captions = CaptionPlacer.place(conversation, translator::captionFor, current.metrics.sizes)
        current.show(captions)
        if (captions.isNotEmpty()) watchForeground()
        return captions.size
    }

    private fun clearCaptions() {
        conversation = Conversation(emptyList(), conversation.area)
        overlay?.hide()
    }

    private fun isSupported(packageName: CharSequence?): Boolean = packageName != null && IncomingSources.accepts(packageName.toString())

    /**
     * While captions are showing, checks every so often that the chat app is still the one in front. Android sends this
     * service no event when the user leaves for another app (it only hears from the chat apps), so without this the
     * captions would stay drawn on top of whatever the user switched to.
     */
    private fun watchForeground() {
        if (watch?.isActive == true) return
        watch = scope.launch {
            while (isActive && overlay?.isShowing == true) {
                delay(800)
                val root = rootInActiveWindow
                val inFront = root != null && isSupported(root.packageName)
                @Suppress("DEPRECATION")
                root?.recycle()
                if (!inFront || !translator.isEnabled()) {
                    clearCaptions()
                    return@launch
                }
            }
        }
    }

    override fun onInterrupt() {
        clearCaptions()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        reads.cancel()
        watch?.cancel()
        clearCaptions()
        translator.clear()
        scope.cancel()
    }
}
