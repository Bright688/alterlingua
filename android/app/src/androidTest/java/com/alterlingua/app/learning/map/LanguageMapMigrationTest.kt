package com.alterlingua.app.learning.map

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device or emulator: every version of [LanguageMapDatabase]'s real, Room-exported schema (`app/schemas/`)
 * actually migrates, in order, to today's version, rather than only ever being created fresh by a test. An install
 * that has been on the phone since version 1 goes through exactly this path for real.
 */
class LanguageMapMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LanguageMapDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun aVersion1Database_withARealRow_migratesAllTheWayToToday_keepingTheRow() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """INSERT INTO language_map_items
                   (language, normalized, type, displayForm, meaning, meaningLanguage, exposureCount, helpRequests,
                    lessonEncounters, correctRecognitions, incorrectRecognitions, firstSeen, lastSeen, masteryScore, masteryState)
                   VALUES ('fr', 'devis', 'WORD', 'devis', 'quotation', 'en', 8, 1, 2, 3, 0, 1000, 2000, 45.0, 'LEARNING')""",
            )
            close()
        }

        // Each step is run in order, exactly as Room would apply it on a real phone.
        helper.runMigrationsAndValidate(TEST_DB, 2, true)
        helper.runMigrationsAndValidate(TEST_DB, 3, true)
        helper.runMigrationsAndValidate(TEST_DB, 4, true)
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        val row = migrated.query("SELECT * FROM language_map_items WHERE normalized = 'devis'")
        assertTrue(row.moveToFirst())
        assertEquals("quotation", row.getString(row.getColumnIndexOrThrow("meaning")))
        assertEquals(8, row.getInt(row.getColumnIndexOrThrow("exposureCount")))
        assertEquals(3, row.getInt(row.getColumnIndexOrThrow("correctRecognitions")))
        // Columns added after version 1 arrive with their declared defaults, not null or a crash.
        assertEquals(0.0, row.getDouble(row.getColumnIndexOrThrow("usefulness")), 0.0001)
        assertEquals(0, row.getInt(row.getColumnIndexOrThrow("pronunciationTries")))
        // The lemma column (version 5) is nullable with no declared default: an old row simply has none yet.
        assertNull(row.getString(row.getColumnIndexOrThrow("lemma")))
        row.close()
    }

    private companion object {
        const val TEST_DB = "language-map-migration-test.db"
    }
}
