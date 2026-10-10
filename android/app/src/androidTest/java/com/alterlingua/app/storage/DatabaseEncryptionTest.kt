package com.alterlingua.app.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.alterlingua.app.learning.map.LanguageMapDatabase
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.RoomLanguageMapStore
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs on a device or emulator: the Personal Language Map's database is genuinely encrypted at rest, and an
 * existing pre-encryption install's real data survives being switched over to it. */
class DatabaseEncryptionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "encryption-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun aPassphrase_isStableAcrossCalls_andIsThirtyTwoRandomBytes() {
        val first = DatabasePassphrase.get(context)
        val second = DatabasePassphrase.get(context)
        assertEquals(32, first.size)
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun aFreshDatabase_cannotBeOpenedWithoutThePassphrase() = runBlocking {
        val passphrase = DatabasePassphrase.get(context)
        val database = Room.databaseBuilder(context, LanguageMapDatabase::class.java, dbName)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
            .build()
        LanguageMapService(RoomLanguageMapStore(database)).recordHelpRequest(UnitKey("fr", "devis", UnitType.WORD))
        database.close()

        val wrongKey = ByteArray(32) { 0 }
        var openedWithWrongKey = true
        try {
            CipherDatabase.openDatabase(context.getDatabasePath(dbName).absolutePath, wrongKey, null, CipherDatabase.OPEN_READONLY, null).use {
                it.rawQuery("SELECT count(*) FROM language_map_items", arrayOf<String>()).use { c -> c.moveToFirst() }
            }
        } catch (_: Exception) {
            openedWithWrongKey = false
        }
        assertFalse("a wrong key must not open the real database", openedWithWrongKey)
    }

    @Test
    fun anExistingPlainTextDatabase_isEncryptedInPlace_withItsRealDataIntact() {
        // Simulate an install from before encryption existed: a plain Room database with a real row already in it.
        val plainDb = Room.databaseBuilder(context, LanguageMapDatabase::class.java, dbName).build()
        runBlocking {
            LanguageMapService(RoomLanguageMapStore(plainDb)).recordHelpRequest(UnitKey("fr", "devis", UnitType.WORD), displayForm = "Devis")
        }
        plainDb.close()

        val passphrase = DatabasePassphrase.get(context)
        DatabaseEncryptor.ensureEncrypted(context, dbName, passphrase)

        // The file now opens only under the real passphrase, and the pre-existing row is still there.
        val reopened = Room.databaseBuilder(context, LanguageMapDatabase::class.java, dbName)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
            .build()
        val item = runBlocking { LanguageMapService(RoomLanguageMapStore(reopened)).item(UnitKey("fr", "devis", UnitType.WORD)) }
        reopened.close()

        assertEquals("Devis", item?.displayForm)
        assertEquals(1, item?.helpRequests)
    }

    @Test
    fun runningEnsureEncrypted_twice_isHarmless() {
        val plainDb = Room.databaseBuilder(context, LanguageMapDatabase::class.java, dbName).build()
        runBlocking { LanguageMapService(RoomLanguageMapStore(plainDb)).recordHelpRequest(UnitKey("fr", "devis", UnitType.WORD)) }
        plainDb.close()

        val passphrase = DatabasePassphrase.get(context)
        DatabaseEncryptor.ensureEncrypted(context, dbName, passphrase)
        DatabaseEncryptor.ensureEncrypted(context, dbName, passphrase) // already encrypted: should be a no-op, not an error

        val reopened = Room.databaseBuilder(context, LanguageMapDatabase::class.java, dbName)
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
            .build()
        val item = runBlocking { LanguageMapService(RoomLanguageMapStore(reopened)).item(UnitKey("fr", "devis", UnitType.WORD)) }
        reopened.close()
        assertEquals(1, item?.helpRequests)
    }

    @Test
    fun aMissingDatabaseFile_isLeftAlone() {
        // A fresh install: nothing to migrate. This must not create a file or throw.
        DatabaseEncryptor.ensureEncrypted(context, dbName, DatabasePassphrase.get(context))
        assertFalse(context.getDatabasePath(dbName).exists())
    }
}
