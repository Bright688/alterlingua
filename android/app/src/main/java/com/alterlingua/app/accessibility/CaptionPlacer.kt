package com.alterlingua.app.accessibility

/**
 * A translation to draw directly under its message: where its box starts, how wide it is, how many lines it takes
 * (never more than it needs) and the text size chosen so it fits the room there is.
 */
data class Caption(val text: String, val left: Int, val top: Int, val width: Int, val maxLines: Int, val textSizePx: Float) {
    override fun toString() = "Caption(redacted)"
}

/** The pixel sizes [CaptionPlacer] works with. */
data class CaptionSizes(
    /** The text sizes a caption may use, largest first. The largest that fits the room is chosen. */
    val textSizesPx: List<Float>,
    /** The height of one line at that text size, including the box's own top and bottom padding. */
    val lineHeightPx: (textSizePx: Float) -> Int,
    val marginPx: Int,
    /** The narrowest a caption may be (a short message still gets a readable box). */
    val minWidthPx: Int,
    /**
     * How far a caption may reach into the next message's text box. A text box carries empty padding above its first
     * line, so a few pixels there hide nothing; between messages from the same sender there are only ~12 px otherwise.
     */
    val overlapAllowancePx: Int,
    /** How many lines [text] takes in a caption box [widthPx] wide at [textSizePx]. At least 1. */
    val measureLines: (text: String, widthPx: Int, textSizePx: Float) -> Int,
)

/**
 * Decides where each translation is drawn: always directly under its own message, sized so that it never hides the
 * next one.
 *
 * An accessibility overlay cannot push the chat app's layout apart to make room, so the height of a caption is
 * limited by the space between its message and the next thing below it. Every other message, name or quoted text on
 * screen counts as something a caption must not cover (and so does an earlier caption, by the space it really uses), and
 * a caption stays inside the conversation area (above the text box the user types in). Within that room the largest
 * text size that shows the whole translation is used; when even the smallest size cannot, the translation is cut with an
 * ellipsis; when there is not room for a single line even at the smallest size, the message gets no caption (a missing
 * caption is better than one that covers a message).
 *
 * The bubble's own time stamp is not counted as something to protect, so a caption may cover it.
 */
object CaptionPlacer {
    const val MAX_LINES = 3

    fun place(
        conversation: Conversation,
        translations: (String) -> String?,
        sizes: CaptionSizes,
    ): List<Caption> {
        val area = conversation.area
        if (sizes.textSizesPx.isEmpty() || area.width <= 0 || area.height <= 0) return emptyList()
        val messages = conversation.messages
        // Everything a caption must not cover: every message, and the space each caption already placed really uses.
        val taken = messages.map { it.bounds }.toMutableList()
        val out = mutableListOf<Caption>()
        for (message in messages) {
            val text = translations(message.text) ?: continue
            val others = taken - message.bounds // (a list minus one element removes one occurrence)
            val caption = under(message, text, others, area, sizes) ?: continue
            out += caption
            taken += Bounds(caption.left, caption.top, caption.left + caption.width, caption.top + caption.maxLines * sizes.lineHeightPx(caption.textSizePx))
        }
        return out
    }

    private fun under(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes): Caption? {
        val maxWidth = (area.width - 2 * sizes.marginPx).coerceAtLeast(1)
        val width = message.bounds.width.coerceIn(minOf(sizes.minWidthPx, maxWidth), maxWidth)
        val left = message.bounds.left.coerceIn(area.left + sizes.marginPx, (area.right - sizes.marginPx - width).coerceAtLeast(area.left + sizes.marginPx))
        val top = message.bounds.bottom
        val room = roomBelow(top, left, left + width, others, area, sizes.overlapAllowancePx)

        var smallestThatFits: Caption? = null
        for (size in sizes.textSizesPx) {
            val lineHeight = sizes.lineHeightPx(size)
            if (lineHeight <= 0) continue
            val needed = sizes.measureLines(text, width, size).coerceAtLeast(1)
            val lines = minOf(room / lineHeight, needed, MAX_LINES)
            if (lines < 1) continue
            val caption = Caption(text, left, top, width, lines, size)
            if (lines >= needed) return caption // the largest size that shows all of it
            smallestThatFits = caption // shows part of it; a smaller size may show more
        }
        return smallestThatFits
    }

    /**
     * How far down from [top] a box spanning [left]..[right] can go: until the next thing in [others] in that column
     * (plus [allowance] px into it), or the bottom of the conversation [area] (with no allowance); 0 when it would start
     * on top of something.
     */
    private fun roomBelow(top: Int, left: Int, right: Int, others: List<Bounds>, area: Bounds, allowance: Int): Int {
        var limit = area.bottom
        var blockedByOther = false
        for (o in others) {
            if (o.right <= left || o.left >= right) continue // not in this column
            if (o.bottom <= top) continue // entirely above
            val start = maxOf(o.top, top)
            if (start < limit) {
                limit = start
                blockedByOther = true
            }
        }
        // Already sitting on top of something (it starts above the caption's top): no room, allowance or not.
        if (blockedByOther && limit <= top) return 0
        val room = (limit - top).coerceAtLeast(0)
        return if (blockedByOther) room + allowance else room
    }
}
