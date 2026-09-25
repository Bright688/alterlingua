package com.alterlingua.app.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Copies what Android's accessibility tree exposes for the current window into a plain [ScreenSnapshot] (text and
 * position only), so the filtering rules in [ChatScreenExtractor] can be tested without a real node tree. It reads;
 * it never taps, scrolls or otherwise acts on anything (CLAUDE.md section 37).
 */
object AccessibilityTreeReader {

    /** A runaway or unusually deep screen must not be walked forever. */
    private const val MAX_NODES = 500

    fun read(root: AccessibilityNodeInfo): ScreenSnapshot {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val out = mutableListOf<ScreenNode>()
        walk(root, out, rect)
        return ScreenSnapshot(Bounds(rect.left, rect.top, rect.right, rect.bottom), out)
    }

    @Suppress("DEPRECATION") // AccessibilityNodeInfo.recycle() is a no-op from API 33 but still safe to call on 26+
    private fun walk(node: AccessibilityNodeInfo, out: MutableList<ScreenNode>, scratch: Rect) {
        if (out.size >= MAX_NODES) return
        if (!node.isVisibleToUser) return // off screen or hidden: nothing drawn there to caption
        val text = node.text?.toString().orEmpty()
        // An empty text box is still kept: where it sits marks the bottom of the conversation area.
        if (text.isNotBlank() || node.isEditable) {
            node.getBoundsInScreen(scratch)
            out += ScreenNode(text, node.isEditable, Bounds(scratch.left, scratch.top, scratch.right, scratch.bottom))
        }
        for (i in 0 until node.childCount) {
            if (out.size >= MAX_NODES) return
            val child = node.getChild(i) ?: continue
            walk(child, out, scratch)
            child.recycle()
        }
    }
}
