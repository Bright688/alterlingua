package com.alterlingua.app.learning.progress

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device or emulator: [ProgressDatabase]'s real, Room-exported version-1 schema migrates to today's
 * version with a real row intact, rather than only ever being created fresh by a test.
 */
class ProgressMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProgressDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun aVersion1Database_withARealDay_migratesToToday_keepingTheRow() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """INSERT INTO daily_progress
                   (date, language, wordsMet, wordsAssisted, newWords, helpRequests, lessonCards, practiceTries,
                    practiceGood, hasSnapshot, encountered, learning, familiar, mastered)
                   VALUES ('2026-09-20', 'fr', 12, 4, 3, 1, 3, 2, 1, 1, 12, 5, 4, 3)""",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        val row = migrated.query("SELECT * FROM daily_progress WHERE date = '2026-09-20' AND language = 'fr'")
        assertTrue(row.moveToFirst())
        assertEquals(12, row.getInt(row.getColumnIndexOrThrow("wordsMet")))
        assertEquals(3, row.getInt(row.getColumnIndexOrThrow("mastered")))
        // Pilot-metric columns added in version 2 arrive with their declared defaults, not null or a crash.
        assertEquals(0, row.getInt(row.getColumnIndexOrThrow("translationsOutgoingText")))
        assertEquals(0, row.getInt(row.getColumnIndexOrThrow("actionsAdaptive")))
        row.close()
    }

    private companion object {
        const val TEST_DB = "progress-migration-test.db"
    }
}
