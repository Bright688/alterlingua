package com.alterlingua.app.accessibility

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Translates the messages read live off a supported chat app's own screen, and remembers the answers so each message is
 * translated once however often the screen is read again (scrolling back, a new message arriving, the app redrawing).
 *
 * The screen is read many times a minute, so this is where request volume is kept in check (a real incident on
 * 2026-09-24 saw a per-event translate call exhaust the provider's per-minute budget and break the keyboard's own
 * Translate button). Four independent limits apply, each of which alone stops a flood:
 *  - a cache: a message already answered is never asked again;
 *  - [maxPerPass]: at most this many new messages are sent per screen reading (the rest wait for the next one);
 *  - [maxPerMinute]: a rolling budget of network requests, shared by every reading;
 *  - a back-off: a message whose translation failed is not retried for [retryAfterFailureMillis].
 *
 * The cache holds message text, in this process's memory only. It is never saved and is cleared by [clear] (the
 * "Delete all learning data" action calls it) and whenever the accessibility service stops.
 *
 * Off by default ([com.alterlingua.app.learning.UserSettings.liveChatTranslationEnabled]) and inert without Android's
 * Accessibility permission, which only the service (limited by Android to the known chat apps) can ever call this from.
 */
class LiveChatTranslator(
    private val api: TranslationApi,
    private val nativeLanguage: suspend () -> Language,
    private val enabled: suspend () -> Boolean,
    private val timeoutMillis: Long = 25_000,
    private val maxPerPass: Int = 6,
    private val maxPerMinute: Int = 30,
    private val maxParallel: Int = 3,
    private val retryAfterFailureMillis: Long = 30_000,
    private val maxCached: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the network call runs (a real network thread in the app, the test's own thread in tests). */
    private val networkContext: CoroutineContext = EmptyCoroutineContext,
    /** Told about each translated message, with the language it was written in. Never delays the translation. */
    private val learning: LearningRecorder = LearningRecorder.None,
) {
    /** A finished lookup: [caption] is the translation to draw, or null when none is needed (already the user's language). */
    private class Entry(val caption: String?)

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxCached
    }
    private val inFlight = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val requestTimes = ArrayDeque<Long>()

    /** True when the user has this feature switched on in Settings. */
    suspend fun isEnabled(): Boolean = enabled()

    /** The translation to draw under [text], or null when it is unknown, still pending, or needs none. */
    fun captionFor(text: String): String? = synchronized(lock) { cache[text]?.caption }

    /**
     * Makes sure [texts] are being translated: those already answered are skipped, and at most [maxPerPass] of the rest
     * are sent (up to [maxParallel] at a time). [onTranslated] is called each time one arrives, so the caller can draw
     * it straight away instead of waiting for the whole batch.
     */
    suspend fun prepare(texts: List<String>, onTranslated: () -> Unit) {
        if (texts.isEmpty() || !enabled()) return
        val native = nativeLanguage()
        val now = clock()
        val wanted = synchronized(lock) {
            texts.distinct()
                .filter { it !in cache && it !in inFlight }
                .filter { failedAt[it]?.let { at -> now - at >= retryAfterFailureMillis } ?: true }
                .take(maxPerPass)
                .filter { takeBudget(now) }
                .onEach { inFlight += it }
        }
        if (wanted.isEmpty()) return
        val gate = Semaphore(maxParallel)
        coroutineScope {
            wanted.map { text ->
                async {
                    try {
                        gate.withPermit { translateOne(text, native, onTranslated) }
                    } finally {
                        synchronized(lock) { inFlight -= text }
                    }
                }
            }.forEach { it.await() }
        }
    }

    private suspend fun translateOne(text: String, native: Language, onTranslated: () -> Unit) {
        val result = try {
            withContext(networkContext) { withTimeout(timeoutMillis) { api.translate(TranslationRequest(text = text, target = native.code)) } }
        } catch (_: TimeoutCancellationException) {
            synchronized(lock) { failedAt[text] = clock() }
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        if (result !is TranslationResult.Success) {
            synchronized(lock) { failedAt[text] = clock() }
            return
        }
        val answer = result.translation
        // Already in the user's language, or unchanged (a name, a number): nothing worth showing under it.
        val needsCaption = answer.sourceLanguage != native.code && answer.text.trim().isNotEmpty() &&
            !answer.text.trim().equals(text.trim(), ignoreCase = true)
        synchronized(lock) {
            failedAt.remove(text)
            cache[text] = Entry(if (needsCaption) answer.text.trim() else null)
        }
        if (needsCaption) learning.record(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, text, answer.sourceLanguage))
        onTranslated()
    }

    /** Takes one request from the rolling per-minute budget; false when it is used up. Call with [lock] held. */
    private fun takeBudget(now: Long): Boolean {
        while (requestTimes.isNotEmpty() && now - requestTimes.first() >= 60_000) requestTimes.removeFirst()
        if (requestTimes.size >= maxPerMinute) return false
        requestTimes.addLast(now)
        return true
    }

    /** Forgets every message and answer (Settings > Delete all learning data; the service stopping). */
    fun clear() = synchronized(lock) {
        cache.clear()
        failedAt.clear()
        inFlight.clear()
        requestTimes.clear()
    }
}
