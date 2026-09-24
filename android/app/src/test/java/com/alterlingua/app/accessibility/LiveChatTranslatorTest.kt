package com.alterlingua.app.accessibility

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.notifications.SeenMessages
import com.alterlingua.app.notifications.TranslatedConversation
import com.alterlingua.app.notifications.TranslationPresenter
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePresenter : TranslationPresenter {
    val shown = mutableListOf<TranslatedConversation>()
    override fun canPost() = true
    override fun show(conversation: TranslatedConversation) { shown += conversation }
    override fun remove(key: String) {}
}

private class FakeApi(var handler: suspend (TranslationRequest) -> TranslationResult) : TranslationApi {
    val requests = mutableListOf<TranslationRequest>()
    override suspend fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return handler(request)
    }
}

class LiveChatTranslatorTest {

    private fun answer(source: String, target: String, text: String) = TranslationResult.Success(Translation(text, source, target))

    private class Setup(val translator: LiveChatTranslator, val api: FakeApi, val presenter: FakePresenter, val enabled: BooleanArray, val seen: SeenMessages)

    private fun setup(
        native: Language = Languages.English,
        api: FakeApi = FakeApi { answer("fr", it.target, "translated") },
        heard: MutableList<TranslationInteraction> = mutableListOf(),
    ): Setup {
        val presenter = FakePresenter()
        val enabled = booleanArrayOf(true)
        val seen = SeenMessages()
        val translator = LiveChatTranslator(
            api = api, nativeLanguage = { native }, enabled = { enabled[0] }, presenter = presenter, seen = seen,
            learning = LearningRecorder { heard += it },
        )
        return Setup(translator, api, presenter, enabled, seen)
    }

    @Test
    fun aScreenTextInAnotherLanguage_isShownTranslated_underTheAppsOwnName() = runTest {
        val s = setup(native = Languages.English)
        s.translator.handle("org.telegram.messenger", listOf("Tu viens demain ?"))
        assertEquals(1, s.presenter.shown.size)
        val shown = s.presenter.shown.single()
        assertEquals("Telegram", shown.title)
        assertEquals("org.telegram.messenger", shown.key)
        assertEquals("translated", shown.lines.single().text)
        assertEquals("fr", shown.lines.single().sourceLanguage)
        assertEquals(false, shown.isGroup)
    }

    @Test
    fun aTextAlreadyInTheUsersLanguage_isNotShown() = runTest {
        val api = FakeApi { answer("en", it.target, it.text) } // the backend says it is already English
        val s = setup(native = Languages.English, api = api)
        s.translator.handle("com.whatsapp", listOf("Are you coming tomorrow?"))
        assertTrue(s.presenter.shown.isEmpty())
    }

    @Test
    fun whenTurnedOff_nothingHappensAtAll() = runTest {
        val s = setup()
        s.enabled[0] = false
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        assertTrue(s.presenter.shown.isEmpty())
        assertTrue(s.api.requests.isEmpty())
    }

    @Test
    fun anUnknownPackage_isIgnored() = runTest {
        val s = setup()
        s.translator.handle("com.some.other.app", listOf("Tu viens demain ?"))
        assertTrue(s.presenter.shown.isEmpty())
        assertTrue(s.api.requests.isEmpty())
    }

    @Test
    fun noTextsAtAll_makesNoRequest() = runTest {
        val s = setup()
        s.translator.handle("com.whatsapp", emptyList())
        assertTrue(s.api.requests.isEmpty())
    }

    @Test
    fun theSameTextSeenAgain_isTranslatedOnlyOnce() = runTest {
        val s = setup()
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        assertEquals(1, s.api.requests.size)
        assertEquals(1, s.presenter.shown.size)
    }

    @Test
    fun theSameTextInDifferentApps_isTranslatedForEach() = runTest {
        val s = setup()
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        s.translator.handle("org.telegram.messenger", listOf("Tu viens demain ?"))
        assertEquals(2, s.api.requests.size)
        assertEquals(2, s.presenter.shown.size)
    }

    @Test
    fun severalDistinctTexts_areEachTranslatedAndShown() = runTest {
        val api = FakeApi { req -> answer("fr", req.target, "T: ${req.text}") }
        val s = setup(api = api)
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?", "Bonjour"))
        assertEquals(2, s.presenter.shown.size)
        assertEquals(setOf("T: Tu viens demain ?", "T: Bonjour"), s.presenter.shown.map { it.lines.single().text }.toSet())
    }

    @Test
    fun aSuccessfulTranslation_feedsTheLearningPipeline() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val s = setup(heard = heard)
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        assertEquals(1, heard.size)
        assertEquals("Tu viens demain ?", heard.single().text)
        assertEquals("fr", heard.single().textLanguage)
    }

    @Test
    fun aFailedTranslation_isForgotten_soItCanBeTriedAgain() = runTest {
        val api = FakeApi { TranslationResult.Failure(TranslationFailure.BACKEND_UNAVAILABLE) }
        val s = setup(api = api)
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        s.translator.handle("com.whatsapp", listOf("Tu viens demain ?"))
        assertEquals(2, s.api.requests.size) // not treated as "already seen": both attempts went to the backend
        assertTrue(s.presenter.shown.isEmpty())
    }
}
