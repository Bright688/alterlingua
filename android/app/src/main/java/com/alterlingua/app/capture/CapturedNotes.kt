package com.alterlingua.app.capture

import androidx.lifecycle.ViewModelStore
import com.alterlingua.app.share.SharedVoiceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One captured voice note being (or already) transcribed and translated. Held in memory only. */
class CapturedNote internal constructor(
    val address: String,
    val createdAt: Long,
    /** The very same processing a shared voice note gets, so every screen shows one result. */
    val viewModel: SharedVoiceViewModel,
    internal val store: ViewModelStore,
) {
    private val startedNow = MutableStateFlow(false)

    /** False while the recording waits for the user to ask for it to be translated (automatic translation is off). */
    val started: StateFlow<Boolean> = startedNow.asStateFlow()

    /** Sends the recording to be transcribed and translated, once. */
    internal fun start() {
        if (startedNow.compareAndSet(expect = false, update = true)) viewModel.start(address)
    }
}

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
    /** Deletes the recording behind an address; used when a note is closed before it was ever translated. */
    private val discard: (String) -> Unit = {},
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
     * The note for [address]: the one already made, or a new one. [announce] makes a new note the one the keyboard shows
     * (true for a recording that has just been captured). [startNow] sends the recording to be translated at once; false
     * leaves it waiting until [translate] is called (the user asked for automatic translation to be off).
     */
    fun open(address: String, announce: Boolean, startNow: Boolean = true): CapturedNote = synchronized(lock) {
        val existing = notes[address]
        if (existing != null) {
            if (startNow) existing.start()
            return existing
        }
        val store = ViewModelStore()
        val note = CapturedNote(address, clock(), create(store), store)
        notes[address] = note
        while (notes.size > maxKept) removeLocked(notes.keys.first())
        if (announce) {
            dismissedOnKeyboard.value = null
            newest.value = note
        }
        if (startNow) note.start()
        note
    }

    /** The user asked for a waiting note to be translated now. */
    fun translate(address: String) = synchronized(lock) { notes[address]?.start() ?: Unit }

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
        if (!note.started.value) discard(address)
        if (newest.value?.address == address) newest.value = null
        if (dismissedOnKeyboard.value == address) dismissedOnKeyboard.value = null
    }
}
