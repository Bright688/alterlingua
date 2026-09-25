package com.alterlingua.app.accessibility

/** A translation to draw: where its box starts and how wide and tall (at most) it may be. */
data class Caption(val text: String, val left: Int, val top: Int, val width: Int, val maxLines: Int) {
    override fun toString() = "Caption(redacted)"
}

/**
 * Decides where each translation is drawn: directly under its own message.
 *
 * An accessibility overlay cannot push the chat app's layout apart to make room, so a caption is drawn on top of
 * whatever sits just below the message (in practice the bubble's own time stamp and the gap to the next bubble). To
 * avoid hiding the next message, a caption is limited to the room left before the next message starts (at least one
 * line, at most [maxLinesCap]); a longer translation is ended with an ellipsis rather than covering it.
 */
object CaptionPlacer {
    const val maxLinesCap = 3

    /**
     * [messages] must be sorted by top (as [ChatScreenExtractor] returns them). [lineHeightPx] is the height of one
     * line of caption text. [translations] holds a translation only for messages that need one.
     */
    fun place(
        messages: List<ChatMessage>,
        translations: (String) -> String?,
        screen: Bounds,
        lineHeightPx: Int,
        marginPx: Int,
        minWidthPx: Int,
    ): List<Caption> {
        if (lineHeightPx <= 0) return emptyList()
        val out = mutableListOf<Caption>()
        for ((index, message) in messages.withIndex()) {
            val text = translations(message.text) ?: continue
            val top = message.bounds.bottom
            val nextTop = messages.drop(index + 1).firstOrNull { it.bounds.top >= message.bounds.bottom }?.bounds?.top
            val limit = nextTop ?: screen.bottom
            val room = limit - top
            val lines = (room / lineHeightPx).coerceIn(1, maxLinesCap)
            val maxWidth = (screen.width - 2 * marginPx).coerceAtLeast(1)
            val width = message.bounds.width.coerceIn(minOf(minWidthPx, maxWidth), maxWidth)
            val left = message.bounds.left.coerceIn(screen.left + marginPx, (screen.right - marginPx - width).coerceAtLeast(screen.left + marginPx))
            if (top + lineHeightPx > screen.bottom) continue // no room even for one line on this screen
            out += Caption(text, left, top, width, lines)
        }
        return out
    }
}
