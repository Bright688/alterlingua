package com.alterlingua.app.accessibility

/**
 * Bounds how often a live screen reading actually happens, regardless of how often Android reports a content change.
 *
 * `TYPE_WINDOW_CONTENT_CHANGED` can fire many times a second in a normal chat app — a timestamp ticking, a read
 * receipt updating, a "typing…" indicator, a call timer, a cursor blink. Without this, every one of those triggers a
 * full tree walk and, whenever the sampled text happens to differ from last time (a ticking counter always does),
 * another live translate request — a real incident on 2026-09-24 saw several requests per second, sustained,
 * exhausting the translation provider's per-minute budget and breaking translation for everything else, including
 * the ordinary keyboard's own "Translate" button, until this throttle was added.
 */
class ScreenReadThrottle(
    private val minIntervalMillis: Long = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var lastAllowedAt: Long = Long.MIN_VALUE / 2

    /** True at most once per [minIntervalMillis]; only advances its own clock on a true. */
    fun tryAcquire(): Boolean {
        val now = clock()
        if (now - lastAllowedAt < minIntervalMillis) return false
        lastAllowedAt = now
        return true
    }
}
