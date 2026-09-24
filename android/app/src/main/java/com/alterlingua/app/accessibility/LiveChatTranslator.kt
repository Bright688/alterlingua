package com.alterlingua.app.accessibility

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.notifications.IncomingSources
import com.alterlingua.app.notifications.SeenMessages
import com.alterlingua.app.notifications.TranslatedConversation
import com.alterlingua.app.notifications.TranslatedLine
import com.alterlingua.app.notifications.TranslationPresenter
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Translates text found live on a supported chat app's own screen while it is open, and shows it through [presenter]
 * (in practice a floating bubble, never a system notification: the user is already looking at the chat, so a second
 * notification for it would be redundant).
 *
 * Deliberately simpler than [com.alterlingua.app.notifications.IncomingTranslator]: a notification comes with a real
 * sender, conversation title and category the source app chose to provide; a screen reading has none of that, only
 * plain strings a heuristic picked out (see [ChatScreenExtractor]). [packageName] alone stands in for both the
 * conversation key (for de-duplication) and the shown title (via [IncomingSources.displayNameOf]).
 *
 * Off by default ([com.alterlingua.app.learning.UserSettings.liveChatTranslationEnabled][com.alterlingua.app.learning.UserSettings])
 * and inert without Android's Accessibility permission, which only the caller (the accessibility service itself,
 * gated by Android at the OS level to the known chat apps) can ever supply events for.
 */
class LiveChatTranslator(
    private val api: TranslationApi,
    private val nativeLanguage: suspend () -> Language,
    private val enabled: suspend () -> Boolean,
    private val presenter: TranslationPresenter,
    private val seen: SeenMessages,
    private val timeoutMillis: Long = 25_000,
    /** Told about each translated line, with the language it was written in. Never delays the translation. */
    private val learning: LearningRecorder = LearningRecorder.None,
) {
    private val lock = Mutex()

    /** [texts] is whatever [ChatScreenExtractor] kept from one screen reading of [packageName]'s current window. */
    suspend fun handle(packageName: String, texts: List<String>) {
        if (texts.isEmpty() || !enabled()) return
        val title = IncomingSources.displayNameOf(packageName) ?: return // not a known, scoped source
        lock.withLock {
            val native = nativeLanguage()
            for (text in texts) {
                if (!seen.firstTime(packageName, sender = "", text = text, timestamp = 0)) continue
                val result = try {
                    withTimeout(timeoutMillis) { api.translate(TranslationRequest(text = text, target = native.code)) }
                } catch (_: TimeoutCancellationException) {
                    // No outcome to report the way a notification has; forget it so the next screen reading (the app
                    // keeps firing these as the chat updates) can try again rather than ignoring this text forever.
                    seen.forget(packageName, sender = "", text = text, timestamp = 0)
                    continue
                } catch (cancelled: CancellationException) {
                    seen.forget(packageName, sender = "", text = text, timestamp = 0)
                    throw cancelled
                }
                if (result !is TranslationResult.Success) {
                    seen.forget(packageName, sender = "", text = text, timestamp = 0) // a translation failure may pass too
                    continue
                }
                val answer = result.translation
                if (answer.sourceLanguage == native.code) continue // already in the user's language
                presenter.show(
                    TranslatedConversation(
                        key = packageName,
                        title = title,
                        isGroup = false,
                        lines = listOf(TranslatedLine(sender = "", text = answer.text, sourceLanguage = answer.sourceLanguage)),
                        openIntent = null, // the user is already in this chat; there is nothing to open
                    ),
                )
                learning.record(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, text, answer.sourceLanguage))
            }
        }
    }
}
