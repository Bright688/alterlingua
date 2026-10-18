package com.alterlingua.app.learning.reading

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the real backend's own page-size cap (router.py: `Query(..., le=50)`) -- a request for more than that
 * must be rejected, not silently honored, exactly like the real server would (see the equivalent lesson already
 * learned the hard way for the Vocabulary curriculum, CurriculumLessonServiceTest's own comment on this). */
private class FakeReadingApi(private val byLanguage: Map<String, List<String>>) : ReadingApi {
    var unavailable = false
    val requestedOffsets = mutableListOf<Int>()
    val requestedLimits = mutableListOf<Int>()

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): ReadingPageResult {
        requestedOffsets += offset
        requestedLimits += limit
        if (unavailable) return ReadingPageResult.Unavailable
        if (limit !in 1..50) return ReadingPageResult.Unavailable
        val all = byLanguage[languageCode].orEmpty()
        return ReadingPageResult.Success(all.drop(offset).take(limit), all.size)
    }
}

private class FakeMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = "meaning of $unit"
}

class ReadingLessonServiceTest {

    private val tenFrenchSentences = (1..10).map { "Phrase numéro $it." }
    private var learning: Language = Languages.French

    private fun service(
        api: ReadingApi = FakeReadingApi(mapOf("fr" to tenFrenchSentences)),
        meanings: MeaningProvider = FakeMeanings(),
        randomOffset: (IntRange) -> Int = { it.first },
    ) = ReadingLessonService(
        api = api,
        meanings = meanings,
        languages = { LessonLanguages(learning, Languages.English) },
        randomOffset = randomOffset,
    )

    @Test fun startsASessionOfEightSentences() = runTest {
        val state = service().start()
        assertTrue(state is ReadingSessionState.InProgress)
        state as ReadingSessionState.InProgress
        assertEquals(8, state.cards.size)
        assertEquals(0, state.position)
    }

    @Test fun sentencesHaveNoTranslationUntilRevealed() = runTest {
        val state = service().start() as ReadingSessionState.InProgress
        assertNull(state.current.translation)
    }

    @Test fun revealFetchesTheCurrentSentencesMeaning() = runTest {
        val svc = service()
        val started = svc.start() as ReadingSessionState.InProgress
        val revealed = svc.reveal(started)
        assertEquals("meaning of ${started.current.sentence}", revealed.current.translation)
    }

    @Test fun revealingTwiceDoesNotAskAgain() = runTest {
        var calls = 0
        val meanings = object : MeaningProvider {
            override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? {
                calls++
                return "m"
            }
        }
        val svc = service(meanings = meanings)
        val started = svc.start() as ReadingSessionState.InProgress
        val once = svc.reveal(started)
        svc.reveal(once)
        assertEquals(1, calls)
    }

    @Test fun nextAdvancesThroughTheSession_thenFinishes() = runTest {
        val svc = service()
        var state = svc.start() as ReadingSessionState.InProgress
        repeat(7) { state = svc.next(state) as ReadingSessionState.InProgress }
        assertTrue(svc.next(state) is ReadingSessionState.Finished)
    }

    @Test fun aLanguageWithFewerThanEightSentences_stillStarts_withWhatExists() = runTest {
        val api = FakeReadingApi(mapOf("fr" to listOf("Une phrase.", "Une autre phrase.")))
        val state = service(api = api).start()
        assertTrue(state is ReadingSessionState.InProgress)
        assertEquals(2, (state as ReadingSessionState.InProgress).cards.size)
    }

    @Test fun aLanguageWithNoSentencesAtAll_isUnavailable_notACrash() = runTest {
        val api = FakeReadingApi(mapOf("fr" to emptyList()))
        assertTrue(service(api = api).start() is ReadingSessionState.Unavailable)
    }

    @Test fun anUnreachableBackend_isUnavailable() = runTest {
        val api = FakeReadingApi(mapOf("fr" to tenFrenchSentences)).apply { unavailable = true }
        assertTrue(service(api = api).start() is ReadingSessionState.Unavailable)
    }

    @Test fun neverRequestsMoreThanTheBackendsOwnPageCap() = runTest {
        val api = FakeReadingApi(mapOf("fr" to tenFrenchSentences))
        service(api = api).start()
        assertTrue("requested limits were ${api.requestedLimits}", api.requestedLimits.all { it <= 50 })
    }

    @Test fun aFreshSessionPicksARandomStartingPoint_notAlwaysTheSameSentences() = runTest {
        val api = FakeReadingApi(mapOf("fr" to (1..100).map { "Phrase $it." }))
        val state = service(api = api, randomOffset = { it.last }).start() as ReadingSessionState.InProgress
        assertEquals("Phrase 93.", state.current.sentence) // offset = total(100) - size(8) = 92, 0-indexed -> sentence 93
    }
}
