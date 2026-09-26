package com.alterlingua.app.capturetest

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow

/** What the test screen shows. Held in memory only, for the life of the process; nothing here holds any audio. */
sealed interface CaptureUiState {
    data object Idle : CaptureUiState

    data class Listening(val appLabel: String, val secondsLeft: Int, val livePeakDb: Double, val usagesSeen: Set<Int>) : CaptureUiState

    /** The capture could not start or broke. [reason] names a kind of problem (an exception class), never any content. */
    data class Failed(val reason: String) : CaptureUiState
}

object CaptureTestState {
    val ui = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)

    /** Finished runs, newest first. */
    val history = MutableStateFlow<List<CaptureResult>>(emptyList())

    /** Set by the "Stop now" button; the service ends its listening loop when it sees it. */
    val stopRequested = AtomicBoolean(false)
}
