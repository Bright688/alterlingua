package com.alterlingua.app.accessibility

/** A rectangle in screen pixels. Plain data (not android.graphics.Rect) so the rules that use it run in unit tests. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * One piece of text AlterLingua's accessibility reader found on a supported chat app's own screen, with where it is,
 * copied out of Android's node tree so the rules that use it can be tested without a real screen.
 */
data class ScreenNode(val text: String, val isEditable: Boolean, val bounds: Bounds) {
    /** Never printed: this may hold private text, so it cannot reach a log or a crash report. */
    override fun toString() = "ScreenNode(redacted)"
}

/** Everything read from one window: its own bounds, and the visible nodes in it. */
data class ScreenSnapshot(val screen: Bounds, val nodes: List<ScreenNode>) {
    override fun toString() = "ScreenSnapshot(redacted)"
}

/** A message-like piece of text and where it sits on the screen. */
data class ChatMessage(val text: String, val bounds: Bounds) {
    override fun toString() = "ChatMessage(redacted)"
}

/**
 * Picks out the messages worth translating from everything a chat app's screen exposes, each with its position so a
 * translation can be drawn right under it.
 *
 * This is necessarily a best-effort heuristic (CLAUDE.md section 39): unlike a notification, which an app deliberately
 * formats for a listener, a screen has no agreed structure. It uses only signals that hold across apps and survive an
 * app updating its layout, never an app's own internal view IDs:
 *  - the text box the user types into (and everything at or below it) is never a message;
 *  - the top of the window (title bar, contact name, status) is not part of the conversation;
 *  - clock times such as "12:34" or "9:05 PM" are not messages;
 *  - text with no letter in it (ticks, counters, emoji-only) is not worth translating.
 */
object ChatScreenExtractor {
    private const val MAX_CHARS = 2_000

    /** The top slice of the window taken by the title bar and status area. */
    private const val TOP_FRACTION = 0.11

    /** With no text box visible, the bottom slice assumed to be a composer or navigation. */
    private const val BOTTOM_FRACTION = 0.08

    private val clockTime = Regex("""^\d{1,2}[:.]\d{2}(\s?([aApP]\.?[mM]\.?))?$""")

    fun extract(snapshot: ScreenSnapshot): List<ChatMessage> {
        val screen = snapshot.screen
        if (screen.height <= 0) return emptyList()
        val composerTop = snapshot.nodes.filter { it.isEditable }.minOfOrNull { it.bounds.top }
        val minTop = screen.top + (screen.height * TOP_FRACTION).toInt()
        val maxBottom = composerTop ?: (screen.bottom - (screen.height * BOTTOM_FRACTION).toInt())
        return snapshot.nodes
            .asSequence()
            .filterNot { it.isEditable }
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() && it.text.length <= MAX_CHARS && it.text.any { c -> c.isLetter() } }
            .filterNot { clockTime.matches(it.text) }
            .filter { it.bounds.height > 0 && it.bounds.width > 0 }
            .filter { it.bounds.top >= minTop && it.bounds.bottom <= maxBottom }
            .map { ChatMessage(it.text, it.bounds) }
            .distinct()
            .sortedBy { it.bounds.top }
            .toList()
    }
}
