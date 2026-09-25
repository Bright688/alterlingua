package com.alterlingua.app.accessibility

/**
 * A translation to draw: where its box starts, how wide it is, and how many lines it may take. [compact] is a smaller
 * one-line style used when there is only a sliver of room.
 */
data class Caption(val text: String, val left: Int, val top: Int, val width: Int, val maxLines: Int, val compact: Boolean = false) {
    override fun toString() = "Caption(redacted)"
}

/** The pixel sizes [CaptionPlacer] works with. */
data class CaptionSizes(
    /** One normal caption line, including its top and bottom padding. */
    val lineHeightPx: Int,
    /** The single line of the compact style, including padding. */
    val compactLineHeightPx: Int,
    val marginPx: Int,
    val minWidthPx: Int,
    /** Space kept between a bubble and a caption placed beside it (bubbles are padded beyond their text). */
    val sideGapPx: Int,
)

/**
 * Decides where each translation is drawn so that it never hides anything the user is reading.
 *
 * An accessibility overlay cannot push the chat app's layout apart to make room, so a caption can only go where nothing
 * else is drawn. Every other message, name or quoted text on screen counts as something a caption must not cover (and so
 * does an earlier caption), and the caption must stay inside the conversation area (above the text box). For each
 * message, in order of preference:
 *  1. **Under it**, as wide as the message, if there is room before the next thing below (at least one line);
 *  2. **Beside it**, in the empty space next to the bubble on whichever side has more room;
 *  3. **Compact under it**: one smaller line, if that is all the room there is;
 *  4. otherwise **no caption**: a missing caption is better than one that covers a message.
 *
 * The bubble's own time stamp is not counted as something to protect, so an under-caption may cover it.
 */
object CaptionPlacer {
    const val MAX_LINES_UNDER = 3
    const val MAX_LINES_BESIDE = 4

    /** A caption beside a bubble is never wider than this share of the conversation area. */
    private const val MAX_SIDE_SHARE = 0.6

    fun place(
        conversation: Conversation,
        translations: (String) -> String?,
        sizes: CaptionSizes,
    ): List<Caption> {
        val area = conversation.area
        if (sizes.lineHeightPx <= 0 || area.width <= 0 || area.height <= 0) return emptyList()
        val messages = conversation.messages
        // Everything a caption must not cover: every message, and each caption already placed.
        val taken = messages.map { it.bounds }.toMutableList()
        val out = mutableListOf<Caption>()
        for (message in messages) {
            val text = translations(message.text) ?: continue
            val others = taken - message.bounds // (a list minus one element removes one occurrence)
            val caption = under(message, text, others, area, sizes)
                ?: beside(message, text, others, area, sizes)
                ?: compactUnder(message, text, others, area, sizes)
                ?: continue
            out += caption
            taken += rect(caption, sizes)
        }
        return out
    }

    private fun under(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes): Caption? {
        val (left, width) = underSpan(message, area, sizes)
        val top = message.bounds.bottom
        val lines = (roomBelow(top, left, left + width, others, area) / sizes.lineHeightPx).coerceAtMost(MAX_LINES_UNDER)
        return if (lines >= 1) Caption(text, left, top, width, lines) else null
    }

    private fun compactUnder(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes): Caption? {
        val (left, width) = underSpan(message, area, sizes)
        val top = message.bounds.bottom
        val room = roomBelow(top, left, left + width, others, area)
        return if (sizes.compactLineHeightPx in 1..room) Caption(text, left, top, width, 1, compact = true) else null
    }

    private fun beside(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes): Caption? {
        val b = message.bounds
        val leftSpace = b.left - sizes.sideGapPx - (area.left + sizes.marginPx)
        val rightSpace = (area.right - sizes.marginPx) - (b.right + sizes.sideGapPx)
        val useLeft = leftSpace >= rightSpace
        val space = if (useLeft) leftSpace else rightSpace
        if (space < sizes.minWidthPx) return null
        val width = space.coerceAtMost((area.width * MAX_SIDE_SHARE).toInt())
        val left = if (useLeft) b.left - sizes.sideGapPx - width else b.right + sizes.sideGapPx
        val top = b.top
        val lines = (roomBelow(top, left, left + width, others, area) / sizes.lineHeightPx).coerceAtMost(MAX_LINES_BESIDE)
        return if (lines >= 1) Caption(text, left, top, width, lines) else null
    }

    /** Where an under-caption starts and how wide it is: the message's own width, at least the minimum, kept on screen. */
    private fun underSpan(message: ChatMessage, area: Bounds, sizes: CaptionSizes): Pair<Int, Int> {
        val maxWidth = (area.width - 2 * sizes.marginPx).coerceAtLeast(1)
        val width = message.bounds.width.coerceIn(minOf(sizes.minWidthPx, maxWidth), maxWidth)
        val left = message.bounds.left.coerceIn(area.left + sizes.marginPx, (area.right - sizes.marginPx - width).coerceAtLeast(area.left + sizes.marginPx))
        return left to width
    }

    /**
     * How far down from [top] a box spanning [left]..[right] can go before it would touch something in [others] or leave
     * the conversation [area]; 0 when it would start on top of something.
     */
    private fun roomBelow(top: Int, left: Int, right: Int, others: List<Bounds>, area: Bounds): Int {
        var limit = area.bottom
        for (o in others) {
            if (o.right <= left || o.left >= right) continue // not in this column
            if (o.bottom <= top) continue // entirely above
            limit = minOf(limit, maxOf(o.top, top)) // starts below, or already covers, the start
        }
        return (limit - top).coerceAtLeast(0)
    }

    private fun rect(caption: Caption, sizes: CaptionSizes): Bounds {
        val height = caption.maxLines * (if (caption.compact) sizes.compactLineHeightPx else sizes.lineHeightPx)
        return Bounds(caption.left, caption.top, caption.left + caption.width, caption.top + height)
    }
}
