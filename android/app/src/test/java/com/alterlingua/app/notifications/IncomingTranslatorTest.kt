package com.alterlingua.app.notifications

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePresenter : TranslationPresenter {
    var canPost = true
    val shown = mutableListOf<TranslatedConversation>()
    val removed = mutableListOf<String>()
    override fun canPost() = canPost
    override fun show(conversation: TranslatedConversation) { shown += conversation }
    override fun remove(key: String) { removed += key }
}

private class FakeApi(var handler: suspend (TranslationRequest) -> TranslationResult) : TranslationApi {
    val requests = mutableListOf<TranslationRequest>()
    override suspend fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return handler(request)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingTranslatorTest {

    private val whatsappOnly = { pkg: String -> pkg == "com.whatsapp" }

    /** A pretend translator: what the backend would answer for these sample messages. */
    private val samples = mapOf(
        "Tu viens demain ?" to ("fr" to mapOf("en" to "Are you coming tomorrow?", "es" to "¿Vienes mañana?", "de" to "Kommst du morgen?", "it" to "Vieni domani?", "nl" to "Kom je morgen?", "zh" to "你明天来吗？", "ja" to "明日来ますか？")),
        "Are you coming tomorrow?" to ("en" to mapOf("fr" to "Tu viens demain ?", "es" to "¿Vienes mañana?", "de" to "Kommst du morgen?", "it" to "Vieni domani?", "nl" to "Kom je morgen?", "zh" to "你明天来吗？", "ja" to "明日来ますか？")),
    )

    private fun answer(request: TranslationRequest): TranslationResult {
        val (source, byTarget) = samples.getValue(request.text)
        return if (source == request.target) {
            TranslationResult.Success(Translation(request.text, source, request.target)) // already in that language: unchanged
        } else {
            TranslationResult.Success(Translation(byTarget.getValue(request.target), source, request.target))
        }
    }

    private class Setup(
        val translator: IncomingTranslator,
        val api: FakeApi,
        val presenter: FakePresenter,
        val outcomes: MutableList<IncomingOutcome>,
        val native: Array<Language>,
        val enabled: BooleanArray,
        val seen: SeenMessages,
    )

    private val heard = mutableListOf<TranslationInteraction>()

    private fun TestScope.setup(native: Language = Languages.English, api: FakeApi = FakeApi { answer(it) }): Setup {
        val presenter = FakePresenter()
        val outcomes = mutableListOf<IncomingOutcome>()
        val current = arrayOf(native)
        val enabled = booleanArrayOf(true)
        val seen = SeenMessages()
        val translator = IncomingTranslator(
            api = api, nativeLanguage = { current[0] }, enabled = { enabled[0] }, presenter = presenter, seen = seen,
            outcomes = { outcomes += it }, isSource = whatsappOnly, timeoutMillis = 25_000, learning = LearningRecorder { heard += it },
        )
        return Setup(translator, api, presenter, outcomes, current, enabled, seen)
    }

    private fun message(text: String = "Tu viens demain ?", sender: String = "Marie", key: String = "chat-marie", time: Long = 1_000) =
        NotificationSnapshot(packageName = "com.whatsapp", key = key, category = "msg", title = sender, text = text, postTime = time)

    // ---- the example from the brief, and other users ----

    @Test
    fun englishSpeaker_getsFrenchMessagesInEnglish_labelledWithTheirSourceLanguage() = runTest {
        val s = setup(native = Languages.English)
        s.translator.handle(message("Tu viens demain ?", "Marie"))

        assertEquals(TranslationRequest("Tu viens demain ?", "en", "auto", "messaging", "natural"), s.api.requests.single())
        val shown = s.presenter.shown.single()
        assertEquals("Marie", shown.title)
        assertEquals(listOf(TranslatedLine("Marie", "Are you coming tomorrow?", "fr")), shown.lines)
        assertEquals(IncomingOutcome(IncomingOutcomeKind.TRANSLATED, "fr", 0).kind, s.outcomes.single().kind)
    }

    @Test
    fun theDestinationIsEachUsersOwnNativeLanguage_notAlwaysEnglish() = runTest {
        for (native in Languages.supported) {
            // A message in some other language than the user's own.
            val text = if (native == Languages.French) "Are you coming tomorrow?" else "Tu viens demain ?"
            val s = setup(native = native)
            s.translator.handle(message(text))

            assertEquals(native.displayName, native.code, s.api.requests.single().target)
            assertEquals("auto", s.api.requests.single().source)
            val line = s.presenter.shown.single().lines.single()
            assertEquals(samples.getValue(text).second.getValue(native.code), line.text)
            assertEquals(samples.getValue(text).first, line.sourceLanguage)
        }
    }

    @Test
    fun changingTheNativeLanguageChangesTheNextTranslation() = runTest {
        val s = setup(native = Languages.English)
        s.translator.handle(message(key = "a", time = 1))
        s.native[0] = Languages.Japanese
        s.translator.handle(message(key = "b", time = 2))
        assertEquals(listOf("en", "ja"), s.api.requests.map { it.target })
        assertEquals("明日来ますか？", s.presenter.shown.last().lines.single().text)
    }

    @Test
    fun aMessageAlreadyInTheUsersLanguageIsLeftAlone() = runTest {
        val s = setup(native = Languages.French)
        s.translator.handle(message("Tu viens demain ?"))
        assertTrue(s.presenter.shown.isEmpty())
        assertEquals(IncomingOutcomeKind.ALREADY_IN_YOUR_LANGUAGE, s.outcomes.single().kind)
    }

    @Test
    fun aTranslatedIncomingMessage_isHandedToTheLearningEngine_withTheLanguageItWasWrittenIn() = runTest {
        val s = setup(native = Languages.English)
        s.translator.handle(message("Tu viens demain ?"))
        assertEquals(listOf(TranslationInteraction(InteractionKind.INCOMING_MESSAGE, "Tu viens demain ?", "fr")), heard)
    }

    @Test
    fun messagesThatWereNotTranslated_teachNothing() = runTest {
        val s = setup(native = Languages.French)
        s.translator.handle(message("Tu viens demain ?")) // already in the user's language
        s.enabled[0] = false
        s.translator.handle(message("Tu viens demain ?", key = "b", time = 2))
        val failing = setup(api = FakeApi { TranslationResult.Failure(TranslationFailure.TIMEOUT) })
        failing.translator.handle(message())
        assertTrue(heard.isEmpty())
    }

    // ---- switching it off, permission ----

    @Test
    fun whenTurnedOff_nothingIsSentAnywhere() = runTest {
        val s = setup()
        s.enabled[0] = false
        s.translator.handle(message())
        assertTrue(s.api.requests.isEmpty() && s.presenter.shown.isEmpty() && s.outcomes.isEmpty())
    }

    @Test
    fun ifNotificationsCannotBePosted_nothingIsSent() = runTest {
        val s = setup()
        s.presenter.canPost = false
        s.translator.handle(message())
        assertTrue(s.api.requests.isEmpty())
        assertEquals(IncomingOutcomeKind.NOTIFICATIONS_BLOCKED, s.outcomes.single().kind)
    }

    // ---- other apps and our own notifications ----

    @Test
    fun otherAppsAndOwnTranslationsAreIgnoredCompletely() = runTest {
        val s = setup()
        s.translator.handle(message().copy(packageName = "com.telegram"))
        s.translator.handle(message().copy(isOwnTranslation = true))
        assertTrue(s.api.requests.isEmpty() && s.presenter.shown.isEmpty() && s.outcomes.isEmpty())
    }

    // ---- duplicates, updates, grouped notifications ----

    @Test
    fun aDuplicateNotification_isTranslatedOnce() = runTest {
        val s = setup()
        s.translator.handle(message())
        s.translator.handle(message())
        assertEquals(1, s.api.requests.size)
        assertEquals(1, s.presenter.shown.size)
    }

    @Test
    fun anUpdatedConversation_translatesOnlyTheNewMessage_andShowsBoth() = runTest {
        val s = setup(native = Languages.Spanish) // both messages are in other languages than Español
        val first = message().copy(messages = listOf(SnapshotMessage("Marie", "Tu viens demain ?", 1)))
        val second = message().copy(messages = listOf(SnapshotMessage("Marie", "Tu viens demain ?", 1), SnapshotMessage("Marie", "Are you coming tomorrow?", 2)))
        s.translator.handle(first)
        s.translator.handle(second)

        assertEquals(listOf("Tu viens demain ?", "Are you coming tomorrow?"), s.api.requests.map { it.text })
        assertEquals(2, s.presenter.shown.last().lines.size)
    }

    @Test
    fun severalNewMessagesInOneNotification_areAllTranslated_inOrder() = runTest {
        val s = setup(native = Languages.Spanish)
        s.translator.handle(message().copy(messages = listOf(SnapshotMessage("Marie", "Tu viens demain ?", 1), SnapshotMessage("Marie", "Are you coming tomorrow?", 2))))
        assertEquals(listOf("Tu viens demain ?", "Are you coming tomorrow?"), s.api.requests.map { it.text })
        assertEquals(1, s.presenter.shown.size) // one notification showing both
        assertEquals(2, s.presenter.shown.single().lines.size)
    }

    @Test
    fun groupedNotifications_theSummaryIsIgnored_andEachConversationGetsItsOwn() = runTest {
        val s = setup()
        s.translator.handle(message(sender = "WhatsApp", text = "2 new messages from 2 chats", key = "summary").copy(isGroupSummary = true))
        s.translator.handle(message(sender = "Marie", key = "chat-marie"))
        s.translator.handle(message(sender = "Paul", key = "chat-paul", time = 2))
        assertEquals(2, s.api.requests.size)
        assertEquals(listOf("chat-marie", "chat-paul"), s.presenter.shown.map { it.key })
    }

    @Test
    fun aGroupChat_showsEachSenderWithTheirLine() = runTest {
        val s = setup()
        s.translator.handle(
            message(key = "family").copy(
                title = "Family", conversationTitle = "Family", isGroupConversation = true,
                messages = listOf(SnapshotMessage("Papa", "Tu viens demain ?", 1)),
            ),
        )
        val shown = s.presenter.shown.single()
        assertEquals("Family", shown.title)
        assertTrue(shown.isGroup)
        assertEquals("Papa", shown.lines.single().sender)
    }

    @Test
    fun linesAreCappedAtFive() = runTest {
        val s = setup()
        repeat(8) { s.translator.handle(message(time = it.toLong() + 1)) }
        assertEquals(5, s.presenter.shown.last().lines.size)
    }

    @Test
    fun whenTheOriginalGoesAway_ourNotificationIsRemoved_andItsTextIsForgotten() = runTest {
        val s = setup()
        s.translator.handle(message(time = 1))
        s.translator.onRemoved("chat-marie")
        assertEquals(listOf("chat-marie"), s.presenter.removed)
        s.translator.handle(message(time = 2)) // a later message starts a fresh conversation
        assertEquals(1, s.presenter.shown.last().lines.size)
    }

    // ---- hidden and missing content ----

    @Test
    fun hiddenContent_isReported_andNothingIsTranslated() = runTest {
        val s = setup()
        s.translator.handle(message().copy(visibility = NotificationSnapshot.VISIBILITY_SECRET))
        s.translator.handle(message(sender = "WhatsApp", text = "1 new message", key = "x"))
        assertTrue(s.api.requests.isEmpty())
        assertEquals(listOf(IncomingOutcomeKind.HIDDEN_CONTENT, IncomingOutcomeKind.HIDDEN_CONTENT), s.outcomes.map { it.kind })
    }

    @Test
    fun missingTextAndMedia_areIgnoredQuietly() = runTest {
        val s = setup()
        s.translator.handle(message(text = ""))
        s.translator.handle(message(text = "🎤 Voice message (0:12)", key = "b"))
        s.translator.handle(message(text = "👍", key = "c"))
        assertTrue(s.api.requests.isEmpty() && s.presenter.shown.isEmpty() && s.outcomes.isEmpty())
    }

    // ---- translation problems ----

    @Test
    fun everyFailure_postsNothing_andIsReportedAsAnOutcome() = runTest {
        for (failure in TranslationFailure.entries) {
            val s = setup(api = FakeApi { TranslationResult.Failure(failure) })
            s.translator.handle(message())
            assertTrue(failure.name, s.presenter.shown.isEmpty())
            assertEquals(failure.name, 1, s.outcomes.size)
            assertFalse(failure.name, s.outcomes.single().kind == IncomingOutcomeKind.TRANSLATED)
        }
    }

    @Test
    fun theOutcomeNamesTheKindOfProblem() = runTest {
        val cases = mapOf(
            TranslationFailure.OFFLINE to IncomingOutcomeKind.OFFLINE,
            TranslationFailure.BACKEND_UNAVAILABLE to IncomingOutcomeKind.BACKEND_UNAVAILABLE,
            TranslationFailure.TIMEOUT to IncomingOutcomeKind.TIMEOUT,
            TranslationFailure.UNSUPPORTED_LANGUAGE to IncomingOutcomeKind.UNSUPPORTED_LANGUAGE,
            TranslationFailure.UNSUPPORTED_PAIR to IncomingOutcomeKind.UNSUPPORTED_LANGUAGE,
            TranslationFailure.SOURCE_UNDETECTED to IncomingOutcomeKind.LANGUAGE_UNDETECTED,
            TranslationFailure.NOT_CONFIGURED to IncomingOutcomeKind.NOT_CONFIGURED,
            TranslationFailure.TRANSLATION_FAILED to IncomingOutcomeKind.TRANSLATION_FAILED,
        )
        for ((failure, kind) in cases) {
            val s = setup(api = FakeApi { TranslationResult.Failure(failure) })
            s.translator.handle(message())
            assertEquals(failure.name, kind, s.outcomes.single().kind)
        }
    }

    @Test
    fun aProblemThatMayPass_isRetriedWhenTheNotificationIsPostedAgain() = runTest {
        var online = false
        val s = setup(api = FakeApi { if (online) answer(it) else TranslationResult.Failure(TranslationFailure.OFFLINE) })
        s.translator.handle(message())
        assertTrue(s.presenter.shown.isEmpty())

        online = true
        s.translator.handle(message()) // WhatsApp posted the same conversation again
        assertEquals("Are you coming tomorrow?", s.presenter.shown.single().lines.single().text)
    }

    @Test
    fun anUnsupportedLanguage_isNotRetried() = runTest {
        val s = setup(api = FakeApi { TranslationResult.Failure(TranslationFailure.UNSUPPORTED_LANGUAGE) })
        s.translator.handle(message())
        s.translator.handle(message())
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun aBackendThatNeverAnswers_timesOut() = runTest {
        val s = setup(api = FakeApi { awaitCancellation() })
        backgroundScope.launch { s.translator.handle(message()) }
        runCurrent()
        advanceTimeBy(25_001); runCurrent()
        assertEquals(IncomingOutcomeKind.TIMEOUT, s.outcomes.single().kind)
        assertTrue(s.presenter.shown.isEmpty())
    }

    @Test
    fun twoNotificationsAtOnceForTheSameMessage_areTranslatedOnce() = runTest {
        val s = setup()
        backgroundScope.launch { s.translator.handle(message()) }
        backgroundScope.launch { s.translator.handle(message()) }
        runCurrent()
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun outcomesNeverContainMessageText() = runTest {
        val s = setup()
        s.translator.handle(message("Tu viens demain ?", "Marie"))
        val text = s.outcomes.joinToString { it.toString() }
        assertFalse("Marie" in text || "Tu viens" in text || "Are you" in text)
    }
}
