package com.alterlingua.app.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What live chat-screen translation has been doing, as numbers only (never any text), so Settings can show whether it is
 * actually reading a chat and how far each reading got.
 *
 * [reads] and [skipped] count screen readings since the app process started; the other fields describe the latest
 * successful reading: [items] pieces of text seen, [textBoxes] typing boxes among them (0 means the bottom of the
 * conversation could not be located), [messages] kept as messages, and [captions] currently drawn.
 */
data class LiveChatReading(
    val reads: Int = 0,
    val skipped: Int = 0,
    val items: Int = 0,
    val textBoxes: Int = 0,
    val messages: Int = 0,
    val captions: Int = 0,
)

/** Holds the latest [LiveChatReading] in memory. Nothing here is saved. */
class LiveChatStatus {
    private val state = MutableStateFlow<LiveChatReading?>(null)

    val last: StateFlow<LiveChatReading?> = state.asStateFlow()

    /** A screen reading finished with these results. */
    fun read(items: Int, textBoxes: Int, messages: Int, captions: Int) = state.update {
        LiveChatReading(
            reads = (it?.reads ?: 0) + 1,
            skipped = it?.skipped ?: 0,
            items = items,
            textBoxes = textBoxes,
            messages = messages,
            captions = captions,
        )
    }

    /** A reading was asked for but there was no supported chat screen to read (or the setting was off). */
    fun skipped() = state.update { (it ?: LiveChatReading()).copy(skipped = (it?.skipped ?: 0) + 1) }

    /** Translations arrived and captions were redrawn. */
    fun captionsDrawn(captions: Int) = state.update { current -> current?.copy(captions = captions) }

    fun clear() {
        state.value = null
    }
}
