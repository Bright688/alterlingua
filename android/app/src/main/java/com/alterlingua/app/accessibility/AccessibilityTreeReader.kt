package com.alterlingua.app.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Copies what Android's accessibility tree exposes for the current window into plain [ScreenNode]s, so the filtering
 * rules in [ChatScreenExtractor] can be tested without a real node tree. Reads text only; it never taps, scrolls or
 * otherwise acts on anything (CLAUDE.md section 37).
 */
object AccessibilityTreeReader {

    /** A runaway or unusually deep screen must not be walked forever. */
    private const val MAX_NODES = 400

    fun read(root: AccessibilityNodeInfo): List<ScreenNode> {
        val out = mutableListOf<ScreenNode>()
        walk(root, out)
        return out
    }

    @Suppress("DEPRECATION") // AccessibilityNodeInfo.recycle() is a no-op from API 33 but still safe to call on 26+
    private fun walk(node: AccessibilityNodeInfo, out: MutableList<ScreenNode>) {
        if (out.size >= MAX_NODES) return
        node.text?.toString()?.let { text -> if (text.isNotBlank()) out += ScreenNode(text, node.isEditable) }
        for (i in 0 until node.childCount) {
            if (out.size >= MAX_NODES) return
            val child = node.getChild(i) ?: continue
            walk(child, out)
            child.recycle()
        }
    }
}
