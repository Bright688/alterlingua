package com.alterlingua.app.learning.speaking

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.reading.ReadingApi
import com.alterlingua.app.learning.reading.ReadingPageResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSpeakingApi(private val byLanguage: Map<String, List<String>>) : ReadingApi {
    var unavailable = false
    val requestedLimits = mutableListOf<Int>()

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): ReadingPageResult {
        requestedLimits += limit
        if (unavailable) return ReadingPageResult.Unavailable
        if (limit !in 1..50) return ReadingPageResult.Unavailable
        val all = byLanguage[languageCode].orEmpty()
        return ReadingPageResult.Success(all.drop(offset).take(limit), all.size)
    }
}

class SpeakingLessonServiceTest {

    private val tenFrenchSentences = (1..10).map { "Phrase numéro $it." }

    private fun service(
        api: ReadingApi = FakeSpeakingApi(mapOf("fr" to tenFrenchSentences)),
        randomOffset: (IntRange) -> Int = { it.first },
    ) = SpeakingLessonService(
        api = api,
        languages = { LessonLanguages(Languages.French, Languages.English) },
        randomOffset = randomOffset,
    )

    @Test fun startsASessionOfEightCards_eachAFreshUnknownCard() = runTest {
        val state = service().start()
        assertTrue(state is SpeakingSessionState.InProgress)
        state as SpeakingSessionState.InProgress
        assertEquals(8, state.cards.size)
        assertEquals("Phrase numéro 1.", state.current.term)
        assertEquals(MasteryStatus.UNKNOWN, state.current.masteryState)
        assertEquals("fr", state.current.key.language)
    }

    @Test fun nextAdvancesThroughTheSession_thenFinishes() = runTest {
        val svc = service()
        var state = svc.start() as SpeakingSessionState.InProgress
        repeat(7) { state = svc.next(state) as SpeakingSessionState.InProgress }
        assertTrue(svc.next(state) is SpeakingSessionState.Finished)
    }

    @Test fun aLanguageWithFewerThanEightSentences_stillStarts_withWhatExists() = runTest {
        val api = FakeSpeakingApi(mapOf("fr" to listOf("Une phrase.", "Une autre phrase.")))
        val state = service(api = api).start()
        assertTrue(state is SpeakingSessionState.InProgress)
        assertEquals(2, (state as SpeakingSessionState.InProgress).cards.size)
    }

    @Test fun aLanguageWithNoSentencesAtAll_isUnavailable_notACrash() = runTest {
        val api = FakeSpeakingApi(mapOf("fr" to emptyList()))
        assertTrue(service(api = api).start() is SpeakingSessionState.Unavailable)
    }

    @Test fun anUnreachableBackend_isUnavailable() = runTest {
        val api = FakeSpeakingApi(mapOf("fr" to tenFrenchSentences)).apply { unavailable = true }
        assertTrue(service(api = api).start() is SpeakingSessionState.Unavailable)
    }

    @Test fun neverRequestsMoreThanTheBackendsOwnPageCap() = runTest {
        val api = FakeSpeakingApi(mapOf("fr" to tenFrenchSentences))
        service(api = api).start()
        assertTrue("requested limits were ${api.requestedLimits}", api.requestedLimits.all { it <= 50 })
    }
}
