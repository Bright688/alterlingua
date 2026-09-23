package com.alterlingua.app.learning.lessons

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyLessonCodecTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val lesson = DailyLesson(
        date = "2026-09-20", language = "ja",
        cards = listOf(
            LessonCard(UnitKey("ja", "見積書", UnitType.WORD), "見積書", CardKind.WORD_CARD, "quotation", "en", LessonContext(3, InteractionKind.INCOMING_MESSAGE, 99, 2, listOf(com.alterlingua.app.learning.lessons.LessonReason.NEW, com.alterlingua.app.learning.lessons.LessonReason.ASKED_TRANSLATION)), MasteryStatus.UNKNOWN),
            LessonCard(UnitKey("ja", "お送りします", UnitType.PHRASE), "お送りします", CardKind.PHRASE_CARD, null, null, LessonContext(1, null, 5, 0, emptyList()), MasteryStatus.LEARNING),
        ),
        position = 1, encountered = setOf(0), completed = false,
    )

    @Test
    fun aLessonSurvivesBeingSavedAndRead_includingUnicodeAndMissingMeanings() {
        assertEquals(lesson, DailyLessonCodec.decode(DailyLessonCodec.encode(lesson)))
    }

    @Test
    fun aDamagedOrEmptySavedLesson_isIgnored() {
        assertNull(DailyLessonCodec.decode("not json"))
        assertNull(DailyLessonCodec.decode("{}"))
        assertNull(DailyLessonCodec.decode(DailyLessonCodec.encode(lesson).replace("WORD_CARD", "MYSTERY")))
        assertNull(DailyLessonCodec.decode(DailyLessonCodec.encode(lesson.copy(cards = emptyList()))))
    }

    @Test
    fun theRealDataStoreKeepsTheLesson_andClearsIt() = runTest {
        val store = DataStoreDailyLessonStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { folder.newFile("lesson.preferences_pb") }))
        assertNull(store.load())
        store.save(lesson)
        assertEquals(lesson, store.load())
        store.save(lesson.copy(position = 0, completed = true))
        assertEquals(true, store.load()!!.completed)
        store.clear()
        assertNull(store.load())
    }
}
