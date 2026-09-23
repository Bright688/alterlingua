package com.alterlingua.app.notifications

import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.assistance.AssistancePolicy
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Shows (or removes) the translated notification. The Android version posts a real notification. */
interface TranslationPresenter {
    /** False when Android will not let AlterLingua post notifications. */
    fun canPost(): Boolean

    fun show(conversation: TranslatedConversation)

    fun remove(key: String)
}

/** Receives how each message ended (never its text). */
fun interface OutcomeSink {
    fun record(outcome: IncomingOutcome)
}

/**
 * Translates incoming messages into the user's own language and hands them to the presenter.
 *
 * Rules (CLAUDE.md sections 21, 25, 38):
 * - The destination is the user's selected native language, read fresh for each message. English is never assumed.
 * - The source language is detected by the backend ("auto"). A message already in the user's language is left alone.
 * - A message is translated once (duplicates and re-posts are ignored), and nothing is saved: text is held in memory
 *   only for the translated notification's conversation, and dropped when that notification goes away.
 * - The assistance mode decides how much help a message gets (`AssistancePolicy`). Full Support translates the whole message
 *   and still feeds the learning pipeline.
 * - Failures never post anything; they are counted as an outcome so Settings can say what happened.
 */
class IncomingTranslator(
    private val api: TranslationApi,
    private val nativeLanguage: suspend () -> Language,
    private val enabled: suspend () -> Boolean,
    private val presenter: TranslationPresenter,
    private val seen: SeenMessages,
    private val outcomes: OutcomeSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = 25_000,
    private val isSource: (String) -> Boolean = IncomingSources::accepts,
    /** Told about each translated incoming message, with the language it was written in. */
    private val learning: LearningRecorder = LearningRecorder.None,
    /** The user's assistance mode, read fresh for each notification. */
    private val assistanceMode: suspend () -> AssistanceMode = { AssistanceMode.FULL_SUPPORT },
) {
    private class Conversation(var title: String, var isGroup: Boolean, var openIntent: Any?) {
        val lines = ArrayDeque<TranslatedLine>()
    }

    private val conversations = HashMap<String, Conversation>()
    private val lock = Mutex()

    suspend fun handle(snapshot: NotificationSnapshot) {
        // Cheap checks first, before anything of the notification is read further.
        if (!isSource(snapshot.packageName) || snapshot.isOwnTranslation) return
        if (!enabled()) return

        val extraction = NotificationExtractor.extract(snapshot, isSource)
        if (extraction is Extraction.Skipped) {
            if (extraction.reason == SkipReason.HIDDEN_CONTENT) record(IncomingOutcomeKind.HIDDEN_CONTENT)
            if (extraction.reason == SkipReason.TOO_LONG) record(IncomingOutcomeKind.TOO_LONG)
            return
        }
        extraction as Extraction.Messages
        if (!presenter.canPost()) return record(IncomingOutcomeKind.NOTIFICATIONS_BLOCKED)

        lock.withLock {
            val native = nativeLanguage()
            val assistance = AssistancePolicy.incoming(assistanceMode())
            var newest: TranslatedLine? = null
            for (message in extraction.messages) {
                if (!seen.firstTime(extraction.conversationKey, message.sender, message.text, message.timestamp)) continue
                val result = try {
                    withTimeout(timeoutMillis) { api.translate(TranslationRequest(text = message.text, target = native.code)) }
                } catch (_: TimeoutCancellationException) {
                    TranslationResult.Failure(TranslationFailure.TIMEOUT)
                } catch (cancelled: CancellationException) {
                    seen.forget(extraction.conversationKey, message.sender, message.text, message.timestamp)
                    throw cancelled
                }
                when (result) {
                    is TranslationResult.Failure -> {
                        val kind = kindFor(result.failure)
                        // Problems that may pass are retried when the conversation's notification is posted again.
                        if (result.failure.canRetry) seen.forget(extraction.conversationKey, message.sender, message.text, message.timestamp)
                        record(kind)
                    }
                    is TranslationResult.Success -> {
                        val answer = result.translation
                        if (answer.sourceLanguage == native.code) {
                            record(IncomingOutcomeKind.ALREADY_IN_YOUR_LANGUAGE, answer.sourceLanguage)
                        } else {
                            val line = TranslatedLine(message.sender, answer.text, answer.sourceLanguage)
                            conversations.getOrPut(extraction.conversationKey) { Conversation(extraction.title, extraction.isGroup, extraction.openIntent) }
                                .also {
                                    it.title = extraction.title
                                    it.isGroup = extraction.isGroup
                                    it.openIntent = extraction.openIntent
                                    it.lines.addLast(line)
                                    while (it.lines.size > MAX_LINES) it.lines.removeFirst()
                                }
                            newest = line
                            if (assistance.feedLearning) learning.record(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, message.text, answer.sourceLanguage))
                            record(IncomingOutcomeKind.TRANSLATED, answer.sourceLanguage)
                        }
                    }
                }
            }
            if (newest != null) {
                val conversation = conversations.getValue(extraction.conversationKey)
                presenter.show(
                    TranslatedConversation(extraction.conversationKey, conversation.title, conversation.isGroup, conversation.lines.toList(), conversation.openIntent),
                )
            }
        }
    }

    /** The original notification went away (the chat was opened or dismissed): remove ours and forget its text. */
    suspend fun onRemoved(key: String) {
        lock.withLock {
            conversations.remove(key)
            presenter.remove(key)
        }
    }

    /** Forget everything held in memory. */
    suspend fun clear() {
        lock.withLock {
            conversations.clear()
            seen.clear()
        }
    }

    private fun kindFor(failure: TranslationFailure) = when (failure) {
        TranslationFailure.OFFLINE -> IncomingOutcomeKind.OFFLINE
        TranslationFailure.BACKEND_UNAVAILABLE -> IncomingOutcomeKind.BACKEND_UNAVAILABLE
        TranslationFailure.TIMEOUT -> IncomingOutcomeKind.TIMEOUT
        TranslationFailure.UNSUPPORTED_LANGUAGE, TranslationFailure.UNSUPPORTED_PAIR -> IncomingOutcomeKind.UNSUPPORTED_LANGUAGE
        TranslationFailure.SOURCE_UNDETECTED -> IncomingOutcomeKind.LANGUAGE_UNDETECTED
        TranslationFailure.TEXT_TOO_LONG -> IncomingOutcomeKind.TOO_LONG
        TranslationFailure.NOT_CONFIGURED -> IncomingOutcomeKind.NOT_CONFIGURED
        else -> IncomingOutcomeKind.TRANSLATION_FAILED
    }

    private fun record(kind: IncomingOutcomeKind, source: String? = null) = outcomes.record(IncomingOutcome(kind, source, clock()))

    private companion object {
        const val MAX_LINES = 5
    }
}

/** The latest outcome, kept in memory for Settings to show. No message text. */
class IncomingStatus : OutcomeSink {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<IncomingOutcome?>(null)
    val last: kotlinx.coroutines.flow.StateFlow<IncomingOutcome?> = state

    override fun record(outcome: IncomingOutcome) {
        state.value = outcome
    }
}
