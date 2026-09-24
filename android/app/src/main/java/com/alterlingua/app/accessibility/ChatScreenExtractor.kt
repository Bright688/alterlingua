package com.alterlingua.app.accessibility

/**
 * One piece of text AlterLingua's accessibility reader found on a supported chat app's own screen, copied out of
 * Android's node tree so the rules that use it can be tested without a real screen.
 */
data class ScreenNode(val text: String, val isEditable: Boolean) {
    /** Never printed: this may hold private text, so it cannot reach a log or a crash report. */
    override fun toString() = "ScreenNode(redacted)"
}

/**
 * Picks out text worth translating from everything a chat app's screen exposes.
 *
 * This is necessarily a best-effort heuristic (CLAUDE.md section 39): unlike a notification, which an app deliberately
 * formats for a listener with a sender, a conversation title and a category, a screen has no agreed structure at all.
 * This can only use simple, robust signals that hold across different apps and survive an app updating its own
 * layout — never the app's own internal view IDs, which differ per app and change between versions. It is expected
 * that occasional non-message text (a "Typing…" indicator, a timestamp) is offered here; [LiveChatTranslator]'s
 * de-duplication means each such stray string is only ever translated once, not repeatedly.
 */
object ChatScreenExtractor {
    private const val MAX_CHARS = 2_000

    fun extract(nodes: List<ScreenNode>): List<String> = nodes
        .asSequence()
        .filterNot { it.isEditable } // never the box the user is typing into
        .map { it.text.trim() }
        .filter { it.isNotEmpty() && it.length <= MAX_CHARS && it.any { c -> c.isLetter() } }
        .distinct()
        .toList()
}
