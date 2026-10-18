package com.alterlingua.app.learning.writing

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.curriculum.CurriculumApi
import com.alterlingua.app.learning.curriculum.CurriculumPageResult
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.MeaningProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeWritingApi(private val byLanguage: Map<String, List<String>>) : CurriculumApi {
    var unavailable = false
    val requestedLimits = mutableListOf<Int>()

    override suspend fun fetchPage(languageCode: String, offset: Int, limit: Int): CurriculumPageResult {
        requestedLimits += limit
        if (unavailable) return CurriculumPageResult.Unavailable
        if (limit !in 1..100) return CurriculumPageResult.Unavailable
        val all = byLanguage[languageCode].orEmpty()
        return CurriculumPageResult.Success(all.drop(offset).take(limit), all.size)
    }
}

private class FakeWritingMeanings : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = "meaning of $unit"
}

class WritingLessonServiceTest {

    private val tenFrenchWords = (1..10).map { "mot$it" }

    private fun service(
        api: CurriculumApi = FakeWritingApi(mapOf("fr" to tenFrenchWords)),
        meanings: MeaningProvider = FakeWritingMeanings(),
        randomOffset: (IntRange) -> Int = { it.first },
    ) = WritingLessonService(
        api = api,
        meanings = meanings,
        languages = { LessonLanguages(Languages.French, Languages.English) },
        randomOffset = randomOffset,
    )

    @Test fun startsASessionOfFivePrompts_withMeaningsAlreadyFilledIn() = runTest {
        val state = service().start()
        assertTrue(state is WritingSessionState.InProgress)
        state as WritingSessionState.InProgress
        assertEquals(5, state.prompts.size)
        assertEquals("mot1", state.current.word)
        assertEquals("meaning of mot1", state.current.meaning)
    }

    @Test fun nextAdvancesThroughTheSession_thenFinishes() = runTest {
        val svc = service()
        var state = svc.start() as WritingSessionState.InProgress
        repeat(4) { state = svc.next(state) as WritingSessionState.InProgress }
        assertTrue(svc.next(state) is WritingSessionState.Finished)
    }

    @Test fun aLanguageWithFewerThanFiveWords_stillStarts_withWhatExists() = runTest {
        val api = FakeWritingApi(mapOf("fr" to listOf("un", "deux")))
        val state = service(api = api).start()
        assertTrue(state is WritingSessionState.InProgress)
        assertEquals(2, (state as WritingSessionState.InProgress).prompts.size)
    }

    @Test fun aLanguageWithNoWordsAtAll_isUnavailable_notACrash() = runTest {
        val api = FakeWritingApi(mapOf("fr" to emptyList()))
        assertTrue(service(api = api).start() is WritingSessionState.Unavailable)
    }

    @Test fun anUnreachableBackend_isUnavailable() = runTest {
        val api = FakeWritingApi(mapOf("fr" to tenFrenchWords)).apply { unavailable = true }
        assertTrue(service(api = api).start() is WritingSessionState.Unavailable)
    }

    @Test fun neverRequestsMoreThanTheBackendsOwnPageCap() = runTest {
        val api = FakeWritingApi(mapOf("fr" to tenFrenchWords))
        service(api = api).start()
        assertTrue("requested limits were ${api.requestedLimits}", api.requestedLimits.all { it <= 100 })
    }
}
