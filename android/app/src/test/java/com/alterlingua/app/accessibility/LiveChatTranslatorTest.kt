package com.alterlingua.app.accessibility

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeApi(var handler: suspend (TranslationRequest) -> TranslationResult) : TranslationApi {
    val requests = mutableListOf<TranslationRequest>()
    override suspend fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return handler(request)
    }
}

class LiveChatTranslatorTest {

    private fun answer(source: String, target: String, text: String) = TranslationResult.Success(Translation(text, source, target))

    private class Setup(val translator: LiveChatTranslator, val api: FakeApi, val enabled: BooleanArray, val now: LongArray, val arrivals: IntArray)

    private fun setup(
        native: Language = Languages.English,
        api: FakeApi = FakeApi { answer("fr", it.target, "T: ${it.text}") },
        heard: MutableList<TranslationInteraction> = mutableListOf(),
        maxPerPass: Int = 6,
        maxPerMinute: Int = 30,
    ): Setup {
        val enabled = booleanArrayOf(true)
        val now = longArrayOf(1_000_000)
        val translator = LiveChatTranslator(
            api = api, nativeLanguage = { native }, enabled = { enabled[0] },
            maxPerPass = maxPerPass, maxPerMinute = maxPerMinute, clock = { now[0] },
            learning = LearningRecorder { heard += it },
        )
        return Setup(translator, api, enabled, now, intArrayOf(0))
    }

    private suspend fun Setup.prepare(vararg texts: String) = translator.prepare(texts.toList()) { arrivals[0]++ }

    @Test
    fun aMessageInAnotherLanguage_getsACaptionInTheUsersLanguage() = runTest {
        val s = setup()
        s.prepare("Tu viens demain ?")
        assertEquals("T: Tu viens demain ?", s.translator.captionFor("Tu viens demain ?"))
        assertEquals("en", s.api.requests.single().target)
    }

    @Test
    fun eachArrivalIsAnnounced_soItCanBeDrawnAtOnce() = runTest {
        val s = setup()
        s.prepare("Bonjour", "Salut", "Merci")
        assertEquals(3, s.arrivals[0])
    }

    @Test
    fun aMessageAlreadyInTheUsersLanguage_getsNoCaption_andIsNotAskedAgain() = runTest {
        val api = FakeApi { answer("en", it.target, it.text) }
        val s = setup(api = api)
        s.prepare("Are you coming tomorrow?")
        assertNull(s.translator.captionFor("Are you coming tomorrow?"))
        s.prepare("Are you coming tomorrow?")
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun aTranslationIdenticalToTheOriginal_isNotShown() = runTest {
        // A name or a number: the backend "translates" it to itself; a caption repeating it would be noise.
        val api = FakeApi { answer("fr", it.target, it.text.uppercase()) }
        val s = setup(api = api)
        s.prepare("MARIE")
        assertNull(s.translator.captionFor("MARIE"))
    }

    @Test
    fun whenTurnedOff_nothingHappensAtAll() = runTest {
        val s = setup()
        s.enabled[0] = false
        s.prepare("Tu viens demain ?")
        assertTrue(s.api.requests.isEmpty())
        assertFalse(s.translator.isEnabled())
    }

    @Test
    fun noTextsAtAll_makesNoRequest() = runTest {
        val s = setup()
        s.prepare()
        assertTrue(s.api.requests.isEmpty())
    }

    @Test
    fun theSameMessageReadAgain_isTranslatedOnlyOnce() = runTest {
        val s = setup()
        repeat(50) { s.prepare("Tu viens demain ?") } // the screen is read again and again
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun theSameMessageTwiceInOneReading_isAskedOnce() = runTest {
        val s = setup()
        s.prepare("Ok", "Ok")
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun aBurstOfNewMessages_isCappedPerReading_andTheRestFollowOnTheNextOne() = runTest {
        val s = setup(maxPerPass = 2)
        val texts = (1..5).map { "Message $it" }.toTypedArray()
        s.prepare(*texts)
        assertEquals(2, s.api.requests.size)
        s.prepare(*texts)
        assertEquals(4, s.api.requests.size)
        s.prepare(*texts)
        assertEquals(5, s.api.requests.size)
    }

    @Test
    fun theRollingPerMinuteBudget_stopsAFloodEvenWithManyDistinctTexts() = runTest {
        val s = setup(maxPerPass = 100, maxPerMinute = 10)
        repeat(20) { i -> s.prepare("Distinct message number $i") }
        assertEquals("never more than the budget in one minute", 10, s.api.requests.size)
        s.now[0] += 61_000 // the minute passes: the budget refills
        repeat(20) { i -> s.prepare("Another distinct message $i") }
        assertEquals(20, s.api.requests.size)
    }

    @Test
    fun aFailedTranslation_isNotRetriedImmediately_butIsAfterTheBackOff() = runTest {
        val api = FakeApi { TranslationResult.Failure(TranslationFailure.BACKEND_UNAVAILABLE) }
        val s = setup(api = api)
        repeat(10) { s.prepare("Tu viens demain ?") }
        assertEquals("no hammering a service that is down", 1, s.api.requests.size)
        assertNull(s.translator.captionFor("Tu viens demain ?"))

        s.now[0] += 31_000
        s.api.handler = { answer("fr", it.target, "Are you coming tomorrow?") }
        s.prepare("Tu viens demain ?")
        assertEquals(2, s.api.requests.size)
        assertEquals("Are you coming tomorrow?", s.translator.captionFor("Tu viens demain ?"))
    }

    @Test
    fun aSuccessfulTranslation_feedsTheLearningPipeline() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val s = setup(heard = heard)
        s.prepare("Tu viens demain ?")
        assertEquals(1, heard.size)
        assertEquals("Tu viens demain ?", heard.single().text)
        assertEquals("fr", heard.single().textLanguage)
    }

    @Test
    fun clear_forgetsEverything_soTheNextReadingAsksAgain() = runTest {
        val s = setup()
        s.prepare("Tu viens demain ?")
        s.translator.clear()
        assertNull(s.translator.captionFor("Tu viens demain ?"))
        s.prepare("Tu viens demain ?")
        assertEquals(2, s.api.requests.size)
    }

    @Test
    fun theCache_isBounded() = runTest {
        val translator = LiveChatTranslator(
            api = FakeApi { answer("fr", it.target, "T: ${it.text}") }, nativeLanguage = { Languages.English }, enabled = { true },
            maxPerPass = 100, maxPerMinute = 1_000, maxCached = 5,
        )
        translator.prepare((1..20).map { "Message $it" }) {}
        val remembered = (1..20).count { translator.captionFor("Message $it") != null }
        assertEquals(5, remembered)
    }
}
