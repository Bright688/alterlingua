package com.alterlingua.app.learning.map

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs on a device or emulator: the Personal Language Map against the REAL Room database, in a private test file. */
class RoomLanguageMapTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "language-map-test.db"
    private lateinit var database: LanguageMapDatabase
    private lateinit var service: LanguageMapService

    private val devis = UnitKey("fr", "devis", UnitType.WORD)

    private fun open(): LanguageMapDatabase = Room.databaseBuilder(context, LanguageMapDatabase::class.java, name).build()

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = open()
        service = LanguageMapService(RoomLanguageMapStore(database))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun anItemIsSavedWithEveryField_andSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        service.recordHelpRequest(devis, displayForm = "Devis")
        service.recordLessonEncounter(devis)
        service.recordRecognition(devis, correct = true)
        service.recordRecognition(devis, correct = false)
        service.setMeaning(devis, "quotation", "en")
        val saved = service.item(devis)!!

        database.close()
        database = open()
        val reopened = LanguageMapService(RoomLanguageMapStore(database)).item(devis)!!

        assertEquals(saved, reopened)
        assertEquals("Devis", reopened.displayForm)
        assertEquals("quotation", reopened.meaning)
        assertEquals(1, reopened.helpRequests)
        assertEquals(1, reopened.lessonEncounters)
        assertEquals(1, reopened.correctRecognitions)
        assertEquals(1, reopened.incorrectRecognitions)
    }

    @Test
    fun aWordAndAPhrase_andTheSameWordInTwoLanguages_areSeparateRows() = runBlocking {
        service.recordHelpRequest(devis)
        service.recordHelpRequest(UnitKey("fr", "devis", UnitType.PHRASE))
        service.recordHelpRequest(UnitKey("es", "devis", UnitType.WORD))
        assertEquals(2, service.items("fr").size)
        assertEquals(1, service.items("es").size)
    }

    @Test
    fun theJourneyToMasteredAndBack_isStored() = runBlocking {
        repeat(4) { service.recordLessonEncounter(devis) }
        repeat(5) { service.recordRecognition(devis, correct = true) }
        assertEquals(MasteryStatus.MASTERED, service.item(devis)!!.masteryState)
        repeat(3) { service.recordRecognition(devis, correct = false) }
        assertEquals(MasteryStatus.FAMILIAR, service.item(devis)!!.masteryState)
    }

    @Test
    fun manyUpdatesAtOnce_areAllCounted() = runBlocking {
        (1..30).map { async { service.recordHelpRequest(devis) } }.awaitAll()
        assertEquals(30, service.item(devis)!!.helpRequests)
    }

    @Test
    fun deletingOneLanguage_leavesTheOthers() = runBlocking {
        service.recordHelpRequest(devis)
        service.recordHelpRequest(UnitKey("es", "presupuesto", UnitType.WORD))
        service.deleteLanguage("fr")
        assertTrue(service.items("fr").isEmpty())
        assertEquals(1, service.items("es").size)
        service.deleteAll()
        assertTrue(service.items("es").isEmpty())
        assertNull(service.item(devis))
    }

    @Test
    fun theMapCanBeObserved() = runBlocking {
        service.recordHelpRequest(devis)
        assertEquals(listOf("devis"), service.observe("fr").first().map { it.normalized })
    }
}
