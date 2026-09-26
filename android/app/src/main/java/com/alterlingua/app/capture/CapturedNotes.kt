package com.alterlingua.app.capture

import androidx.lifecycle.ViewModelStore
import com.alterlingua.app.share.SharedVoiceState
import com.alterlingua.app.share.SharedVoiceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One captured voice note being (or already) transcribed and translated. Held in memory only. */
class CapturedNote internal constructor(
    val address: String,
    val createdAt: Long,
    /** The very same processing a shared voice note gets, so every screen shows one result. */
    val viewModel: SharedVoiceViewModel,
    internal val store: ViewModelStore,
)

/**
 * The captured voice notes of this app run, newest last. A note is created once per recording and shared by everything that
 * shows it (the keyboard's panel, the notification's result screen), so a note is transcribed and translated only once.
 *
 * Nothing here is saved: the notes, their transcripts and translations live in memory and go when the app's process does, when
 * the user closes a note, when more than [maxKept] notes pile up, or when the user erases their data. The recordings themselves
 * are private cache files that the processing deletes as soon as the server has answered.
 */
class CapturedNotes(
    private val create: (ViewModelStore) -> SharedVoiceViewModel,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxKept: Int = 5,
    /** Where the wait for each note's translation runs (the app's own scope). Null: nobody is told when a note is done. */
    private val scope: CoroutineScope? = null,
    /** Called once when a note has been transcribed and translated (not when it failed or was closed). */
    private val onTranslated: suspend (CapturedNote) -> Unit = {},
) {
    private val lock = Any()
    private val notes = LinkedHashMap<String, CapturedNote>()
    private val newest = MutableStateFlow<CapturedNote?>(null)
    private val dismissedOnKeyboard = MutableStateFlow<String?>(null)

    /** The note the keyboard should show: the most recent one that finished being captured. */
    val latest: StateFlow<CapturedNote?> = newest.asStateFlow()

    /** The address of the note the user closed on the keyboard (it is still open from the notification). */
    val dismissed: StateFlow<String?> = dismissedOnKeyboard.asStateFlow()

    /**
     * The note for [address]: the one already made, or a new one that starts transcribing at once. [announce] makes a new
     * note the one the keyboard shows (true for a recording that has just been captured, false when the user opens an
     * older one from a notification).
     */
    fun open(address: String, announce: Boolean): CapturedNote = synchronized(lock) {
        notes[address]?.let { return it }
        val store = ViewModelStore()
        val note = CapturedNote(address, clock(), create(store), store)
        notes[address] = note
        while (notes.size > maxKept) removeLocked(notes.keys.first())
        if (announce) {
            dismissedOnKeyboard.value = null
            newest.value = note
        }
        note.viewModel.start(address)
        scope?.launch {
            val settled = note.viewModel.uiState.first { it is SharedVoiceState.Result || it is SharedVoiceState.Failed || it == SharedVoiceState.Closed }
            if (settled is SharedVoiceState.Result) onTranslated(note)
        }
        note
    }

    /** Ends a note: stops its work, deletes anything it kept, and forgets its text. */
    fun close(address: String) = synchronized(lock) { removeLocked(address) }

    /** The user closed the note's panel on the keyboard. The note itself stays, so its notification still opens it. */
    fun dismissOnKeyboard(address: String) {
        dismissedOnKeyboard.value = address
    }

    /** Forgets every note (the user erased their data). */
    fun clearAll() = synchronized(lock) { notes.keys.toList().forEach(::removeLocked) }

    private fun removeLocked(address: String) {
        val note = notes.remove(address) ?: return
        note.viewModel.cancel()
        note.store.clear()
        if (newest.value?.address == address) newest.value = null
        if (dismissedOnKeyboard.value == address) dismissedOnKeyboard.value = null
    }
}
