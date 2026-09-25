package com.alterlingua.app.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns a flood of accessibility events into a few screen readings.
 *
 * `TYPE_WINDOW_CONTENT_CHANGED` can fire many times a second in a normal chat app (a timestamp ticking, a read receipt
 * updating, a "typing…" indicator), and scroll events fire continuously during a fling. Reading the whole tree on each
 * would waste battery and, before the translator's own cache and budget existed, flooded the translation service (a
 * real incident on 2026-09-24). Two ways to ask for a reading:
 *  - [pokeAtMostEvery]: content changed. At most one reading per [delayMillis], and one is always guaranteed to
 *    follow the last change (a steady stream of events cannot starve it).
 *  - [pokeWhenSettled]: the screen is moving. The reading waits until [delayMillis] passes with no further pokes,
 *    so captions are not redrawn at positions that are already out of date.
 *
 * [action] must be quick (it is a tree walk and a redraw, not a network wait): a poke that arrives while it runs
 * schedules the next reading rather than being lost.
 */
class ReadScheduler(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 350,
    private val action: suspend () -> Unit,
) {
    private var pending: Job? = null

    fun pokeAtMostEvery() {
        if (pending?.isActive == true) return
        schedule()
    }

    fun pokeWhenSettled() {
        pending?.cancel()
        schedule()
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }

    private fun schedule() {
        pending = scope.launch {
            delay(delayMillis)
            pending = null // from here a new poke schedules the next reading, and cancel() cannot interrupt this one
            action()
        }
    }
}
