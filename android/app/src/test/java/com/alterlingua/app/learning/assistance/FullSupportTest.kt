package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.LearningContext
import com.alterlingua.app.learning.engine.LearningPipeline
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapExposureStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.notifications.IncomingOutcome
import com.alterlingua.app.notifications.IncomingOutcomeKind
import com.alterlingua.app.notifications.IncomingTranslator
import com.alterlingua.app.notifications.NotificationSnapshot
import com.alterlingua.app.notifications.SeenMessages
import com.alterlingua.app.notifications.TranslatedConversation
import com.alterlingua.app.notifications.TranslationPresenter
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullSupportTest {

    private class Presenter : TranslationPresenter {
        val shown = mutableListOf<TranslatedConversation>()
        override fun canPost() = true
        override fun show(conversation: TranslatedConversation) { shown += conversation }
        override fun remove(key: String) {}
    }

    private val french = mapOf(
        "Je vais envoyer le devis avant midi" to "I will send the quotation before noon",
        "Tu viens demain ?" to "Are you coming tomorrow?",
    )
    private val api = object : TranslationApi {
        val requests = mutableListOf<TranslationRequest>()
        override suspend fun translate(request: TranslationRequest): TranslationResult {
            requests += request
            return if (request.text in french) TranslationResult.Success(Translation(french.getValue(request.text), "fr", request.target))
            else TranslationResult.Success(Translation(request.text, request.target, request.target))
        }
    }

    private fun snapshot(text: String, key: String = "chat", time: Long = 1) =
        NotificationSnapshot(packageName = "com.whatsapp", key = key, category = "msg", title = "Marie", text = text, postTime = time)

    private fun translator(
        presenter: Presenter,
        learning: LearningRecorder,
        mode: AssistanceMode = AssistanceMode.FULL_SUPPORT,
    ) = IncomingTranslator(
        api = api, nativeLanguage = { Languages.English }, enabled = { true }, presenter = presenter, seen = SeenMessages(),
        outcomes = { _: IncomingOutcome -> }, isSource = { it == "com.whatsapp" }, learning = learning, assistanceMode = { mode },
    )

    // ---- the policy ----

    @Test
    fun fullSupport_translatesTheWholeMessage_andKeepsLearningOn() {
        val plan = AssistancePolicy.incoming(AssistanceMode.FULL_SUPPORT)
        assertTrue(plan.translateWholeMessage)
        assertTrue(plan.feedLearning)
    }

    @Test
    fun theOtherModesAreNotBuiltYet_soTheyDoNotHideAnything() {
        assertEquals(AssistancePolicy.FULL_SUPPORT, AssistancePolicy.incoming(AssistanceMode.ADAPTIVE))
        assertEquals(AssistancePolicy.FULL_SUPPORT, AssistancePolicy.incoming(AssistanceMode.ON_DEMAND))
    }

    // ---- incoming messages under Full Support ----

    @Test
    fun aForeignMessage_isFullyTranslated_andAlsoOfferedForLearning() = runTest {
        val presenter = Presenter()
        val heard = mutableListOf<TranslationInteraction>()
        translator(presenter, { heard += it }).handle(snapshot("Tu viens demain ?"))

        assertEquals("Are you coming tomorrow?", presenter.shown.single().lines.single().text)
        assertEquals(1, heard.size)
        assertEquals("fr", heard.single().textLanguage)
    }

    @Test
    fun aMessageInTheUsersOwnLanguage_isNeitherTranslatedNorLearnedFrom() = runTest {
        val presenter = Presenter()
        val heard = mutableListOf<TranslationInteraction>()
        val t = IncomingTranslator(
            api = api, nativeLanguage = { Languages.French }, enabled = { true }, presenter = presenter, seen = SeenMessages(),
            outcomes = { _: IncomingOutcome -> }, isSource = { it == "com.whatsapp" }, learning = { heard += it },
            assistanceMode = { AssistanceMode.FULL_SUPPORT },
        )
        t.handle(snapshot("Tu viens demain ?"))
        assertTrue(presenter.shown.isEmpty())
        assertTrue(heard.isEmpty())
    }

    @Test
    fun theModeIsReadFreshForEachNotification() = runTest {
        var reads = 0
        val presenter = Presenter()
        val t = IncomingTranslator(
            api = api, nativeLanguage = { Languages.English }, enabled = { true }, presenter = presenter, seen = SeenMessages(),
            outcomes = { _: IncomingOutcome -> }, isSource = { it == "com.whatsapp" },
            assistanceMode = { reads++; AssistanceMode.FULL_SUPPORT },
        )
        t.handle(snapshot("Tu viens demain ?", key = "a", time = 1))
        t.handle(snapshot("Tu viens demain ?", key = "b", time = 2))
        assertEquals(2, reads)
    }

    // ---- end to end: a message reaches the Personal Language Map ----

    @Test
    fun underFullSupport_theMapStillGrows_fromTheRealPipeline() = runTest {
        val map = LanguageMapService(InMemoryLanguageMapStore())
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = true) },
            store = LanguageMapExposureStore(map),
        )
        val recorder = LearningRecorder { interaction ->
            // The production recorder launches this in the background; here it is run in place so the test can look.
            kotlinx.coroutines.runBlocking { pipeline.record(interaction) }
        }
        val presenter = Presenter()
        val t = translator(presenter, recorder)
        t.handle(snapshot("Je vais envoyer le devis avant midi"))

        assertEquals(1, presenter.shown.size) // fully translated...
        val items = map.items("fr")
        assertTrue("the map received units", items.isNotEmpty()) // ...and learning still happened
        assertTrue(items.any { it.normalized == "devis" })
        assertTrue(items.all { it.language == "fr" && it.exposureCount >= 1 })
        assertTrue(items.none { it.masteryState == MasteryStatus.MASTERED })
    }

    @Test
    fun theUsersLearnFromMessagesSwitch_stillWinsUnderFullSupport() = runTest {
        val map = LanguageMapService(InMemoryLanguageMapStore())
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = false) },
            store = LanguageMapExposureStore(map),
        )
        val presenter = Presenter()
        translator(presenter, learning = { kotlinx.coroutines.runBlocking { pipeline.record(it) } })
            .handle(snapshot("Je vais envoyer le devis avant midi"))

        assertEquals(1, presenter.shown.size) // still fully translated
        assertTrue(map.items("fr").isEmpty()) // but the user chose not to learn from messages
        assertFalse(presenter.shown.isEmpty())
    }

    @Test
    fun beingTranslatedForIsNotRecordedAsAskingForHelp() = runTest {
        val map = LanguageMapService(InMemoryLanguageMapStore())
        val pipeline = LearningPipeline(
            analyzers = AnalyzerRegistry(listOf(RuleBasedAnalyzer())),
            context = { LearningContext(Languages.French, Languages.English, enabled = true) },
            store = LanguageMapExposureStore(map),
        )
        translator(Presenter(), learning = { kotlinx.coroutines.runBlocking { pipeline.record(it) } })
            .handle(snapshot("Je vais envoyer le devis avant midi"))
        assertTrue(map.items("fr").all { it.helpRequests == 0 })
    }
}
