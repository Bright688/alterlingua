package com.alterlingua.app.accessibility

/**
 * A translation to draw: where its box starts, how wide it is, and how many lines it takes (never more than it needs).
 * [compact] is a smaller style used when there is only a sliver of room.
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
    /** The narrowest a caption under a message may be (a short message still gets a readable box). */
    val minWidthPx: Int,
    /** The narrowest a caption beside a bubble may be. */
    val sideMinWidthPx: Int,
    /** Space kept between a bubble and a caption placed beside it (bubbles are padded beyond their text). */
    val sideGapPx: Int,
    /** How many lines [text] takes in a caption box [widthPx] wide (normal or compact style). At least 1. */
    val measureLines: (text: String, widthPx: Int, compact: Boolean) -> Int,
)

/**
 * Decides where each translation is drawn so that it never hides anything the user is reading.
 *
 * An accessibility overlay cannot push the chat app's layout apart to make room, so a caption can only go where nothing
 * else is drawn. Every other message, name or quoted text on screen counts as something a caption must not cover (and so
 * does an earlier caption, by the space it really uses), and the caption must stay inside the conversation area (above
 * the text box). For each message these places are tried:
 *  1. **under it**, as wide as the message;
 *  2. **beside it**, in the empty space next to the bubble on whichever side has more room;
 *  3. **compact beside it**: the same, in a smaller style that fits between tightly stacked bubbles;
 *  4. **compact under it**.
 * The first place where the whole translation fits wins; if none shows all of it, the first that shows at least one line
 * is used and the end is cut with an ellipsis. If no place has room for even one line, the message gets no caption: a
 * missing caption is better than one that covers a message.
 *
 * The bubble's own time stamp is not counted as something to protect, so an under-caption may cover it.
 */
object CaptionPlacer {
    const val MAX_LINES_UNDER = 3
    const val MAX_LINES_BESIDE = 4

    /** A caption beside a bubble is never wider than this share of the conversation area. */
    private const val MAX_SIDE_SHARE = 0.6

    /** A place a caption could go, and whether it would have to cut the translation short. */
    private class Candidate(val caption: Caption, val truncated: Boolean)

    fun place(
        conversation: Conversation,
        translations: (String) -> String?,
        sizes: CaptionSizes,
    ): List<Caption> {
        val area = conversation.area
        if (sizes.lineHeightPx <= 0 || area.width <= 0 || area.height <= 0) return emptyList()
        val messages = conversation.messages
        // Everything a caption must not cover: every message, and the space each caption already placed really uses.
        val taken = messages.map { it.bounds }.toMutableList()
        val out = mutableListOf<Caption>()
        for (message in messages) {
            val text = translations(message.text) ?: continue
            val others = taken - message.bounds // (a list minus one element removes one occurrence)
            val candidates = listOfNotNull(
                under(message, text, others, area, sizes, compact = false),
                beside(message, text, others, area, sizes, compact = false),
                beside(message, text, others, area, sizes, compact = true),
                under(message, text, others, area, sizes, compact = true),
            )
            val chosen = (candidates.firstOrNull { !it.truncated } ?: candidates.firstOrNull())?.caption ?: continue
            out += chosen
            taken += rect(chosen, sizes)
        }
        return out
    }

    private fun under(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes, compact: Boolean): Candidate? {
        val (left, width) = underSpan(message, area, sizes)
        val top = message.bounds.bottom
        val room = roomBelow(top, left, left + width, others, area)
        return fit(text, left, top, width, room, compact, MAX_LINES_UNDER, sizes)
    }

    private fun beside(message: ChatMessage, text: String, others: List<Bounds>, area: Bounds, sizes: CaptionSizes, compact: Boolean): Candidate? {
        val b = message.bounds
        val leftSpace = b.left - sizes.sideGapPx - (area.left + sizes.marginPx)
        val rightSpace = (area.right - sizes.marginPx) - (b.right + sizes.sideGapPx)
        val useLeft = leftSpace >= rightSpace
        val space = if (useLeft) leftSpace else rightSpace
        if (space < sizes.sideMinWidthPx) return null
        val width = space.coerceAtMost((area.width * MAX_SIDE_SHARE).toInt())
        val left = if (useLeft) b.left - sizes.sideGapPx - width else b.right + sizes.sideGapPx
        val room = roomBelow(b.top, left, left + width, others, area)
        return fit(text, left, b.top, width, room, compact, MAX_LINES_BESIDE, sizes)
    }

    /** A candidate in a box of [width] with [room] pixels of height, or null when not even one line fits. */
    private fun fit(text: String, left: Int, top: Int, width: Int, room: Int, compact: Boolean, cap: Int, sizes: CaptionSizes): Candidate? {
        val lineHeight = if (compact) sizes.compactLineHeightPx else sizes.lineHeightPx
        if (lineHeight <= 0) return null
        val needed = sizes.measureLines(text, width, compact).coerceAtLeast(1)
        val lines = minOf(room / lineHeight, needed, cap)
        if (lines < 1) return null
        return Candidate(Caption(text, left, top, width, lines, compact), truncated = lines < needed)
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

    /** The space a placed caption really uses (its own lines, not the most it could have had). */
    private fun rect(caption: Caption, sizes: CaptionSizes): Bounds {
        val height = caption.maxLines * (if (caption.compact) sizes.compactLineHeightPx else sizes.lineHeightPx)
        return Bounds(caption.left, caption.top, caption.left + caption.width, caption.top + height)
    }
}
